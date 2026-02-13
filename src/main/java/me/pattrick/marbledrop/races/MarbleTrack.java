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

    public Location getWatchLocation() {
        return (watchLocation == null) ? null : watchLocation.clone();
    }

    public void setWatchLocation(Location loc) {
        this.watchLocation = (loc == null) ? null : loc.clone();
    }

    void removeLastPoint() {
        if (!points.isEmpty()) points.remove(points.size() - 1);
    }
}
