package me.pattrick.marbledrop.races;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class TrackManager {

    private final Map<String, MarbleTrack> tracks = new HashMap<>();
    private final TrackStorage storage;

    public TrackManager(Plugin plugin) {
        this.storage = new TrackStorage(plugin);
        tracks.putAll(storage.loadAll());
    }

    public void saveNow() {
        storage.saveAll(tracks.values());
    }

    public boolean createTrack(String id, World world) {
        if (id == null || id.isBlank()) return false;
        if (world == null) return false;

        id = id.toLowerCase();
        if (tracks.containsKey(id)) return false;

        tracks.put(id, new MarbleTrack(id, world));
        saveNow();
        return true;
    }

    public boolean addPoint(String id, Location loc) {
        if (id == null || loc == null) return false;

        id = id.toLowerCase();
        MarbleTrack track = tracks.get(id);
        if (track == null) return false;

        // enforce same world
        if (!track.getWorld().equals(loc.getWorld())) return false;

        track.addPoint(loc);
        saveNow();
        return true;
    }

    public boolean undoLast(String id) {
        if (id == null) return false;

        id = id.toLowerCase();
        MarbleTrack track = tracks.get(id);
        if (track == null) return false;

        if (track.size() == 0) return false;

        track.removeLastPoint();
        saveNow();
        return true;
    }

    public boolean removeTrack(String id) {
        if (id == null) return false;

        id = id.toLowerCase();
        boolean removed = (tracks.remove(id) != null);
        if (removed) saveNow();
        return removed;
    }

    public MarbleTrack getTrack(String id) {
        if (id == null) return null;
        return tracks.get(id.toLowerCase());
    }

    public boolean setWatchLocation(String id, Location loc) {
        if (id == null || id.isBlank() || loc == null) return false;

        id = id.toLowerCase();
        MarbleTrack track = tracks.get(id);
        if (track == null) return false;

        if (!track.getWorld().equals(loc.getWorld())) return false;

        track.setWatchLocation(loc);
        saveNow();
        return true;
    }

    public boolean clearWatchLocation(String id) {
        if (id == null || id.isBlank()) return false;

        id = id.toLowerCase();
        MarbleTrack track = tracks.get(id);
        if (track == null) return false;

        track.setWatchLocation(null);
        saveNow();
        return true;
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(tracks.keySet());
    }

    public List<String> sortedIds() {
        List<String> list = new ArrayList<>(tracks.keySet());
        Collections.sort(list);
        return list;
    }

    /** Tracks an admin has explicitly opted into the scheduled race queue (see /md track autorace). */
    public List<String> autoRaceEligibleIds() {
        List<String> list = new ArrayList<>();
        for (MarbleTrack t : tracks.values()) {
            if (t.isAutoRaceEligible()) list.add(t.getId());
        }
        Collections.sort(list);
        return list;
    }

    public Collection<MarbleTrack> getTracks() {
        return Collections.unmodifiableCollection(tracks.values());
    }
}
