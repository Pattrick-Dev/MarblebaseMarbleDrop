package me.pattrick.marbledrop.races;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Corner-smoothed, densely-resampled version of a track's raw waypoints.
 * Built once per track edit (see MarbleTrack) via a Catmull-Rom curve
 * through the raw waypoints, then walked at a fixed arc-length step.
 * <p>
 * Used both to answer, for a given world (x, z), "how far along the track
 * is that" (see project()), and as the centerline MarbleRunner's forward
 * progress is measured against and its lateral clearance is sampled from
 * (see isWallAt()/measureClearance()/measureWidthAt() below) - forward
 * progress itself never depends on any of this, only cosmetic placement
 * does, so a marble can never physically wedge against real geometry.
 */
public final class TrackSpline {

    private static final double SAMPLE_STEP = 0.25; // arc-length between resampled points, in blocks
    private static final int SUBDIVISIONS_PER_SEGMENT = 16; // Catmull-Rom evaluations per control-point span, before resampling
    private static final int PROJECT_WINDOW = 40; // samples searched either side of the hint (~10 blocks at SAMPLE_STEP)
    private static final double REPROJECT_THRESHOLD = 4.0; // blocks; beyond this the windowed search falls back to a full scan

    public static final double MARBLE_RADIUS = 0.18; // roughly how much visual space a marble occupies, for grid spacing/separation

    // How far above a sampled point to check for solid blocks - two heights
    // to catch typical wall heights without being tripped up by ankle-high
    // lips in the track.
    private static final double WALL_SCAN_HEIGHT_LOW = 0.30;
    private static final double WALL_SCAN_HEIGHT_HIGH = 0.90;

    private final World world;
    private final List<Location> samples; // dense, evenly arc-length-spaced points along the curve
    private final double[] cumulative;     // cumulative arc length to samples.get(i); same size as samples

    private TrackSpline(World world, List<Location> samples, double[] cumulative) {
        this.world = world;
        this.samples = samples;
        this.cumulative = cumulative;
    }

    public static TrackSpline build(List<Location> raw) {
        if (raw == null || raw.isEmpty()) {
            return new TrackSpline(null, Collections.emptyList(), new double[0]);
        }
        World world = raw.get(0).getWorld();
        if (raw.size() < 2) {
            return new TrackSpline(world, List.of(raw.get(0).clone()), new double[]{0.0});
        }

        List<Location> dense = evaluateCatmullRom(raw);
        return resample(world, dense);
    }

    // --------------------------------------------------------------
    // Catmull-Rom evaluation
    // --------------------------------------------------------------

    // Centripetal (alpha=0.5), not uniform - see evaluateCatmullRom() for why.
    private static final double CATMULL_ROM_ALPHA = 0.5;

    private static List<Location> evaluateCatmullRom(List<Location> raw) {
        int n = raw.size();

        // Phantom points before the first and after the last waypoint so the
        // open curve's end tangents follow the first/last real segment
        // instead of going flat.
        List<Location> padded = new ArrayList<>(n + 2);
        padded.add(extrapolate(raw.get(1), raw.get(0)));
        padded.addAll(raw);
        padded.add(extrapolate(raw.get(n - 2), raw.get(n - 1)));

        List<Location> dense = new ArrayList<>();
        for (int k = 0; k < n - 1; k++) {
            Location p0 = padded.get(k);
            Location p1 = padded.get(k + 1);
            Location p2 = padded.get(k + 2);
            Location p3 = padded.get(k + 3);

            // Knot spacing from actual inter-point distance (raised to
            // CATMULL_ROM_ALPHA), not a fixed 0..1 per segment - uniform
            // parameterization assumes every segment takes the same "time"
            // to traverse regardless of how far apart the real waypoints
            // are, which is what let the curve overshoot past a waypoint
            // and swing back on itself whenever waypoints ended up unevenly
            // spaced (exactly what walking-and-placing them produces) -
            // reads in-race as a marble zipping out to a point and back
            // instead of rolling straight through it. Centripetal spacing
            // (this) is the standard fix: it's guaranteed cusp/loop-free
            // for any point placement.
            double t0 = 0;
            double t1 = t0 + Math.pow(p0.distance(p1), CATMULL_ROM_ALPHA);
            double t2 = t1 + Math.pow(p1.distance(p2), CATMULL_ROM_ALPHA);
            double t3 = t2 + Math.pow(p2.distance(p3), CATMULL_ROM_ALPHA);

            boolean lastSegment = (k == n - 2);
            int steps = lastSegment ? SUBDIVISIONS_PER_SEGMENT + 1 : SUBDIVISIONS_PER_SEGMENT;
            for (int s = 0; s < steps; s++) {
                double frac = (double) s / SUBDIVISIONS_PER_SEGMENT;
                double t = t1 + frac * (t2 - t1);
                dense.add(catmullRomPoint(p0, p1, p2, p3, t0, t1, t2, t3, t));
            }
        }
        return dense;
    }

    private static Location extrapolate(Location from, Location to) {
        double x = to.getX() * 2 - from.getX();
        double y = to.getY() * 2 - from.getY();
        double z = to.getZ() * 2 - from.getZ();
        return new Location(to.getWorld(), x, y, z);
    }

    /** Barry-Goldman recursive interpolation for non-uniform (centripetal) Catmull-Rom, evaluated per axis. */
    private static Location catmullRomPoint(Location p0, Location p1, Location p2, Location p3,
                                              double t0, double t1, double t2, double t3, double t) {
        double x = catmullRom1D(p0.getX(), p1.getX(), p2.getX(), p3.getX(), t0, t1, t2, t3, t);
        double y = catmullRom1D(p0.getY(), p1.getY(), p2.getY(), p3.getY(), t0, t1, t2, t3, t);
        double z = catmullRom1D(p0.getZ(), p1.getZ(), p2.getZ(), p3.getZ(), t0, t1, t2, t3, t);
        return new Location(p1.getWorld(), x, y, z);
    }

    private static double catmullRom1D(double p0, double p1, double p2, double p3,
                                        double t0, double t1, double t2, double t3, double t) {
        double a1 = lerp1D(p0, p1, t0, t1, t);
        double a2 = lerp1D(p1, p2, t1, t2, t);
        double a3 = lerp1D(p2, p3, t2, t3, t);
        double b1 = lerp1D(a1, a2, t0, t2, t);
        double b2 = lerp1D(a2, a3, t1, t3, t);
        return lerp1D(b1, b2, t1, t2, t);
    }

    // Guards ta==tb (coincident source points, or a degenerate padded
    // segment) by just holding v0 instead of dividing by ~0.
    private static double lerp1D(double v0, double v1, double ta, double tb, double t) {
        double denom = tb - ta;
        if (denom < 1e-9) return v0;
        return v0 + (v1 - v0) * (t - ta) / denom;
    }

    // --------------------------------------------------------------
    // Arc-length resampling
    // --------------------------------------------------------------

    private static TrackSpline resample(World world, List<Location> dense) {
        List<Location> samples = new ArrayList<>();
        samples.add(dense.get(0).clone());

        double carry = 0.0;
        Location prev = dense.get(0);
        for (int i = 1; i < dense.size(); i++) {
            Location cur = dense.get(i);
            double segLen = prev.distance(cur);
            double consumed = 0.0;

            while (carry + (segLen - consumed) >= SAMPLE_STEP) {
                double remaining = SAMPLE_STEP - carry;
                double t = (segLen > 1e-9) ? (consumed + remaining) / segLen : 1.0;
                samples.add(lerp(prev, cur, t));
                consumed += remaining;
                carry = 0.0;
            }
            carry += (segLen - consumed);
            prev = cur;
        }

        Location last = dense.get(dense.size() - 1);
        if (samples.get(samples.size() - 1).distance(last) > 1e-6) {
            samples.add(last.clone());
        }

        double[] cumulative = new double[samples.size()];
        for (int i = 1; i < samples.size(); i++) {
            cumulative[i] = cumulative[i - 1] + samples.get(i - 1).distance(samples.get(i));
        }
        return new TrackSpline(world, samples, cumulative);
    }

    private static Location lerp(Location a, Location b, double t) {
        double x = a.getX() + (b.getX() - a.getX()) * t;
        double y = a.getY() + (b.getY() - a.getY()) * t;
        double z = a.getZ() + (b.getZ() - a.getZ()) * t;
        return new Location(a.getWorld(), x, y, z);
    }

    // --------------------------------------------------------------
    // Queries
    // --------------------------------------------------------------

    public World getWorld() {
        return world;
    }

    public List<Location> getSamples() {
        return Collections.unmodifiableList(samples);
    }

    public double totalLength() {
        return cumulative.length == 0 ? 0.0 : cumulative[cumulative.length - 1];
    }

    /** World-space point on the curve at arc-length s (clamped to [0, totalLength]), including interpolated Y. */
    public Location positionAt(double s) {
        if (samples.isEmpty()) return null;
        s = clamp(s, 0, totalLength());
        int i = indexFloor(s);
        if (i >= samples.size() - 1) return samples.get(samples.size() - 1).clone();

        Location a = samples.get(i), b = samples.get(i + 1);
        double segLen = cumulative[i + 1] - cumulative[i];
        double t = (segLen > 1e-9) ? (s - cumulative[i]) / segLen : 0.0;
        return lerp(a, b, t);
    }

    /** Normalized horizontal (x/z) direction of travel at arc-length s. */
    public Vector tangentAt(double s) {
        if (samples.size() < 2) return new Vector(0, 0, 1);
        int i = indexFloor(clamp(s, 0, totalLength()));
        if (i >= samples.size() - 1) i = samples.size() - 2;
        if (i < 0) i = 0;

        Location a = samples.get(i), b = samples.get(i + 1);
        Vector dir = b.toVector().subtract(a.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1e-9) return new Vector(0, 0, 1);
        return dir.normalize();
    }

    /**
     * Evenly spaced starting-grid positions for `count` racers at the start of
     * this spline (s = 0), fanned out across `usableWidth` (centered on the
     * centerline) and staggered into further-back rows - offset behind the
     * start line along the reverse tangent, not along the spline itself - once
     * a row is full, so racers never spawn overlapping and jamming each other.
     */
    public List<Location> startingGrid(int count, double usableWidth, double spacing) {
        List<Location> out = new ArrayList<>(Math.max(0, count));
        if (count <= 0 || samples.isEmpty()) return out;

        Location start = positionAt(0);
        Vector tangent = tangentAt(0);
        Vector normal = new Vector(-tangent.getZ(), 0, tangent.getX());

        int perRow = Math.max(1, (int) Math.floor(usableWidth / spacing) + 1);

        for (int i = 0; i < count; i++) {
            int row = i / perRow;
            int col = i % perRow;
            int colsInThisRow = Math.min(perRow, count - row * perRow);

            double lateral = (col - (colsInThisRow - 1) / 2.0) * spacing;
            double back = row * spacing;

            Location loc = start.clone()
                    .add(normal.clone().multiply(lateral))
                    .subtract(tangent.clone().multiply(back));
            out.add(loc);
        }
        return out;
    }

    /** True if a solid block sits at (x, z) near floorY - real Minecraft geometry, not a simulated collider. */
    public static boolean isWallAt(World world, double x, double floorY, double z) {
        if (world == null) return false;
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return world.getBlockAt(bx, (int) Math.floor(floorY + WALL_SCAN_HEIGHT_LOW), bz).getType().isSolid()
                || world.getBlockAt(bx, (int) Math.floor(floorY + WALL_SCAN_HEIGHT_HIGH), bz).getType().isSolid();
    }

    /**
     * How far (in blocks) it's clear to move in direction `dir` from `base`
     * before hitting a solid block, capped at `maxSearch`. Used both for the
     * starting grid's width measurement and for a runner's own per-tick
     * lateral clearance sampling (see MarbleRunner).
     */
    public static double measureClearance(World world, Location base, Vector dir, double maxSearch) {
        if (world == null) return maxSearch;
        for (double d = 0.2; d <= maxSearch; d += 0.2) {
            double x = base.getX() + dir.getX() * d;
            double z = base.getZ() + dir.getZ() * d;
            if (isWallAt(world, x, base.getY(), z)) {
                return d;
            }
        }
        return maxSearch;
    }

    /** Real (block-based) clear width at arc-length s, measured left+right from the centerline. */
    public double measureWidthAt(double s, double maxSearch) {
        if (world == null) return maxSearch * 2;

        Location base = positionAt(s);
        Vector tangent = tangentAt(s);
        Vector normal = new Vector(-tangent.getZ(), 0, tangent.getX());

        double right = measureClearance(world, base, normal, maxSearch);
        double left = measureClearance(world, base, normal.clone().multiply(-1), maxSearch);
        return left + right;
    }

    /**
     * Nearest arc-length position on the curve to world point (x, z), searching
     * near hintS first for speed. Pass hintS &lt; 0 to force a full scan (e.g. on
     * first registration, when there's no prior position to hint from).
     */
    public double project(double x, double z, double hintS) {
        if (samples.size() < 2) return 0.0;

        int hintIdx = (hintS < 0) ? -1 : indexFloor(clamp(hintS, 0, totalLength()));
        int lo, hi;
        if (hintIdx < 0) {
            lo = 0;
            hi = samples.size() - 2;
        } else {
            lo = Math.max(0, hintIdx - PROJECT_WINDOW);
            hi = Math.min(samples.size() - 2, hintIdx + PROJECT_WINDOW);
        }

        double bestDistSq = Double.MAX_VALUE;
        double bestS = 0.0;

        for (int i = lo; i <= hi; i++) {
            Location a = samples.get(i), b = samples.get(i + 1);
            double ax = a.getX(), az = a.getZ();
            double dx = b.getX() - ax, dz = b.getZ() - az;
            double segLenSq = dx * dx + dz * dz;
            double t = (segLenSq > 1e-9) ? ((x - ax) * dx + (z - az) * dz) / segLenSq : 0.0;
            t = clamp(t, 0, 1);

            double px = ax + dx * t, pz = az + dz * t;
            double ddx = x - px, ddz = z - pz;
            double distSq = ddx * ddx + ddz * ddz;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestS = cumulative[i] + t * (cumulative[i + 1] - cumulative[i]);
            }
        }

        if (hintIdx >= 0 && bestDistSq > REPROJECT_THRESHOLD * REPROJECT_THRESHOLD) {
            return project(x, z, -1); // fell outside the search window - fall back to a full scan once
        }
        return bestS;
    }

    private int indexFloor(double s) {
        int lo = 0, hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (cumulative[mid] <= s) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    private static double clamp(double v, double a, double b) {
        if (v < a) return a;
        if (v > b) return b;
        return v;
    }
}
