package me.pattrick.marbledrop.races;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;

import java.util.Random;

/**
 * A single marble racing on a track's TrackPhysics world.
 * <p>
 * Forward drive, marble-vs-marble collision, and marble-vs-wall collision
 * are all handled by the shared dyn4j World (see TrackPhysics) -- this
 * class only ever pushes a drive force into its own body before the world
 * steps, and reads its own body's position back out after. Progress along
 * the track is derived by projecting that position onto the track's
 * TrackSpline, so leader/finish detection stays well-defined even though
 * the body itself is free to move anywhere within the walls (including
 * sideways, to overtake).
 */
public final class MarbleRunner {

    public interface FinishListener {
        void onFinish();
    }

    // --------------------
    // Tunables
    // --------------------

    private static final double TICKS_PER_SECOND = 20.0;

    // RaceManager.computeSpeedPerTick() was tuned for the old system, where
    // "speed" advanced progress directly with zero drag of any kind. Real
    // physics now has wall/marble friction and linear damping eating into
    // that, so the same stat-driven speedPerTick nets out visibly slower
    // than it used to. This multiplier compensates; it's a first-pass guess
    // (untested against a live server) -- raise/lower it directly to taste
    // rather than touching the underlying stat balance in RaceManager.
    private static final double SPEED_MULTIPLIER = 9.0;

    // Hard ceiling on total speed (forward + lateral combined), as a multiple
    // of this runner's own target cruise speed. Collision impulses from a
    // pileup are otherwise uncapped -- energy can compound tick over tick
    // faster than the drive/damping bleeds it off, especially at higher
    // SPEED_MULTIPLIER values, which is what actually drives bodies deep
    // enough into a wall in one step for the solver to visibly lag behind.
    private static final double MAX_SPEED_MULTIPLE = 3.0;

    // Drive blends the body's forward speed toward the target by this fraction
    // of the remaining gap each tick -- e.g. 0.5 closes ~95% of the gap in
    // about 5 ticks (~0.25s). This is deliberately not a force/gain-based P
    // controller: these bodies are light enough (radius 0.18, default density)
    // that a force of gain * error way overshot the target speed every single
    // tick, flipped sign, and overshot back -- a stable-looking number on
    // paper but a visibly jittery, stuttering marble that also nets out far
    // slower than the target speed because it's fighting itself constantly.
    // Blending velocity directly is stable regardless of mass.
    private static final double DRIVE_BLEND = 0.5;

    // Small organic wobble on the drive direction, purely cosmetic.
    private static final double WANDER_ACCEL = 0.05;
    private static final double WANDER_DECAY = 0.90;
    private static final double WANDER_MAX_RAD = Math.toRadians(12.0);

    // Per-tick speed variance (chaos stat) and occasional burst (aggression stat) -- flavor only.
    private static final double SPEED_WOBBLE_ACCEL = 0.008;
    private static final double SPEED_WOBBLE_DECAY = 0.90;
    private static final double SPEED_WOBBLE_MAX = 0.35;
    private static final double AGGRESSION_BURST_CHANCE = 0.01; // per tick, scaled by aggression
    private static final double AGGRESSION_BURST_MULT = 1.5;

    private static final double FINISH_EPSILON = 0.05; // blocks of remaining arc-length that still counts as "finished"

    // A small armor stand's helmet slot sits well above its feet (the actual
    // teleport position) -- without this the marble visibly floats above the
    // track surface instead of resting on it.
    private static final double DISPLAY_Y_OFFSET = 0.75;

    // Steer using the tangent a bit further up the track than the marble's
    // current progress, not the tangent exactly at its own position. Without
    // this, the drive aims straight at the current point right up until the
    // marble is basically at a sharp corner, then has to snap the drive
    // direction almost instantly -- by then it's already driving into the
    // corner wall instead of starting to turn into it. This doesn't change
    // where the wall collision itself is (that's real blocks, unavoidably
    // square on a hard turn) -- it just gives the marble a head start on
    // aiming around the bend instead of finding out about it at the last
    // possible moment.
    private static final double DRIVE_LOOKAHEAD = 1.25;

    // Safeguard against a marble wedging into a harsh corner and stalling
    // there indefinitely: if progress hasn't advanced meaningfully for this
    // many ticks, force an escape kick (extra forward push + a lateral shove)
    // instead of waiting on the normal drive to eventually work it free.
    private static final double STALL_PROGRESS_EPSILON = 0.05; // blocks
    private static final int STALL_TICKS_THRESHOLD = 30; // ~1.5s at 20 ticks/sec
    private static final double STALL_KICK_FORWARD_MULT = 2.5;
    private static final double STALL_KICK_LATERAL_MULT = 1.5;

    private final TrackPhysics physics;
    private final Body body;
    private final ArmorStand stand;

    private final double totalLength;
    private final double baseTargetSpeed; // blocks/second, converted from the legacy blocks/tick speedPerTick tuning

    private final FinishListener finishListener;
    private final double chaos;       // 0..1
    private final double aggression;  // 0..1

    private final Random rng = new Random();

    private double lastS;
    private double lastGoodX;
    private double lastGoodZ;
    private boolean finished = false;

    private double speedWobble = 0.0;
    private double wobbleAngle;
    private double wobbleAngleVel = 0.0;

    private double stallCheckpointS = 0.0;
    private int stallTicks = 0;
    private int stallKickSign = 1;

    public MarbleRunner(TrackPhysics physics, Location start, ItemStack helmet, double speedPerTick,
                         double chaos, double aggression, FinishListener finishListener) {
        this(physics, start, helmet, speedPerTick, chaos, aggression, finishListener, null);
    }

    /**
     * @param viewer When non-null, this marble is hidden from every other
     *               player and shown only to {@code viewer} -- used by the
     *               tutorial's practice race so a racer only ever sees their
     *               own marble and their own AI opponents, never another
     *               concurrent tutorial-taker's. Null means visible to
     *               everyone, as in a real race.
     */
    public MarbleRunner(TrackPhysics physics, Location start, ItemStack helmet, double speedPerTick,
                         double chaos, double aggression, FinishListener finishListener, Player viewer) {
        this.physics = physics;
        this.finishListener = finishListener;
        this.chaos = clamp01(chaos);
        this.aggression = clamp01(aggression);
        this.baseTargetSpeed = speedPerTick * TICKS_PER_SECOND * SPEED_MULTIPLIER;
        this.totalLength = physics.getSpline().totalLength();

        this.stand = spawnMarble(lowerForDisplay(start), helmet, viewer);
        this.body = physics.registerMarble(start.getX(), start.getZ());
        // Every caller spawns runners at/near the start of the track, so progress
        // starts at exactly 0 rather than being *found* via a global nearest-point
        // search. That search has no way to tell "just started" from "about to
        // finish" on a track that loops back near its own start (a shared
        // start/finish line) -- it would happily snap a fresh spawn onto the far
        // (finish) end of the lap and fire an instant false finish. Once tracking
        // begins here, every later step's windowed re-projection (see
        // TrackSpline.project) stays anchored near the previous tick's progress,
        // so it never has that ambiguity.
        this.lastS = 0.0;
        this.lastGoodX = start.getX();
        this.lastGoodZ = start.getZ();
        this.wobbleAngle = ((rng.nextDouble() * 2.0) - 1.0) * (0.55 + 0.45 * this.chaos) * WANDER_MAX_RAD;
    }

    private static Location lowerForDisplay(Location loc) {
        return loc.clone().subtract(0, DISPLAY_Y_OFFSET, 0);
    }

    private ArmorStand spawnMarble(Location loc, ItemStack helmet, Player viewer) {
        World world = (loc == null) ? null : loc.getWorld();
        if (world == null) throw new IllegalArgumentException("MarbleRunner start location has no world.");

        ArmorStand as = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setInvisible(true);
        as.setSmall(true);
        as.setGravity(false);
        as.setMarker(true);
        as.setCustomNameVisible(false);

        if (helmet != null) {
            EntityEquipment eq = as.getEquipment();
            if (eq != null) {
                ItemStack h = helmet.clone();
                h.setAmount(1);
                eq.setHelmet(h);
            }
        }

        if (viewer != null) {
            as.setVisibleByDefault(false);
            viewer.showEntity(JavaPlugin.getProvidingPlugin(MarbleRunner.class), as);
        }

        return as;
    }

    /** Pushes this tick's drive force into the body. Called once per engine tick, before the shared World steps. */
    void applyDrive() {
        if (finished) return;

        if (checkAndHandleStall()) return;

        Vector tangent = physics.getSpline().tangentAt(Math.min(lastS + DRIVE_LOOKAHEAD, totalLength));

        speedWobble += (rng.nextDouble() - 0.5) * SPEED_WOBBLE_ACCEL * chaos;
        speedWobble *= SPEED_WOBBLE_DECAY;
        speedWobble = clamp(speedWobble, -SPEED_WOBBLE_MAX, SPEED_WOBBLE_MAX);

        double targetSpeed = baseTargetSpeed * (1.0 + speedWobble);
        if (rng.nextDouble() < AGGRESSION_BURST_CHANCE * aggression) {
            targetSpeed *= AGGRESSION_BURST_MULT;
        }

        wobbleAngleVel += (rng.nextDouble() - 0.5) * WANDER_ACCEL * chaos;
        wobbleAngleVel *= WANDER_DECAY;
        wobbleAngle += wobbleAngleVel;
        wobbleAngle = clamp(wobbleAngle, -WANDER_MAX_RAD, WANDER_MAX_RAD);

        double cos = Math.cos(wobbleAngle), sin = Math.sin(wobbleAngle);
        double dirX = tangent.getX() * cos - tangent.getZ() * sin;
        double dirZ = tangent.getX() * sin + tangent.getZ() * cos;

        Vector2 velocity = body.getLinearVelocity(); // dyn4j (x, y) == world (x, z)
        double forwardSpeed = velocity.x * dirX + velocity.y * dirZ;
        double lateralX = velocity.x - dirX * forwardSpeed; // collision/momentum component perpendicular to drive direction
        double lateralZ = velocity.y - dirZ * forwardSpeed;

        double newForwardSpeed = forwardSpeed + (targetSpeed - forwardSpeed) * DRIVE_BLEND;

        // Only the forward component is steered toward the target; the lateral
        // component is passed through untouched so marble-vs-marble jostling
        // and overtaking still come entirely from real collision, not the motor.
        double vx = dirX * newForwardSpeed + lateralX;
        double vz = dirZ * newForwardSpeed + lateralZ;

        double maxSpeed = baseTargetSpeed * MAX_SPEED_MULTIPLE;
        double speedSq = vx * vx + vz * vz;
        if (speedSq > maxSpeed * maxSpeed) {
            double scale = maxSpeed / Math.sqrt(speedSq);
            vx *= scale;
            vz *= scale;
        }

        body.setLinearVelocity(vx, vz);
    }

    /**
     * Detects a marble that hasn't made meaningful progress in a while --
     * wedged in a harsh corner, jammed against another marble, whatever --
     * and forces it free with a decisive kick (extra forward push plus a
     * sideways shove, alternating sides on repeated stalls in case one
     * direction is the actual wedge) instead of relying on the normal drive
     * to eventually work it out. Returns true if a kick was applied this
     * tick, in which case the normal drive is skipped so it doesn't
     * immediately overwrite the kick.
     */
    private boolean checkAndHandleStall() {
        if (lastS - stallCheckpointS >= STALL_PROGRESS_EPSILON) {
            stallCheckpointS = lastS;
            stallTicks = 0;
            return false;
        }

        stallTicks++;
        if (stallTicks < STALL_TICKS_THRESHOLD) return false;

        Vector tangent = physics.getSpline().tangentAt(lastS);
        double normalX = -tangent.getZ();
        double normalZ = tangent.getX();

        double forwardKick = baseTargetSpeed * STALL_KICK_FORWARD_MULT;
        double lateralKick = baseTargetSpeed * STALL_KICK_LATERAL_MULT * stallKickSign;

        body.setLinearVelocity(
                tangent.getX() * forwardKick + normalX * lateralKick,
                tangent.getZ() * forwardKick + normalZ * lateralKick
        );

        stallKickSign = -stallKickSign;
        stallTicks = 0;
        stallCheckpointS = lastS;
        return true;
    }

    /**
     * Reads the body's position back after the shared World has stepped, updates progress and the
     * display entity, and detects finish. Returns false once this runner should be removed.
     */
    boolean syncAfterStep() {
        if (finished) return false;

        Vector2 pos = body.getWorldCenter(); // dyn4j (x, y) == world (x, z)
        double x = pos.x, z = pos.y;

        // Safety net on top of dyn4j's own continuous collision detection: if
        // this tick still somehow left the body inside solid geometry (e.g. a
        // fast body sweeping exactly through the seam between two abutting
        // block colliders), revert to the last known-clear spot and kill
        // velocity so it doesn't immediately retry the same path into the
        // wall. Checked against the floor height near last tick's progress,
        // since that's the best estimate of "where the track actually is"
        // before this tick's new progress is resolved below.
        double floorY = physics.getSpline().positionAt(lastS).getY();
        if (TrackPhysics.isWallAt(physics.getBukkitWorld(), x, floorY, z)) {
            body.translate(lastGoodX - x, lastGoodZ - z);
            body.setLinearVelocity(0, 0);
            x = lastGoodX;
            z = lastGoodZ;
        }

        double s = physics.getSpline().project(x, z, lastS);
        lastS = s;
        lastGoodX = x;
        lastGoodZ = z;

        double y = physics.getSpline().positionAt(s).getY() - DISPLAY_Y_OFFSET;
        Vector tangent = physics.getSpline().tangentAt(s);
        float yaw = yawFromVector(tangent);

        Location newLoc = new Location(physics.getBukkitWorld(), x, y, z, yaw, 0f);
        stand.teleport(newLoc);

        if (s >= totalLength - FINISH_EPSILON) {
            finished = true;
            physics.unregisterMarble(body);
            stand.remove();
            if (finishListener != null) finishListener.onFinish();
            return false;
        }
        return true;
    }

    /** How far along the track (arc-length, in blocks) this runner has traveled so far. */
    public double getProgress() {
        return lastS;
    }

    public double getTotalLength() {
        return totalLength;
    }

    TrackPhysics getPhysics() {
        return physics;
    }

    /** Immediately despawns this runner without firing its FinishListener. For admin cleanup of stray/stuck runners. */
    void forceRemove() {
        if (finished) return;
        finished = true;
        physics.unregisterMarble(body);
        stand.remove();
    }

    private float yawFromVector(Vector v) {
        return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
    }

    public Location getLocation() {
        return stand != null && stand.isValid() ? stand.getLocation() : null;
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
