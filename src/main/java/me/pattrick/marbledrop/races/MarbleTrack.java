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

    // Cached Catmull-Rom spline through `points`, used for actual race
    // physics (see MarbleRunner) -- gives a smooth, evenly arc-length
    // sampled curve with well-defined slope/curvature at any point along
    // it, which real gravity/cornering physics needs. Recomputed whenever
    // points change. The raw `points` list above is left untouched --
    // track editing tools (TrackGuiListener, TrackVisualizer, etc.) still
    // work off it directly, so admins always see/edit exactly what they
    // placed.
    private TrackSpline raceSpline = TrackSpline.build(new ArrayList<>());

    // ✅ Optional watch spot for spectating
    private Location watchLocation;

    private int laps = 1;

    // Opt-in flag for the scheduled race system (see ScheduledRaceManager) --
    // false by default so a track isn't auto-cycled before an admin has
    // actually finished building/testing it.
    private boolean autoRaceEligible = false;

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

    /** Smooth spline through the raw waypoints, used for race physics. See TrackSpline. */
    public TrackSpline getRaceSpline() {
        return raceSpline;
    }

    private void recomputeRacePath() {
        raceSpline = TrackSpline.build(points);
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

    public int getLaps() {
        return laps;
    }

    public void setLaps(int laps) {
        this.laps = Math.max(1, laps);
    }

    public boolean isAutoRaceEligible() {
        return autoRaceEligible;
    }

    public void setAutoRaceEligible(boolean autoRaceEligible) {
        this.autoRaceEligible = autoRaceEligible;
    }
}
