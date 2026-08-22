package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.marble.MarbleStat;
import me.pattrick.marbledrop.marble.MarbleStats;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/**
 * Real(ish) marble physics, without a rigid-body simulation.
 * <p>
 * A marble's forward position is still a single monotonically-increasing
 * arc-length value {@code s} along the track's spline (see TrackSpline) --
 * exactly like the old path-progress model - so it is still mathematically
 * impossible for a marble to wedge against geometry or get permanently
 * stuck. What's different is *how fast* {@code s} advances each tick: instead
 * of an almost-constant per-tick step, a real scalar velocity is carried
 * between ticks and driven by slope (gravity), rolling resistance, cornering
 * grip, and the marble's own stats - so marbles actually speed up going
 * downhill, bleed speed climbing hills, have to brake for sharp corners
 * (and can lose control if they don't), and carry momentum through it all.
 * <p>
 * This project already tried the "obvious" version of real physics once --
 * free bodies in a shared dyn4j World, driven by forces, with real 2D
 * collision resolution - and reverted it. It looked right on paper but felt
 * wrong in practice: bodies overshot their target speed and oscillated,
 * pileups compounded collision energy faster than damping could bleed it
 * off (sometimes driving a body clean through a wall in one step), and
 * corners could wedge a body indefinitely, needing a "stall kick" escape
 * hatch bolted on top. All of that came from letting forward motion depend
 * on free 2D/3D movement and collision response. This version never does
 * that: forward motion is still a guaranteed 1D advance along the spline,
 * and everything physics-like (gravity, drag, cornering, bumps) only ever
 * adjusts the *rate* of that guaranteed advance, never blocks it outright.
 * <p>
 * Sideways (lateral) position is layered on top and never feeds back into
 * {@code s}, but it's real 1D physics, not a cosmetic target the marble
 * smoothly tracks: a marble carries its own lateral velocity in a straight
 * line and only changes direction by bouncing off a wall of the sampled
 * real-world corridor (losing some energy each bounce), with cornering
 * adding a centrifugal push toward the outside wall. That's what makes a
 * marble visibly ricochet through a corner instead of smoothly curving
 * through it like a train on rails. Real marble-vs-marble separation is
 * layered on top of that so two marbles can never render inside each other.
 * <p>
 * None of that matters if it doesn't render smoothly, though: a marble is
 * an ItemDisplay (not the old invisible-ArmorStand-wearing-a-helmet trick),
 * moved via teleport() every tick same as before, but with
 * {@code setTeleportDuration()} set so Paper eases each position update
 * over a couple of ticks client-side instead of snapping instantly - that
 * smoothing is what turns this same per-tick physics into something that
 * actually looks like continuous rolling instead of hopping point to point.
 */
public final class MarbleRunner {

    public interface FinishListener {
        void onFinish();
    }

    // --------------------
    // Tunables - stat -> physics parameter mapping
    // --------------------
    // All five stats are assumed to run roughly 0-100. Every physics
    // quantity below is expressed *relative to this runner's own top speed*
    // (blocks/tick) rather than as an absolute constant, so the whole model
    // scales together regardless of exactly how BASE_TOP_SPEED/SPEED_RANGE
    // are tuned - gravity, drag, cornering, etc. all "just work" at
    // whatever pace the top-speed constants are set to.

    private static final double BASE_TOP_SPEED = 0.1;    // blocks/tick at SPEED = 0 (~2 blocks/sec)
    private static final double SPEED_RANGE = 0.14;      // + up to this much at SPEED = 100 (~4.8 blocks/sec)

    private static final double BASE_ACCEL_RATE = 0.025; // fraction of speed-gap closed per tick at ACCEL = 0
    private static final double ACCEL_RANGE = 0.06;      // + up to this much at ACCEL = 100

    private static final double HANDLING_MITIGATION = 1.1; // how much cornering grip (0..1 from HANDLING stat) offsets turn severity

    // --------------------
    // Tunables - dynamics
    // --------------------

    // Gravity's pull along the track's slope, scaled by this runner's own
    // top speed so steep/flat sections feel proportionally similar across
    // slow and fast marbles alike. Strong on purpose - slopes should
    // visibly drive the pace, not just nudge it.
    private static final double GRAVITY_STRENGTH = 3.5;
    private static final double SLOPE_SAMPLE_HALF_WIDTH = 0.4; // blocks either side of `s` sampled for slope

    // Once a marble exceeds its own top speed (typically from a downhill
    // gravity assist), drag reels it back in. Loose on purpose - a real
    // marble picks up real speed on a real hill; this only exists as an
    // eventual ceiling (see MAX_SPEED_MULTIPLIER below), not a leash.
    private static final double OVERSPEED_DRAG = 0.15;

    // Cornering: how far ahead the upcoming turn is sampled, and the floor
    // so a marble is always still visibly moving through even the sharpest
    // corner (never a hard stop - see class javadoc on why). Pushed back
    // up after marbles were observed still speeding up right into corners:
    // the drive force (accelRate pulling toward top speed) is active every
    // tick regardless of what's ahead, and with only ~1.4 blocks of warning
    // and a soft brake, it was winning right up until the corner actually
    // arrived. More lookahead (real warning) plus a firmer brake fixes
    // that; wall-impact drag (below) is still the backup for whatever this
    // doesn't catch, not the primary mechanism.
    private static final double CORNER_LOOKAHEAD = 2.2;
    private static final double CORNER_SEVERITY_SCALE = 0.9;
    private static final double MIN_CORNER_SPEED_FRACTION = 0.35;
    private static final double CORNER_BRAKE_RATE = 0.45;

    // Losing control: badly overspeeding a corner risks a brief speed/line
    // penalty, scaled down by STABILITY. Purely a chance-based "moment,"
    // not a hard punishment, so poor-stability marbles still finish - they
    // just look ragged doing it.
    private static final double SPINOUT_TRIGGER = 1.25; // v / maxCornerSpeed ratio that risks a spinout
    private static final double SPINOUT_BASE_CHANCE = 0.05; // per tick, scaled by (1 - stability)
    private static final double SPINOUT_SPEED_PENALTY = 0.55;
    private static final double SPINOUT_WOBBLE_KICK = 0.5;

    // Boost is player-triggered now (see triggerBoost()/RaceBoostListener),
    // not a random per-tick roll - a real racer decides when to spend a
    // limited, non-recharging charge (see RaceBoostItem), so how well that
    // budget is timed is what matters, not just how much of it a
    // high-BOOST-stat marble started with. It's still a real velocity
    // impulse (not an instant one-tick multiplier), so it has momentum and
    // decays naturally back toward top speed via drive/drag. No separate
    // "was this well-timed" bonus needed here - the existing cornering
    // brake and wall-impact drag already eat a chunk of a boost thrown
    // away right before a corner, and let a boost used cleanly on a
    // straight or exit keep its full value, which is exactly the skill
    // signal this is going for.
    private static final double BOOST_IMPULSE_FRACTION = 1.10; // of top speed, added instantly (2x the original 0.55)

    // How long a trail of particles follows a boosted marble, and how it
    // looks - purely cosmetic, never touches physics.
    private static final int BOOST_TRAIL_DURATION_TICKS = 15;
    private static final int BOOST_TRAIL_PARTICLE_COUNT = 6;

    // AI-controlled runners (see buildStatsRunner()/the aiBoost constructor
    // param) have no live player to right-click a real Boost item, so they
    // roll for one themselves - same charge budget a human gets
    // (boostChargesForStat() in RaceManager), spent at random with a
    // cooldown between uses rather than a human's deliberate timing.
    private static final double AI_BOOST_CHANCE_PER_TICK = 0.004;
    private static final int AI_BOOST_COOLDOWN_TICKS = 100; // ~5s between AI boost rolls

    // Gentle organic speed variance, dampened by STABILITY - keeps
    // otherwise-identical marbles from moving in lockstep.
    private static final double SPEED_NOISE_ACCEL = 0.006;
    private static final double SPEED_NOISE_DECAY = 0.90;
    private static final double SPEED_NOISE_MAX = 0.18;

    // Absolute floor/ceiling on velocity, as a multiple of this runner's own
    // top speed. The floor is what guarantees a finite race time even on a
    // brutal uphill with a bad marble; the ceiling is a safety valve against
    // gravity + boost compounding unboundedly.
    private static final double MIN_CRAWL_FRACTION = 0.15;
    private static final double MAX_SPEED_MULTIPLIER = 2.5;

    // A marble that's had to shove another one out of the way this tick
    // (see the separation loop below) and is the one doing the catching-up
    // bleeds a little speed, like bumping the marble ahead - purely a
    // velocity nudge, never a hard block, so it can't cause a permanent jam.
    private static final double BUMP_DRAG = 0.04;

    // --------------------
    // Tunables - lateral placement (cosmetic; never affects `s`)
    // --------------------

    private static final double CLEARANCE_STEP = 0.20;
    private static final double CLEARANCE_MAX_SEARCH = 1.5;
    private static final double CLEARANCE_MARGIN = 0.18; // roughly a marble's own radius
    private static final double CLEARANCE_SAFETY_FACTOR = 0.85; // stay a bit inside sampled clearance, not right at the edge
    private static final double CLEARANCE_SAMPLE_HEIGHT_LOW = 0.30;
    private static final double CLEARANCE_SAMPLE_HEIGHT_HIGH = 0.90;

    // Raw clearance is a fresh block ray-cast every tick, in a direction
    // that rotates smoothly with the track's tangent - but real block
    // walls are blocky, not smooth, so as that ray direction sweeps across
    // a block corner (exactly what happens going through a turn) the
    // measured distance can jump tick to tick even though the track's true
    // width barely changed. The marble's position always fully honors
    // whatever clearance it's given (never renders in a wall - that's the
    // fix for the old clipping bug), so feeding it noisy measurements
    // directly is what read as "warping" through corners. Smoothing the
    // measurement itself (not the marble's reaction to it) fixes that
    // without reopening the clipping problem: this converges toward the
    // real value within a handful of ticks, fast enough to still track a
    // genuine width change, slow enough to absorb single-tick sampling
    // noise.
    private static final double CLEARANCE_SMOOTHING = 0.35;

    // Lateral motion is real 1D physics between the corridor's two walls --
    // a marble carries lateral velocity in a straight line and only ever
    // changes direction by bouncing off a wall (losing some energy), rather
    // than smoothly tracking a computed target position. This is what makes
    // corners look like a marble ricocheting through a chute instead of a
    // train smoothly following the rails.
    // Pulled back from an earlier, much more aggressive pass (damping 0.96,
    // centrifugal 0.9, wander 0.15, restitution 0.75) that turned out to
    // read as erratic/chaotic rather than lively - these are a calmer
    // middle ground between that and the original smooth-drift version.
    private static final double LATERAL_DAMPING = 0.92; // per-tick decay on lateral velocity - bounces settle out again instead of compounding
    private static final double CENTRIFUGAL_SCALE = 0.5; // cornering push toward the outside wall, relative to turn sharpness and forward speed
    private static final double LATERAL_WANDER_ACCEL = 0.07; // random lateral nudge per tick, relative to top speed - a little organic life, not constant jitter
    private static final double BOUNCE_RESTITUTION = 0.6; // fraction of lateral speed kept after bouncing off a wall

    // Every other force above is either shared (cornering, gravity) or pure
    // per-tick noise (wander) - neither gives a marble a consistent
    // "personality," so a whole field of otherwise-similar marbles ends up
    // tracing nearly the same line. This is a small, constant, per-marble
    // push toward its own persistent laneBias side (see the field), so one
    // marble tends to hug the inside of corners and another tends to run
    // wide, all race long - distinct lines without overpowering the
    // corridor/bounce physics still driving the moment-to-moment motion.
    private static final double LANE_PREFERENCE_ACCEL = 0.08;

    // Anticipatory passing: without this, a marble only ever reacts to
    // another one once they're already almost touching (the hard
    // MIN_SEPARATION shove below), which makes the field bunch up
    // single-file with no real opportunity to get around each other. This
    // steers a marble around another one it's closing in on well before
    // that - based on how far ahead the other one is (in track progress,
    // not raw distance) and how much their lanes overlap - so marbles
    // actively hunt for a gap instead of only getting shoved aside at the
    // last second. When there's a real corner coming up, "which way" isn't
    // arbitrary: it dives for the INSIDE line (the shorter way through the
    // turn - same side the track is curving toward, see turnComponent) if
    // that line is actually open, which is the real overtaking move, not
    // just dodging sideways. If the marble ahead already has the inside,
    // there's no gap to force, so it falls back to plain avoidance instead.
    private static final double OVERTAKE_LOOKAHEAD = 2.5; // blocks of progress within which a marble ahead triggers evasive steering
    private static final double OVERTAKE_LANE_RANGE = 0.55; // blocks - how close laterally counts as "in my way"
    private static final double OVERTAKE_STEER_ACCEL = 0.10; // relative to top speed - how hard to steer clear to open a passing lane
    private static final double INSIDE_LINE_TURN_THRESHOLD = 0.08; // turnSeverity above which a corner counts as worth diving to the inside for

    // Hitting a wall costs real forward speed, scaled by how hard the hit
    // was - this is what makes cornering matter beyond the proactive
    // brake above. Moderate on purpose: a real impact should cost
    // something, but a marble bouncing through several corners in a row
    // shouldn't visibly stagger every time.
    private static final double WALL_IMPACT_DRAG = 0.2;

    // Hard cap on lateral velocity itself, independent of forward speed
    // (corridor widths are fixed, real-world blocks - they don't get wider
    // just because a marble is going faster). Centrifugal push scales with
    // forward velocity, so without this a fast marble's lateral velocity
    // can blow past what a narrow corridor can absorb in one tick, which is
    // what reads as the marble teleporting/zipping sideways. The corridor
    // clamp below is a separate, unconditional guarantee against ever
    // rendering inside a wall - the two together are what keep lateral
    // motion both smooth and always physically inside the track.
    private static final double MAX_LATERAL_VEL = 0.13;

    // Below this incoming speed, a "hit" isn't a real impact - it's a
    // steady, weak force (typically centrifugal load through a sustained
    // corner) just pressing the marble against the wall tick after tick.
    // Reflecting that with restitution every single tick is what caused
    // "porpoising": bounce off, get pushed straight back into the wall by
    // the same still-active force, bounce again, forever. Below this
    // threshold the marble settles into contact and slides along the wall
    // instead - real marbles don't bounce off a wall they're being
    // continuously pressed into, they just ride it.
    private static final double BOUNCE_MIN_IMPACT = MAX_LATERAL_VEL * 0.35;

    private static final double MIN_SEPARATION = 0.36; // roughly a marble's diameter plus a small buffer

    // Marbles used to be rendered as an invisible marker ArmorStand wearing
    // the marble as a helmet, moved via teleport() every tick. Bukkit never
    // client-interpolates a teleport - every physics-correct position
    // update rendered as a hard snap, which is what read as the marble
    // "going point to point" instead of rolling. An ItemDisplay renders the
    // exact same textured-head item, but Paper can smoothly interpolate its
    // position between updates (see spawnMarble()/tick()), so the same
    // per-tick physics now actually looks continuous.
    private static final int TELEPORT_INTERPOLATION_TICKS = 2; // how many ticks the client eases a position update over
    private static final float MARBLE_DISPLAY_SCALE = 0.55f; // shrinks the default item-display size down to roughly marble-sized

    // Positive values push the marble DOWN from the track-surface height
    // (baseY - DISPLAY_Y_OFFSET), negative values push it UP. At 0.0 the
    // marble rendered a full block too low; -1.0 (a full block up) turned
    // out to overshoot by about half a block, confirmed live - the item's
    // actual render origin sits somewhere between the two, not at either
    // extreme.
    private static final double DISPLAY_Y_OFFSET = -0.5;

    private final ItemDisplay stand;

    private final TrackSpline spline;
    private final double totalLength;

    // Stat-derived physics parameters, fixed for this runner's lifetime.
    private final double topSpeed;       // blocks/tick on flat ground
    private final double accelRate;      // fraction of speed-gap closed per tick
    private final double handlingGrip;   // 0 .. ~1.1, offsets corner turn severity
    private final double stability;      // 0..1

    private double s = 0.0;          // arc-length traveled so far this lap, 0..totalLength
    private double velocity = 0.0;   // blocks/tick, always >= a small crawl floor

    private final int totalLaps;
    private int lapsCompleted = 0;

    private final FinishListener finishListener;

    // This runner's persistent racing-line personality, in [-1, 1]
    // (negative = favors hugging the left/inside, positive = favors
    // right/outside). Seeds the initial lateral kick AND applies as a
    // small constant force every tick (see LANE_PREFERENCE_ACCEL) so
    // marbles trace visibly different lines all race long instead of
    // converging onto the same one.
    private final double laneBias;

    private final Random rng = new Random();

    private double speedNoise = 0.0;

    // Real lateral state: position across the corridor (blocks from
    // centerline, signed along the track's "right" vector) and its own
    // persistent velocity - see the bounce physics in tick().
    private double lateralPos = 0.0;
    private double lateralVel;

    // Smoothed clearance - see CLEARANCE_SMOOTHING. Negative means
    // "not sampled yet," so the very first tick snaps straight to the raw
    // measurement instead of smoothing from a fake zero.
    private double smoothedRightClear = -1.0;
    private double smoothedLeftClear = -1.0;

    private double pendingVelocityScale = 1.0; // applied at the end of tick() - see BUMP_DRAG

    // Cosmetic boost trail - see BOOST_TRAIL_DURATION_TICKS.
    private int boostTrailTicksRemaining = 0;

    // AI autonomous boosting - see AI_BOOST_CHANCE_PER_TICK.
    private final boolean aiBoost;
    private final double boostStatFraction;
    private int aiBoostChargesRemaining;
    private int aiBoostCooldown = 0;

    // True by default so every existing call path (test races, the
    // tutorial, /md track run) behaves exactly as before: spawn and go.
    // RaceManager.start() is the one caller that holds real-race runners
    // at their starting grid spot (see hold()/release()) until a countdown
    // finishes, so players actually see the grid before the race goes.
    private boolean released = true;

    // Backwards-compatible convenience constructors (mid-tier stats).
    public MarbleRunner(MarbleTrack track, Location start) {
        this(track, start, null, new MarbleStats(50, 50, 50, 50, 50), 1, null);
    }

    public MarbleRunner(MarbleTrack track, Location start, ItemStack helmet, FinishListener finishListener) {
        this(track, start, helmet, new MarbleStats(50, 50, 50, 50, 50), 1, finishListener);
    }

    public MarbleRunner(MarbleTrack track, Location start, ItemStack helmet, MarbleStats stats,
                         int laps, FinishListener finishListener) {
        this(track, start, helmet, stats, laps, finishListener, false);
    }

    /**
     * @param aiBoost true for a runner with no live player to right-click a
     *                real Boost item - it rolls for its own boosts instead
     *                (see AI_BOOST_CHANCE_PER_TICK). Used by
     *                RaceManager#buildStatsRunner(), which is the shared
     *                path for every runner that isn't under a human's
     *                real-time control (test-race AI racers, and
     *                ScheduledRaceManager's automated races, including
     *                their player-owned entries - those run unattended
     *                too, so they get the same treatment).
     */
    public MarbleRunner(MarbleTrack track, Location start, ItemStack helmet, MarbleStats stats,
                         int laps, FinishListener finishListener, boolean aiBoost) {
        this.spline = (track != null) ? track.getRaceSpline() : TrackSpline.build(java.util.Collections.emptyList());
        this.totalLength = spline.totalLength();
        this.finishListener = finishListener;
        this.totalLaps = Math.max(1, laps);
        this.stand = spawnMarble(lowerForDisplay(start), helmet);

        MarbleStats st = (stats != null) ? stats : new MarbleStats(50, 50, 50, 50, 50);
        double speedStat = clamp01(st.get(MarbleStat.SPEED) / 100.0);
        double accelStat = clamp01(st.get(MarbleStat.ACCEL) / 100.0);
        double handlingStat = clamp01(st.get(MarbleStat.HANDLING) / 100.0);
        double stabilityStat = clamp01(st.get(MarbleStat.STABILITY) / 100.0);

        this.topSpeed = BASE_TOP_SPEED + speedStat * SPEED_RANGE;
        this.accelRate = BASE_ACCEL_RATE + accelStat * ACCEL_RANGE;
        this.handlingGrip = handlingStat * HANDLING_MITIGATION;
        this.stability = stabilityStat;

        this.velocity = topSpeed * 0.5; // rolling start, not a standing one

        this.laneBias = (rng.nextDouble() * 2.0) - 1.0;
        this.lateralVel = laneBias * topSpeed * 0.4; // gentle initial seed so marbles start bouncing out of phase with each other

        this.aiBoost = aiBoost;
        double boostStat = clamp01(st.get(MarbleStat.BOOST) / 100.0);
        this.boostStatFraction = boostStat;
        // Same 2-6 charge budget boostChargesForStat() gives a human racer in RaceManager.
        this.aiBoostChargesRemaining = aiBoost ? (int) Math.round(2 + boostStat * 4) : 0;
    }

    private static Location lowerForDisplay(Location loc) {
        return loc.clone().subtract(0, DISPLAY_Y_OFFSET, 0);
    }

    private ItemDisplay spawnMarble(Location loc, ItemStack helmet) {
        World world = (loc == null) ? null : loc.getWorld();
        if (world == null) throw new IllegalArgumentException("MarbleRunner start location has no world.");

        ItemDisplay display = (ItemDisplay) world.spawnEntity(loc, EntityType.ITEM_DISPLAY);

        ItemStack shown = (helmet != null) ? helmet.clone() : new ItemStack(Material.PLAYER_HEAD);
        shown.setAmount(1);
        display.setItemStack(shown);

        // FIXED, not one of the billboard modes - a marble is a physical
        // object sitting on the track, not a sprite that should spin to
        // face whoever's looking at it.
        display.setBillboard(Display.Billboard.FIXED);

        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(MARBLE_DISPLAY_SCALE, MARBLE_DISPLAY_SCALE, MARBLE_DISPLAY_SCALE),
                new AxisAngle4f(0f, 0f, 0f, 1f)
        ));

        // The actual fix for "point to point": interpolate position changes
        // over a couple of ticks instead of snapping instantly on every
        // teleport() call in tick() below.
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(TELEPORT_INTERPOLATION_TICKS);
        display.setTeleportDuration(TELEPORT_INTERPOLATION_TICKS);

        return display;
    }

    public boolean tick() {
        return tick(null);
    }

    public boolean tick(List<MarbleRunner> allRunners) {

        if (spline == null || totalLength <= 0.0) {
            stand.remove();
            return false;
        }

        // Held at the starting grid, waiting on a countdown - sit tight,
        // stay alive, don't move or advance laps.
        if (!released) return true;

        // AI autonomous boosting - see AI_BOOST_CHANCE_PER_TICK. A human
        // racer decides timing deliberately (RaceBoostListener); an AI
        // runner just rolls the dice each tick once its cooldown clears,
        // weighted by its own BOOST stat, until its budget runs out.
        if (aiBoost) {
            if (aiBoostCooldown > 0) {
                aiBoostCooldown--;
            } else if (aiBoostChargesRemaining > 0 && rng.nextDouble() < AI_BOOST_CHANCE_PER_TICK * (0.3 + 0.7 * boostStatFraction)) {
                triggerBoost();
                aiBoostChargesRemaining--;
                aiBoostCooldown = AI_BOOST_COOLDOWN_TICKS;
            }
        }

        if (s >= totalLength) {
            lapsCompleted++;
            if (lapsCompleted >= totalLaps) {
                if (finishListener != null) finishListener.onFinish();
                stand.remove();
                return false;
            }
            // Carry any overshoot into the new lap rather than dropping it,
            // so lap boundaries don't cost the runner speed.
            s -= totalLength;
        }

        // --------------------
        // Sample the track at the CURRENT position to figure out this
        // tick's forces - slope for gravity, upcoming curvature for
        // cornering. Advancing `s` happens after, using these.
        // --------------------
        Vector forward = spline.tangentAt(s);
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());

        double slope = sampleSlope(s);

        Vector aheadForward = spline.tangentAt(Math.min(s + CORNER_LOOKAHEAD, totalLength));
        // Component of the upcoming direction along "right" - ~sin(turn angle); positive = curving right.
        double turnComponent = aheadForward.getX() * right.getX() + aheadForward.getZ() * right.getZ();
        double turnSeverity = Math.abs(turnComponent) / CORNER_LOOKAHEAD;

        double netSeverity = Math.max(0.0, turnSeverity - handlingGrip);
        double cornerFraction = clamp(1.0 - netSeverity * CORNER_SEVERITY_SCALE, MIN_CORNER_SPEED_FRACTION, 1.0);
        double maxCornerSpeed = topSpeed * cornerFraction;

        // --------------------
        // Integrate velocity: gravity (slope), drive toward top speed,
        // overspeed drag, and cornering brake all layered together.
        // --------------------
        double gravityAccel = -GRAVITY_STRENGTH * topSpeed * slope;
        double driveAccel = accelRate * (topSpeed - velocity);
        double overspeedDrag = (velocity > topSpeed) ? -OVERSPEED_DRAG * (velocity - topSpeed) : 0.0;
        double cornerBrake = (velocity > maxCornerSpeed) ? -CORNER_BRAKE_RATE * (velocity - maxCornerSpeed) : 0.0;

        velocity += gravityAccel + driveAccel + overspeedDrag + cornerBrake;

        // Losing control on a hot corner - scaled down hard by stability.
        if (maxCornerSpeed > 1e-9 && velocity > maxCornerSpeed * SPINOUT_TRIGGER) {
            double spinoutChance = SPINOUT_BASE_CHANCE * (1.0 - stability);
            if (rng.nextDouble() < spinoutChance) {
                velocity *= SPINOUT_SPEED_PENALTY;
                lateralVel += (rng.nextBoolean() ? 1.0 : -1.0) * SPINOUT_WOBBLE_KICK * topSpeed;
            }
        }

        // Organic per-tick noise, dampened by stability.
        speedNoise += (rng.nextDouble() - 0.5) * SPEED_NOISE_ACCEL * (1.0 - stability * 0.6);
        speedNoise *= SPEED_NOISE_DECAY;
        speedNoise = clamp(speedNoise, -SPEED_NOISE_MAX, SPEED_NOISE_MAX);
        velocity *= (1.0 + speedNoise);

        // Apply any drag queued up by last tick's marble-vs-marble bump.
        velocity *= pendingVelocityScale;
        pendingVelocityScale = 1.0;

        double minCrawl = topSpeed * MIN_CRAWL_FRACTION;
        double maxSpeed = topSpeed * MAX_SPEED_MULTIPLIER;
        velocity = clamp(velocity, minCrawl, maxSpeed);

        s += velocity;
        if (s > totalLength) s = totalLength;

        // --------------------
        // Resolve exact on-track centerline position + tangent direction
        // at the new `s`.
        // --------------------
        Location basePos = spline.positionAt(s);
        World world = spline.getWorld();
        forward = spline.tangentAt(s);
        right = new Vector(-forward.getZ(), 0, forward.getX());

        double baseX = basePos.getX();
        double baseY = basePos.getY();
        double baseZ = basePos.getZ();

        // --------------------
        // How much room is there to spread out here? Purely informational
        // - never used to block or slow forward progress above. Smoothed
        // (see CLEARANCE_SMOOTHING) so a noisy single-tick measurement at a
        // corner doesn't read as the marble warping sideways.
        // --------------------
        double rawRightClear = computeClearance(world, baseX, baseY, baseZ, right) * CLEARANCE_SAFETY_FACTOR;
        double rawLeftClear = computeClearance(world, baseX, baseY, baseZ, right.clone().multiply(-1)) * CLEARANCE_SAFETY_FACTOR;

        smoothedRightClear = (smoothedRightClear < 0)
                ? rawRightClear
                : smoothedRightClear + (rawRightClear - smoothedRightClear) * CLEARANCE_SMOOTHING;
        smoothedLeftClear = (smoothedLeftClear < 0)
                ? rawLeftClear
                : smoothedLeftClear + (rawLeftClear - smoothedLeftClear) * CLEARANCE_SMOOTHING;

        double rightClear = smoothedRightClear;
        double leftClear = smoothedLeftClear;

        // --------------------
        // Lateral motion: a real 1D bounce between the corridor's two
        // walls. Cornering pushes lateral velocity outward (centrifugal
        // force), a little randomness keeps it organic, and hitting either
        // wall reflects the velocity (with some energy lost) instead of
        // the position just snapping back - so the marble visibly
        // ricochets side to side through a corner rather than smoothly
        // hugging a racing line through it. A hard-enough impact also
        // bleeds real forward speed (next tick - see pendingVelocityScale
        // below), so crashing into a wall actually costs something instead
        // of being purely cosmetic.
        // --------------------
        double centrifugal = -Math.signum(turnComponent) * CENTRIFUGAL_SCALE * Math.abs(turnComponent) * velocity;
        lateralVel += centrifugal;
        lateralVel += laneBias * LANE_PREFERENCE_ACCEL * topSpeed;
        lateralVel += (rng.nextDouble() - 0.5) * LATERAL_WANDER_ACCEL * topSpeed * (0.7 - 0.4 * stability);

        // Anticipatory passing - see OVERTAKE_LOOKAHEAD. Well before the
        // hard MIN_SEPARATION shove below would otherwise be the only thing
        // moving them apart, steer around any marble close ahead and
        // lane-blocking. On a real corner, that means diving for the
        // inside line (the actual overtaking move) if it's open; if the
        // marble ahead already holds the inside, there's no gap to force,
        // so fall back to just steering clear of wherever they are.
        if (allRunners != null) {
            for (MarbleRunner other : allRunners) {
                if (other == this) continue;
                double progressGap = other.s - this.s;
                if (progressGap <= 0 || progressGap >= OVERTAKE_LOOKAHEAD) continue;

                double laneGap = other.lateralPos - this.lateralPos;
                if (Math.abs(laneGap) >= OVERTAKE_LANE_RANGE) continue;

                double urgency = 1.0 - (progressGap / OVERTAKE_LOOKAHEAD);

                double steerSign;
                if (turnSeverity > INSIDE_LINE_TURN_THRESHOLD) {
                    double insideSign = Math.signum(turnComponent);
                    boolean otherHasInside = Math.abs(other.lateralPos) > 1e-6 && Math.signum(other.lateralPos) == insideSign;
                    steerSign = otherHasInside
                            ? ((Math.abs(laneGap) > 1e-6) ? -Math.signum(laneGap) : -insideSign)
                            : insideSign;
                } else {
                    steerSign = (Math.abs(laneGap) > 1e-6) ? -Math.signum(laneGap) : (laneBias >= 0 ? 1.0 : -1.0);
                }

                lateralVel += steerSign * OVERTAKE_STEER_ACCEL * urgency * topSpeed;
            }
        }

        lateralVel *= LATERAL_DAMPING;
        lateralVel = clamp(lateralVel, -MAX_LATERAL_VEL, MAX_LATERAL_VEL);

        double candidatePos = lateralPos + lateralVel;

        if (candidatePos > rightClear || candidatePos < -leftClear) {
            double impactSpeed = Math.abs(lateralVel);

            if (impactSpeed > BOUNCE_MIN_IMPACT) {
                // A real impact - bounce with restitution, and it costs forward speed too.
                double impactSeverity = clamp(impactSpeed / MAX_LATERAL_VEL, 0.0, 1.0);
                pendingVelocityScale = Math.min(pendingVelocityScale, 1.0 - WALL_IMPACT_DRAG * impactSeverity);

                if (candidatePos > rightClear) {
                    candidatePos = rightClear - (candidatePos - rightClear);
                } else {
                    candidatePos = -leftClear - (candidatePos + leftClear);
                }
                lateralVel = -lateralVel * BOUNCE_RESTITUTION;
            } else {
                // Too weak to be a real impact - a steady force (typically
                // cornering load) is just pressing the marble against the
                // wall. Settle into contact and slide along it instead of
                // reflecting every tick, which is what caused porpoising.
                candidatePos = clamp(candidatePos, -leftClear, rightClear);
                lateralVel = 0.0;
            }
        }

        // Always fully honor the CURRENT corridor - never render inside a
        // wall, even if that means a bigger-than-usual correction because
        // the corridor genuinely narrowed a lot since last tick (e.g.
        // rounding into a corner). MAX_LATERAL_VEL above is what keeps
        // ordinary bounce motion from ever teleporting/zipping; this clamp
        // is a hard geometric guarantee, not something to soften at the
        // cost of clipping into a wall.
        double lateral = clamp(candidatePos, -leftClear, rightClear);
        lateralPos = lateral;

        // --------------------
        // Real separation from other marbles - this is what prevents
        // phasing through each other. Re-clamped to sampled clearance
        // after every push, so separation can never shove a marble
        // through a wall either - on a track too narrow for two marbles
        // side by side, they'll simply be as separated as physically
        // possible, not perfectly the full MIN_SEPARATION apart.
        // --------------------
        double candX = baseX + right.getX() * lateral;
        double candZ = baseZ + right.getZ() * lateral;

        if (allRunners != null) {
            for (MarbleRunner other : allRunners) {
                if (other == this) continue;
                Location otherLoc = other.getLocation();
                if (otherLoc == null) continue;

                double dx = candX - otherLoc.getX();
                double dz = candZ - otherLoc.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);

                if (dist < MIN_SEPARATION) {
                    double pushSign = (System.identityHashCode(this) < System.identityHashCode(other)) ? -1.0 : 1.0;
                    double pushNeeded = (dist > 1e-6) ? (MIN_SEPARATION - dist) : MIN_SEPARATION;

                    lateral += pushSign * pushNeeded;
                    lateral = clamp(lateral, -leftClear, rightClear);

                    candX = baseX + right.getX() * lateral;
                    candZ = baseZ + right.getZ() * lateral;

                    // Bumping into a marble ahead bleeds a little speed, like a real nudge.
                    if (other.s > this.s) {
                        pendingVelocityScale = Math.min(pendingVelocityScale, 1.0 - BUMP_DRAG);
                    }
                }
            }
        }

        float yaw = yawFromVector(forward);
        Location newLoc = new Location(world, candX, baseY - DISPLAY_Y_OFFSET, candZ, yaw, 0f);
        stand.teleport(newLoc);

        if (boostTrailTicksRemaining > 0 && world != null) {
            world.spawnParticle(Particle.CRIT, candX, baseY - DISPLAY_Y_OFFSET + 0.15, candZ,
                    BOOST_TRAIL_PARTICLE_COUNT, 0.08, 0.05, 0.08, 0.02);
            boostTrailTicksRemaining--;
        }

        return true;
    }

    /** Signed elevation grade (rise/run) at arc-length `at`, via a small finite-difference sample. */
    private double sampleSlope(double at) {
        double sBack = clamp(at - SLOPE_SAMPLE_HALF_WIDTH, 0, totalLength);
        double sFwd = clamp(at + SLOPE_SAMPLE_HALF_WIDTH, 0, totalLength);
        double ds = sFwd - sBack;
        if (ds < 1e-6) return 0.0;

        double yBack = spline.positionAt(sBack).getY();
        double yFwd = spline.positionAt(sFwd).getY();
        return (yFwd - yBack) / ds;
    }

    /**
     * How far (in blocks) it's clear to move in the given direction from
     * (baseX, baseY, baseZ) before hitting a solid block, capped at
     * CLEARANCE_MAX_SEARCH and reduced by CLEARANCE_MARGIN. Samples two
     * heights above the base point to catch typical wall heights without
     * being tripped up by ankle-high lips in the track.
     */
    private double computeClearance(World world, double baseX, double baseY, double baseZ, Vector dir) {
        for (double d = CLEARANCE_STEP; d <= CLEARANCE_MAX_SEARCH; d += CLEARANCE_STEP) {
            double x = baseX + dir.getX() * d;
            double z = baseZ + dir.getZ() * d;

            if (isSolidAt(world, x, baseY + CLEARANCE_SAMPLE_HEIGHT_LOW, z)
                    || isSolidAt(world, x, baseY + CLEARANCE_SAMPLE_HEIGHT_HIGH, z)) {
                return Math.max(0.0, d - CLEARANCE_MARGIN);
            }
        }
        return CLEARANCE_MAX_SEARCH - CLEARANCE_MARGIN;
    }

    private boolean isSolidAt(World world, double x, double y, double z) {
        Block b = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return b.getType().isSolid();
    }

    /** How far along the track (arc-length, in blocks) this runner has traveled so far. */
    public double getProgress() {
        return s;
    }

    public double getTotalLength() {
        return totalLength;
    }

    /** Current scalar speed, in blocks/tick - exposed for UI/leaderboard flavor (e.g. a speedometer). */
    public double getVelocity() {
        return velocity;
    }

    /**
     * Fires a real velocity impulse right now - called by RaceBoostListener
     * when the owning player right-clicks their Boost item. Deliberately a
     * plain, unconditional action: charge tracking lives on the item itself
     * (its stack amount, see RaceBoostItem), not here, so this never needs
     * to know how many uses are left - the listener already checked before
     * calling it.
     */
    public void triggerBoost() {
        velocity += topSpeed * BOOST_IMPULSE_FRACTION;
        boostTrailTicksRemaining = BOOST_TRAIL_DURATION_TICKS;
    }

    /** Toggles the glowing-outline render flag on this marble's entity - see RaceGlowListener. Purely cosmetic, never touches physics. */
    public void setGlowing(boolean glowing) {
        if (stand != null && stand.isValid()) stand.setGlowing(glowing);
    }

    public boolean isGlowing() {
        return stand != null && stand.isValid() && stand.isGlowing();
    }

    /** The underlying display entity - exposed narrowly for ProtocolLib-based per-player glow filtering (see RaceGlowPrivacy). */
    public Entity getEntity() {
        return stand;
    }

    private float yawFromVector(Vector v) {
        return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
    }

    public Location getLocation() {
        return stand != null && stand.isValid() ? stand.getLocation() : null;
    }

    /** Force-despawns this runner's marble without firing its finish listener - used by /race purge. */
    void despawn() {
        if (stand != null && stand.isValid()) stand.remove();
    }

    /** Holds this runner motionless at its spawn spot until release() - see RaceManager's starting-grid countdown. */
    void hold() {
        released = false;
    }

    /** Lets a held runner start actually racing - see hold(). */
    void release() {
        released = true;
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private double clamp(double v, double a, double b) {
        if (v < a) return a;
        if (v > b) return b;
        return v;
    }
}
