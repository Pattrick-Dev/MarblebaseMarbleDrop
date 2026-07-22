package me.pattrick.marbledrop.races;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;

/**
 * Path-progress marble movement.
 * <p>
 * A marble's forward position is purely a function of how far it's
 * traveled along the track's (corner-smoothed) arc length -- a single
 * monotonically-increasing `progress` value. Forward progress NEVER
 * depends on colliding with or clearing anything in the world, so it
 * is mathematically impossible for a marble to get stuck on a corner.
 * <p>
 * Sideways (lateral) position is layered on top of that guaranteed
 * forward motion, and IS aware of the physical world -- but only to
 * answer "how much room is there to spread out here," never to block
 * or redirect forward movement. Each tick, a runner samples how much
 * clear space exists to its left and right (a handful of block checks,
 * not real collision), then places itself somewhere across that width
 * based on a persistent per-runner "lane" preference, with real
 * marble-vs-marble separation layered on top so two marbles can never
 * render inside each other. If sampling ever finds zero clearance for
 * some reason, the marble simply hugs the centerline -- still a fully
 * valid, un-stuck position.
 */
public final class MarbleRunner {

    public interface FinishListener {
        void onFinish();
    }

    // --------------------
    // Tunables
    // --------------------

    // Lateral clearance sampling: how far out (and how finely) each
    // runner checks for solid blocks to either side, to know how much
    // of the track's actual physical width it's safe to use this tick.
    private static final double CLEARANCE_STEP = 0.20;
    private static final double CLEARANCE_MAX_SEARCH = 1.5;
    private static final double CLEARANCE_MARGIN = 0.18; // roughly a marble's own radius
    private static final double CLEARANCE_SAFETY_FACTOR = 0.85; // stay a bit inside sampled clearance, not right at the edge
    private static final double CLEARANCE_SAMPLE_HEIGHT_LOW = 0.30;
    private static final double CLEARANCE_SAMPLE_HEIGHT_HIGH = 0.90;

    // Small organic wobble layered on top of the lane position, purely cosmetic.
    private static final double WANDER_ACCEL = 0.004;
    private static final double WANDER_DECAY = 0.90;
    private static final double WANDER_MAX = 0.10;

    // Real marble-vs-marble separation -- this is what actually prevents
    // two marbles from ever rendering on top of / inside each other.
    private static final double MIN_SEPARATION = 0.36; // roughly a marble's diameter plus a small buffer

    // Per-tick speed variance (chaos stat) and occasional burst (aggression
    // stat) -- kept purely for personality/flavor, same intent as before.
    private static final double SPEED_WOBBLE_ACCEL = 0.008;
    private static final double SPEED_WOBBLE_DECAY = 0.90;
    private static final double SPEED_WOBBLE_MAX = 0.35;
    private static final double AGGRESSION_BURST_CHANCE = 0.01; // per tick, scaled by aggression
    private static final double AGGRESSION_BURST_MULT = 1.5;

    private final MarbleTrack track; // kept for reference/future use; not read for movement
    private final List<Location> path; // resolved once from track.getRacePoints() -- see MarbleTrack/TrackSmoother
    private final ArmorStand stand;

    private final double[] cumulativeDist; // arc-length from path[0] to path[i], size == path.size()
    private final double totalLength;
    private final double baseWorldSpeed; // world units of arc-length covered per tick at "normal" speed

    private double progress = 0.0; // arc-length traveled so far, 0..totalLength -- monotonic, never decreases
    private int segmentHint = 0;   // search hint into cumulativeDist; only ever advances forward

    private final double speedPerTick;
    private final FinishListener finishListener;

    private final double chaos;       // 0..1
    private final double aggression;  // 0..1

    // Persistent per-runner preferred lane across the track's width, in
    // [-1, 1] (negative = left side, positive = right side). Set once at
    // construction so each marble has a consistent "racing line" rather
    // than randomly drifting lane to lane.
    private final double laneBias;

    private final Random rng = new Random();

    private double speedWobble = 0.0;
    private double wander;
    private double wanderVel = 0.0;

    // Backwards-compatible constructors
    public MarbleRunner(MarbleTrack track, Location start) {
        this(track, start, null, 0.02, 0.35, 0.50, null);
    }

    public MarbleRunner(MarbleTrack track, Location start, ItemStack helmet, double speedPerTick) {
        this(track, start, helmet, speedPerTick, 0.35, 0.50, null);
    }

    public MarbleRunner(MarbleTrack track, Location start, ItemStack helmet, double speedPerTick, FinishListener finishListener) {
        this(track, start, helmet, speedPerTick, 0.35, 0.50, finishListener);
    }

    public MarbleRunner(MarbleTrack track, Location start, ItemStack helmet, double speedPerTick,
                        double chaos, double aggression, FinishListener finishListener) {
        this.track = track;
        this.path = (track != null) ? track.getRacePoints() : java.util.Collections.emptyList();
        this.speedPerTick = speedPerTick;
        this.finishListener = finishListener;
        this.chaos = clamp01(chaos);
        this.aggression = clamp01(aggression);
        this.stand = spawnMarble(start, helmet);

        this.cumulativeDist = computeCumulativeDistances(this.path);
        this.totalLength = (cumulativeDist.length > 0) ? cumulativeDist[cumulativeDist.length - 1] : 0.0;

        int segCount = Math.max(1, this.path.size() - 1);
        double avgSegLen = (this.totalLength > 0.0) ? (this.totalLength / segCount) : 1.0;
        this.baseWorldSpeed = speedPerTick * avgSegLen;

        this.laneBias = (rng.nextDouble() * 2.0) - 1.0;
        this.wander = ((rng.nextDouble() * 2.0) - 1.0) * (0.55 + 0.45 * this.chaos) * WANDER_MAX;
    }

    private static double[] computeCumulativeDistances(List<Location> pts) {
        if (pts == null || pts.isEmpty()) return new double[0];

        double[] out = new double[pts.size()];
        out[0] = 0.0;
        for (int i = 1; i < pts.size(); i++) {
            out[i] = out[i - 1] + pts.get(i - 1).distance(pts.get(i));
        }
        return out;
    }

    private ArmorStand spawnMarble(Location loc, ItemStack helmet) {
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

        return as;
    }

    public boolean tick() {
        return tick(null);
    }

    public boolean tick(List<MarbleRunner> allRunners) {

        if (path == null || path.size() < 2 || totalLength <= 0.0) {
            stand.remove();
            return false;
        }

        if (progress >= totalLength) {
            if (finishListener != null) finishListener.onFinish();
            stand.remove();
            return false;
        }

        // --------------------
        // Advance race progress (this is the ONLY thing that determines
        // where the marble is along the track -- everything below is
        // sideways placement layered on top, and never feeds back into
        // this value).
        // --------------------
        speedWobble += (rng.nextDouble() - 0.5) * SPEED_WOBBLE_ACCEL * chaos;
        speedWobble *= SPEED_WOBBLE_DECAY;
        speedWobble = clamp(speedWobble, -SPEED_WOBBLE_MAX, SPEED_WOBBLE_MAX);

        double effectiveSpeed = baseWorldSpeed * (1.0 + speedWobble);
        if (rng.nextDouble() < AGGRESSION_BURST_CHANCE * aggression) {
            effectiveSpeed *= AGGRESSION_BURST_MULT;
        }
        if (effectiveSpeed < 0) effectiveSpeed = 0;

        progress += effectiveSpeed;
        if (progress > totalLength) progress = totalLength;

        // --------------------
        // Resolve exact on-track centerline position + tangent direction
        // --------------------
        while (segmentHint < cumulativeDist.length - 2 && cumulativeDist[segmentHint + 1] <= progress) {
            segmentHint++;
        }
        int i = segmentHint;

        Location aLoc = path.get(i);
        Location bLoc = path.get(i + 1);
        World world = aLoc.getWorld();
        Vector a = aLoc.toVector();
        Vector b = bLoc.toVector();

        Vector segVec = b.clone().subtract(a);
        double segLen3D = segVec.length();
        Vector forward = (segLen3D > 1e-6) ? segVec.clone().multiply(1.0 / segLen3D) : new Vector(0, 0, 1);
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());

        double segStart = cumulativeDist[i];
        double segEnd = cumulativeDist[i + 1];
        double segLen = segEnd - segStart;
        double t = (segLen > 1e-6) ? clamp((progress - segStart) / segLen, 0.0, 1.0) : 0.0;

        double baseX = a.getX() + (b.getX() - a.getX()) * t;
        double baseY = a.getY() + (b.getY() - a.getY()) * t;
        double baseZ = a.getZ() + (b.getZ() - a.getZ()) * t;

        // --------------------
        // How much room is there to spread out here? Purely informational
        // -- never used to block or slow forward progress above.
        // --------------------
        double rightClear = computeClearance(world, baseX, baseY, baseZ, right) * CLEARANCE_SAFETY_FACTOR;
        double leftClear = computeClearance(world, baseX, baseY, baseZ, right.clone().multiply(-1)) * CLEARANCE_SAFETY_FACTOR;

        // --------------------
        // Lane position: spread across the actual available width based
        // on this marble's persistent lane preference, plus a little
        // organic wobble on top for liveliness.
        // --------------------
        wanderVel += (rng.nextDouble() - 0.5) * WANDER_ACCEL * chaos;
        wanderVel *= WANDER_DECAY;
        wander += wanderVel;
        wander = clamp(wander, -WANDER_MAX, WANDER_MAX);

        double laneOffset = (laneBias >= 0) ? laneBias * rightClear : laneBias * leftClear; // laneBias negative -> leftClear side
        double lateral = clamp(laneOffset + wander, -leftClear, rightClear);

        // --------------------
        // Real separation from other marbles -- this is what prevents
        // phasing through each other. Re-clamped to sampled clearance
        // after every push, so separation can never shove a marble
        // through a wall either -- on a track too narrow for two marbles
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
                }
            }
        }

        float yaw = yawFromVector(forward);
        Location newLoc = new Location(world, candX, baseY, candZ, yaw, 0f);
        stand.teleport(newLoc);

        return true;
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
        return progress;
    }

    public double getTotalLength() {
        return totalLength;
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
