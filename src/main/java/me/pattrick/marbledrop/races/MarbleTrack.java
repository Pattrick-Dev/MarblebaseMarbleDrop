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

    // Cached, corner-smoothed version of `points`, used for actual race
    // physics (see MarbleRunner). Recomputed whenever points change.
    // The raw `points` list above is left untouched -- track editing
    // tools (TrackGuiListener, TrackVisualizer, etc.) still work off it
    // directly, so admins always see/edit exactly what they placed.
    private List<Location> racePoints = new ArrayList<>();

    // ✅ Optional watch spot for spectating
    private Location watchLocation;

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

    /** Corner-smoothed path used for race physics. See TrackSmoother. */
    public List<Location> getRacePoints() {
        return racePoints;
    }

    private void recomputeRacePath() {
        racePoints = TrackSmoother.smooth(points);
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
}
