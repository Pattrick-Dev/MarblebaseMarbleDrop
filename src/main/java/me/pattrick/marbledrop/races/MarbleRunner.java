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

public final class MarbleRunner {

    public interface FinishListener {
        void onFinish();
    }

    // --------------------
    // Tunables
    // --------------------
    private static final double MARBLE_RADIUS = 0.16;

    // prevent tunneling
    private static final double STEP_SIZE = 0.06;

    // sample “wall height”
    private static final double SAMPLE_Y1 = 0.35;
    private static final double SAMPLE_Y2 = 0.95;

    private static final double NUDGE = 0.02;

    // repulsion
    private static final double MIN_SEPARATION = 0.34;
    private static final double REPULSE_RANGE = 0.70;
    private static final double BASE_REPULSE = 0.11;

    // motion damping
    private static final double AIR_DRAG = 0.992;

    // forward “slope” acceleration (guarantees progress, but still allows chaos)
    // IMPORTANT: we keep it small and NOT scaled by baseWorld to prevent speed explosion
    private static final double SLOPE_ACCEL = 0.010;

    // cap speed so it stays sane
    // IMPORTANT: lowered effective multiplier inside tick to keep “old feel”
    private static final double MAX_SPEED_MULT_SOFT = 1.35;

    // steering keeps them generally aimed down segment (not rail-guided)
    private static final double STEER_STRENGTH = 0.040;
    private static final double STEER_LOOKAHEAD = 0.75;

    // small wander (so lanes diverge a bit)
    private static final double WANDER_ACCEL = 0.012;
    private static final double WANDER_FORCE = 0.020; // slightly reduced to prevent wall ping-pong
    private static final double WANDER_MAX = 0.75;

    // overtakes
    private static final double OVERTAKE_RANGE = 1.00;
    private static final double OVERTAKE_FORCE = 0.030; // slightly reduced

    // wall collision response
    private static final double WALL_BOUNCE = 0.70;      // 0=dead stop sideways, 1=perfect bounce
    private static final double WALL_FRICTION = 0.88;    // slows overall velocity when scraping walls
    private static final double MIN_FORWARD_AFTER_HIT = 0.02;

    // finish threshold
    private static final double SEG_ADVANCE_EPS = 0.10;

    // stuck handling
    private static final int STUCK_TICKS = 8;
    private static final double STUCK_MOVE_EPS = 0.0025;
    private static final double STUCK_FORWARD_TELEPORT = 0.35;

    private final MarbleTrack track;
    private final List<Location> path; // resolved once from track.getRacePoints() -- see MarbleTrack/TrackSmoother
    private final ArmorStand stand;

    private int segment = 0;

    private final double speedPerTick;
    private final FinishListener finishListener;

    private final double chaos;       // 0..1
    private final double aggression;  // 0..1

    private final Random rng = new Random();

    // momentum velocity (XZ)
    private Vector vel = new Vector(0, 0, 0);

    // wander state
    private double wander = (rng.nextDouble() * 2.0) - 1.0;
    private double wanderVel = 0.0;

    // stuck tracking
    private int stuckTicks = 0;

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

        // initial tiny random
        this.vel = new Vector((rng.nextDouble() - 0.5) * 0.010, 0, (rng.nextDouble() - 0.5) * 0.010);
        this.wander *= (0.55 + 0.45 * this.chaos);
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

        if (path == null || path.size() < 2) {
            stand.remove();
            return false;
        }

        if (segment >= path.size() - 1) {
            if (finishListener != null) finishListener.onFinish();
            stand.remove();
            return false;
        }

        Location aLoc = path.get(segment);
        Location bLoc = path.get(segment + 1);

        Vector a = aLoc.toVector();
        Vector b = bLoc.toVector();

        Vector seg = b.clone().subtract(a);
        double segLen = seg.length();
        if (segLen <= 0.0001) {
            segment++;
            return true;
        }

        Vector forward = seg.clone().multiply(1.0 / segLen);
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());

        // XZ-only length used for segment advance detection and Y interpolation
        double segLenXZ = Math.sqrt(seg.getX() * seg.getX() + seg.getZ() * seg.getZ());
        if (segLenXZ < 0.0001) segLenXZ = segLen; // vertical-only segment fallback

        // convert your base stat speed into world speed scale (this is the “old feel” anchor)
        double baseWorld = speedPerTick * segLen;

        // --------------------
        // Steering: gently aim down track (not rail)
        // --------------------
        Vector pos = stand.getLocation().toVector();

        // How far through this segment we already are (0 = just started,
        // 1 = at the end). Used below to start anticipating a sharp turn
        // before actually reaching it, instead of driving straight at the
        // wall and relying on wall-bounce physics to survive the corner.
        double segProgress = clamp(projAlong(pos, a, forward) / segLenXZ, 0.0, 1.0);

        double look = clamp(STEER_LOOKAHEAD, 0.10, 0.95);
        Vector aim;

        if (segProgress > 0.55 && segment + 2 < path.size()) {
            Vector nextDir = path.get(segment + 2).toVector().subtract(b);
            nextDir.setY(0);

            if (nextDir.lengthSquared() > 1e-6) {
                nextDir.normalize();
                double blend = clamp((segProgress - 0.55) / 0.45, 0.0, 1.0);
                Vector blendedDir = forward.clone().multiply(1.0 - blend).add(nextDir.multiply(blend));
                if (blendedDir.lengthSquared() > 1e-6) {
                    blendedDir.normalize();
                } else {
                    blendedDir = forward.clone();
                }
                aim = pos.clone().add(blendedDir.multiply(Math.max(0.4, segLen * look)));
            } else {
                aim = a.clone().add(forward.clone().multiply(segLen * look));
            }
        } else {
            aim = a.clone().add(forward.clone().multiply(segLen * look));
        }

        Vector toAim = aim.clone().subtract(pos);
        toAim.setY(0);

        Vector steerDir = (toAim.lengthSquared() > 1e-6) ? toAim.normalize() : forward.clone();
        Vector desiredVel = steerDir.clone().multiply(baseWorld);

        double steer = STEER_STRENGTH * (1.10 - 0.60 * chaos) * (0.90 + 0.25 * aggression);
        if (steer < 0.015) steer = 0.015;

        Vector steerAccel = desiredVel.clone().subtract(vel).multiply(steer);

        // --------------------
        // Forward “slope”: always accelerates forward (SMALL + NOT scaled)
        // --------------------
        // This prevents “way faster than before”.
        Vector slopeAccel = forward.clone().multiply(SLOPE_ACCEL * (1.00 - 0.25 * chaos));

        // --------------------
        // Wander: small sideways wiggle
        // --------------------
        wanderVel += (rng.nextDouble() - 0.5) * 2.0 * WANDER_ACCEL * chaos;
        wanderVel *= 0.94;
        wander += wanderVel;
        if (wander > WANDER_MAX) wander = WANDER_MAX;
        if (wander < -WANDER_MAX) wander = -WANDER_MAX;

        Vector wanderAccel = right.clone().multiply(wander * WANDER_FORCE * (0.55 + 0.45 * chaos));

        // --------------------
        // Repulsion + overtake
        // --------------------
        Vector repel = computeRepulsion(allRunners).multiply(0.65 + 0.45 * chaos);
        Vector overtake = computeOvertake(allRunners, forward, right);

        // integrate velocity
        vel.add(steerAccel);
        vel.add(slopeAccel);
        vel.add(wanderAccel);
        vel.add(repel);
        vel.add(overtake);
        vel.setY(0);

        // HARD anchor: keep overall speed near stat-based speed
        // (prevents the “way faster than before” problem)
        double maxSpeed = Math.max(0.06, baseWorld * MAX_SPEED_MULT_SOFT);
        double spd = vel.length();
        if (spd > maxSpeed) vel.multiply(maxSpeed / spd);

        // air drag
        vel.multiply(AIR_DRAG);

        // Prevent backward motion: always maintain at least 20% of base forward speed.
        // Repulsion from a marble directly ahead is the main cause of backward velocity.
        // Boosted up to 35% in the last ~45% of a segment (the same corner-approach
        // window used for steering above) so marbles don't lose all their momentum
        // right as they need it most to punch through a turn.
        double fwdFloorRatio = 0.20;
        if (segProgress > 0.55) {
            double boost = clamp((segProgress - 0.55) / 0.45, 0.0, 1.0);
            fwdFloorRatio = 0.20 + 0.15 * boost;
        }
        double fwdFloor = baseWorld * fwdFloorRatio;
        double fwdDot = vel.dot(forward);
        if (fwdDot < fwdFloor) {
            vel.add(forward.clone().multiply(fwdFloor - fwdDot));
        }

        // --------------------
        // Move with swept collision and REAL wall response
        // --------------------
        Location cur = stand.getLocation();
        // Pre-snap cur's Y to track height so collision detection doesn't falsely
        // hit the ledge block above when the marble has descended partway down a slope.
        double sCur = projAlong(cur.toVector(), a, forward);
        double tCur = clamp(sCur / segLenXZ, 0.0, 1.0);
        cur.setY(aLoc.getY() + tCur * (bLoc.getY() - aLoc.getY()));

        MoveResult mr = moveSweptWithWallPhysics(cur, vel, forward);

        Location next = mr.loc;
        vel = mr.newVel;

        // Re-enforce forward floor after wall bounces can reflect velocity backward.
        fwdDot = vel.dot(forward);
        if (fwdDot < fwdFloor) {
            vel.add(forward.clone().multiply(fwdFloor - fwdDot));
        }

        // Snap Y to track slope so marbles follow inclines/declines without flying off.
        double sAlong = projAlong(next.toVector(), a, forward);
        double tSeg = clamp(sAlong / segLenXZ, 0.0, 1.0);
        next.setY(aLoc.getY() + tSeg * (bLoc.getY() - aLoc.getY()));

        // yaw from motion
        Vector actual = next.toVector().subtract(cur.toVector());
        if (actual.lengthSquared() > 1e-6) next.setYaw(yawFromVector(actual));
        next.setPitch(0f);

        stand.teleport(next);

        // --------------------
        // Stuck detection + escape
        // --------------------
        // Measure XZ-only movement so Y-snapping a slope step doesn't reset the stuck counter.
        double dxz = Math.sqrt(Math.pow(next.getX() - cur.getX(), 2) + Math.pow(next.getZ() - cur.getZ(), 2));
        if (dxz < STUCK_MOVE_EPS) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }

        if (stuckTicks >= STUCK_TICKS) {
            // Fan through angles around forward to find a clear escape direction.
            // Previous code only tried straight forward; if that block was solid the
            // marble was silently not moved and immediately stuck again next cycle.
            double[] escAngles = {0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2};
            for (double angle : escAngles) {
                double cos = Math.cos(angle), sin = Math.sin(angle);
                Vector dir = new Vector(
                        forward.getX() * cos - forward.getZ() * sin,
                        0,
                        forward.getX() * sin + forward.getZ() * cos
                ).multiply(STUCK_FORWARD_TELEPORT);
                Location esc = next.clone().add(dir);
                if (!collides(next.getWorld(), esc)) {
                    stand.teleport(esc);
                    break;
                }
            }

            vel = forward.clone().multiply(Math.max(0.04, baseWorld * 0.90));
            if (rng.nextDouble() < 0.45) wander = -wander;
            stuckTicks = 0;
        }

        // --------------------
        // Segment progression by projection
        // --------------------
        double s = projAlong(stand.getLocation().toVector(), a, forward);
        if (s >= segLenXZ - SEG_ADVANCE_EPS) {
            segment++;
        }

        if (segment >= path.size() - 1) {
            if (finishListener != null) finishListener.onFinish();
            stand.remove();
            return false;
        }

        return true;
    }

    // ---------------------------------------------
    // Movement with wall physics + corner escape:
    // - sweep substeps
    // - axis reflect + friction
    // - if fully stuck, attempt diagonal/forward escape before giving up
    // ---------------------------------------------

    private static final class MoveResult {
        final Location loc;
        final Vector newVel;
        MoveResult(Location loc, Vector newVel) {
            this.loc = loc;
            this.newVel = newVel;
        }
    }

    private MoveResult moveSweptWithWallPhysics(Location start, Vector velIn, Vector forward) {
        World world = start.getWorld();
        if (world == null) return new MoveResult(start, velIn);

        Vector move = velIn.clone();
        double len = move.length();
        if (len <= 1e-9) return new MoveResult(start, velIn);

        int steps = (int) Math.ceil(len / STEP_SIZE);
        if (steps < 1) steps = 1;

        Vector step = move.clone().multiply(1.0 / steps);

        Location cur = start.clone();
        Vector v = velIn.clone();

        for (int i = 0; i < steps; i++) {
            Location attempt = cur.clone().add(step);

            if (!collides(world, attempt)) {
                cur = attempt;
                continue;
            }

            // try x-only and z-only to determine collision axis
            Location xOnly = cur.clone().add(step.getX(), 0, 0);
            boolean xOk = !collides(world, xOnly);

            Location zOnly = cur.clone().add(0, 0, step.getZ());
            boolean zOk = !collides(world, zOnly);

            if (xOk && !zOk) {
                cur = xOnly;
                v.setZ(-v.getZ() * WALL_BOUNCE);
                v.multiply(WALL_FRICTION);
            } else if (zOk && !xOk) {
                cur = zOnly;
                v.setX(-v.getX() * WALL_BOUNCE);
                v.multiply(WALL_FRICTION);
            } else if (xOk) {
                if (Math.abs(step.getX()) >= Math.abs(step.getZ())) {
                    cur = xOnly;
                    v.setZ(-v.getZ() * WALL_BOUNCE);
                } else {
                    cur = zOnly;
                    v.setX(-v.getX() * WALL_BOUNCE);
                }
                v.multiply(WALL_FRICTION);
            } else if (zOk) {
                cur = zOnly;
                v.setX(-v.getX() * WALL_BOUNCE);
                v.multiply(WALL_FRICTION);
            } else {
                // Corner / concave trap: attempt a few escape probes.

                // 1) diagonal micro-step (often clears corner)
                Location diag = cur.clone().add(step.getX() * 0.5, 0, step.getZ() * 0.5);
                if (!collides(world, diag)) {
                    cur = diag;
                } else {
                    // 2) forward micro-step
                    Location fwd = cur.clone().add(forward.clone().multiply(0.08));
                    if (!collides(world, fwd)) {
                        cur = fwd;
                    } else {
                        // 3) nudge attempts
                        Location n1 = cur.clone().add(Math.signum(step.getX()) * NUDGE, 0, 0);
                        if (!collides(world, n1)) cur = n1;

                        Location n2 = cur.clone().add(0, 0, Math.signum(step.getZ()) * NUDGE);
                        if (!collides(world, n2)) cur = n2;
                    }
                }

                // heavy friction but keep some forward
                v.multiply(0.65);
            }

            // preserve forward motion: enforce minimum forward component after collision
            double fwdDot = v.dot(forward);
            if (fwdDot < MIN_FORWARD_AFTER_HIT) {
                v.add(forward.clone().multiply(MIN_FORWARD_AFTER_HIT - fwdDot));
            }

            // update step for remaining substeps from updated v
            double remaining = (steps - i - 1);
            if (remaining > 0) {
                Vector newMove = v.clone();
                double newLen = newMove.length();
                if (newLen > 1e-6) {
                    double target = len * (remaining / (double) steps);
                    newMove.multiply(target / newLen);
                    step = newMove.multiply(1.0 / remaining);
                }
            }
        }

        v.setY(0);
        return new MoveResult(cur, v);
    }

    // ---------------------------------------------
    // Collision sampling
    // ---------------------------------------------

    private boolean collides(World world, Location loc) {
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        double y1 = y + SAMPLE_Y1;
        double y2 = y + SAMPLE_Y2;

        // Cardinal-only sampling: diagonal sample points fire on block corners that the
        // marble should slide past cleanly, causing false "corner trap" stalls.
        return isSolid(world, x + MARBLE_RADIUS, y1, z) ||
                isSolid(world, x - MARBLE_RADIUS, y1, z) ||
                isSolid(world, x, y1, z + MARBLE_RADIUS) ||
                isSolid(world, x, y1, z - MARBLE_RADIUS) ||

                isSolid(world, x + MARBLE_RADIUS, y2, z) ||
                isSolid(world, x - MARBLE_RADIUS, y2, z) ||
                isSolid(world, x, y2, z + MARBLE_RADIUS) ||
                isSolid(world, x, y2, z - MARBLE_RADIUS);
    }

    private boolean isSolid(World world, double x, double y, double z) {
        Block b = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return b.getType().isSolid();
    }

    // ---------------------------------------------
    // Repulsion + overtakes
    // ---------------------------------------------

    private Vector computeRepulsion(List<MarbleRunner> allRunners) {
        if (allRunners == null || allRunners.isEmpty()) return new Vector(0, 0, 0);

        Vector myPos = stand.getLocation().toVector();

        Vector force = new Vector(0, 0, 0);

        for (MarbleRunner other : allRunners) {
            if (other == null || other == this) continue;
            if (other.stand == null || !other.stand.isValid()) continue;

            Vector d = myPos.clone().subtract(other.stand.getLocation().toVector());
            d.setY(0);

            double dist = d.length();
            if (dist < 1e-6) continue;
            if (dist > REPULSE_RANGE) continue;

            double closeness = (REPULSE_RANGE - dist) / REPULSE_RANGE;
            double hard = 0.0;
            if (dist < MIN_SEPARATION) hard = (MIN_SEPARATION - dist) / MIN_SEPARATION;

            double strength = (BASE_REPULSE * (0.30 + 0.70 * closeness)) + (hard * 0.20);
            force.add(d.multiply(1.0 / dist).multiply(strength));
        }

        force.setY(0);
        return force;
    }

    private Vector computeOvertake(List<MarbleRunner> allRunners, Vector forwardDir, Vector rightDir) {
        if (allRunners == null || allRunners.isEmpty()) return new Vector(0, 0, 0);

        Vector myPos = stand.getLocation().toVector();

        MarbleRunner best = null;
        double bestDist = Double.MAX_VALUE;

        for (MarbleRunner other : allRunners) {
            if (other == null || other == this) continue;
            if (other.stand == null || !other.stand.isValid()) continue;

            Vector to = other.stand.getLocation().toVector().subtract(myPos);
            to.setY(0);

            double dist = to.length();
            if (dist > OVERTAKE_RANGE) continue;

            double ahead = to.dot(forwardDir);
            if (ahead <= 0) continue;

            if (dist < bestDist) {
                bestDist = dist;
                best = other;
            }
        }

        if (best == null) return new Vector(0, 0, 0);

        double urgency = clamp01((OVERTAKE_RANGE - bestDist) / OVERTAKE_RANGE);
        double side = (wander >= 0) ? 1.0 : -1.0;

        double strength = OVERTAKE_FORCE * urgency * (0.35 + 0.65 * aggression);
        return rightDir.clone().multiply(side * strength);
    }

    // ---------------------------------------------
    // Helpers
    // ---------------------------------------------

    private double projAlong(Vector pos, Vector a, Vector forward) {
        Vector d = pos.clone().subtract(a);
        d.setY(0);
        // Use XZ-only forward so slope Y doesn't distort the progress measurement.
        Vector fXZ = new Vector(forward.getX(), 0, forward.getZ());
        double fXZLen = fXZ.length();
        if (fXZLen < 1e-6) return 0;
        return d.dot(fXZ) / fXZLen;
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
