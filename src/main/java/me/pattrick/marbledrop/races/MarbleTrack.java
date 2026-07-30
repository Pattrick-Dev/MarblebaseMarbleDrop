package me.pattrick.marbledrop.races;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MarbleTrack {

    private final String id;
    private final World world;
    private final List<Location> points = new ArrayList<>();

    // Cached Catmull-Rom spline through `points`, used for race physics
    // (see TrackPhysics/MarbleRunner). Recomputed whenever points change.
    // The raw `points` list above is left untouched -- track editing
    // tools (TrackGuiListener, TrackVisualizer, etc.) still work off it
    // directly, so admins always see/edit exactly what they placed.
    private TrackSpline spline = TrackSpline.build(points);

    // ✅ Optional watch spot for spectating
    private Location watchLocation;

    // Opt-in flag: only tracks marked eligible are candidates for the
    // scheduled server race's random pick (see ScheduledRaceManager).
    private boolean autoRaceEligible;

    public MarbleTrack(String id, World world) {
        this.id = id;
        this.world = world;
    }

    public String getId() {
        return id;
    }

    public World getWorld() {
        return world;
    }

    public void addPoint(Location loc) {
        if (loc == null) return;
        points.add(loc.clone());
        recomputeRacePath();
    }

    public int size() {
        return points.size();
    }

    public Location getPoint(int index) {
        return points.get(index);
    }

    public List<Location> getPoints() {
        return Collections.unmodifiableList(points);
    }

    /** Corner-smoothed spline used for race physics. See TrackPhysics. */
    public TrackSpline getSpline() {
        return spline;
    }

    private void recomputeRacePath() {
        spline = TrackSpline.build(points);
    }

    public Location getWatchLocation() {
        return (watchLocation == null) ? null : watchLocation.clone();
    }

    public void setWatchLocation(Location loc) {
        this.watchLocation = (loc == null) ? null : loc.clone();
    }

    void removeLastPoint() {
        if (!points.isEmpty()) points.remove(points.size() - 1);
        recomputeRacePath();
    }

    public boolean isAutoRaceEligible() {
        return autoRaceEligible;
    }

    public void setAutoRaceEligible(boolean eligible) {
        this.autoRaceEligible = eligible;
    }
}
