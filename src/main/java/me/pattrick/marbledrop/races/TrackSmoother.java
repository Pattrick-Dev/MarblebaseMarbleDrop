package me.pattrick.marbledrop.races;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Softens sharp corners in a raw, admin-placed track path before it's
 * used for race physics. Straight sections and already-gentle curves
 * are left completely untouched -- only vertices where the direction
 * changes by more than SHARP_ANGLE_DEGREES get corner-cut.
 * <p>
 * This never changes what an admin sees or edits in the track-building
 * tools (those still work off the raw MarbleTrack points via
 * getPoints()/getPoint()/size()) -- it only affects the path
 * MarbleRunner actually simulates against, via
 * MarbleTrack.getRacePoints().
 */
public final class TrackSmoother {

    // Direction change (degrees) at a vertex above which it's treated
    // as a "sharp corner" worth softening. Below this, the turn is
    // gentle enough that the existing physics already handles it fine.
    // Tune this if corners still feel sticky (lower = more aggressive
    // smoothing) or if gentle curves start feeling "cut" unnecessarily
    // (raise it).
    private static final double SHARP_ANGLE_DEGREES = 35.0;

    // How far in from each neighboring point to cut the corner (0.0-0.5).
    // 0.25 is a standard Chaikin-style default: replaces the sharp
    // vertex with two points, each 25% of the way toward it from either
    // neighbor, turning one hard angle into two gentler ones.
    private static final double CUT_RATIO = 0.25;

    private TrackSmoother() {}

    public static List<Location> smooth(List<Location> raw) {
        if (raw == null) return new ArrayList<>();
        if (raw.size() < 3) return new ArrayList<>(raw); // nothing to smooth, no interior vertices

        List<Location> out = new ArrayList<>();
        out.add(raw.get(0).clone());

        for (int i = 1; i < raw.size() - 1; i++) {
            Location prev = raw.get(i - 1);
            Location cur = raw.get(i);
            Location next = raw.get(i + 1);

            double angle = turnAngleDegrees(prev, cur, next);

            if (angle >= SHARP_ANGLE_DEGREES) {
                Location before = lerp(prev, cur, 1.0 - CUT_RATIO);
                Location after = lerp(cur, next, CUT_RATIO);
                out.add(before);
                out.add(after);
            } else {
                out.add(cur.clone());
            }
        }

        out.add(raw.get(raw.size() - 1).clone());
        return out;
    }

    private static double turnAngleDegrees(Location prev, Location cur, Location next) {
        Vector v1 = cur.toVector().subtract(prev.toVector());
        Vector v2 = next.toVector().subtract(cur.toVector());

        if (v1.lengthSquared() < 1e-9 || v2.lengthSquared() < 1e-9) return 0.0;

        double cos = v1.normalize().dot(v2.normalize());
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }

    private static Location lerp(Location a, Location b, double t) {
        double x = a.getX() + (b.getX() - a.getX()) * t;
        double y = a.getY() + (b.getY() - a.getY()) * t;
        double z = a.getZ() + (b.getZ() - a.getZ()) * t;
        return new Location(a.getWorld(), x, y, z);
    }
}
