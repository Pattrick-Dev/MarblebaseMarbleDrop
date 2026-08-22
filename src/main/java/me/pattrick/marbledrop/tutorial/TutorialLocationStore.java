package me.pattrick.marbledrop.tutorial;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Stores one teleport checkpoint Location per TutorialStep, plus a single
 * tutorial race track id, persisted to tutorial-locations.yml. Set in-game
 * via /md tutorial setlocation <step> / clearlocation / setrace <trackId>.
 * <p>
 * Coordinates are kept as raw (world name, x/y/z/yaw/pitch) data and the
 * {@link World} is only resolved on demand in {@link #get}/{@link
 * #getPostTutorialLocation} - NOT once at load() time. A world created by
 * another plugin (Multiverse, etc.) isn't guaranteed to be loaded yet when
 * this plugin enables; resolving eagerly at boot meant a checkpoint whose
 * world loaded a moment too late was silently dropped for the rest of that
 * server session (see git history - this is exactly what happened on the
 * live server: "world"/"spawn-world" were both valid, just not necessarily
 * loaded before this ran). Resolving lazily, at the moment a player is
 * actually about to be teleported, sidesteps that boot-order race entirely.
 */
public final class TutorialLocationStore {

    private final Plugin plugin;
    private final File file;
    private final Map<TutorialStep, RawLocation> locations = new EnumMap<>(TutorialStep.class);
    private String raceTrackId;
    private RawLocation postTutorialLocation;

    private record RawLocation(String world, double x, double y, double z, float yaw, float pitch) {
        static RawLocation of(Location loc) {
            return new RawLocation(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        }
    }

    public TutorialLocationStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tutorial-locations.yml");
        load();
    }

    public Location get(TutorialStep step) {
        return resolve(locations.get(step), "Tutorial location for " + step.name());
    }

    public boolean has(TutorialStep step) {
        return locations.containsKey(step);
    }

    public void set(TutorialStep step, Location location) {
        locations.put(step, RawLocation.of(location));
        save();
    }

    public void clear(TutorialStep step) {
        locations.remove(step);
        save();
    }

    public String getRaceTrackId() {
        return raceTrackId;
    }

    public void setRaceTrackId(String trackId) {
        this.raceTrackId = trackId;
        save();
    }

    public Location getPostTutorialLocation() {
        return resolve(postTutorialLocation, "Post-tutorial location");
    }

    public void setPostTutorialLocation(Location location) {
        this.postTutorialLocation = RawLocation.of(location);
        save();
    }

    /** Null (with a warning) if the raw entry is unset or its world isn't currently loaded. */
    private Location resolve(RawLocation raw, String logLabel) {
        if (raw == null) return null;

        World world = Bukkit.getWorld(raw.world());
        if (world == null) {
            plugin.getLogger().warning(logLabel + " references world '" + raw.world() + "', which isn't currently loaded - skipping.");
            return null;
        }
        return new Location(world, raw.x(), raw.y(), raw.z(), raw.yaw(), raw.pitch());
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        this.raceTrackId = yml.getString("race-track-id", null);

        if (yml.contains("post-tutorial.world")) {
            this.postTutorialLocation = readRaw(yml, "post-tutorial");
        }

        for (TutorialStep step : TutorialStep.values()) {
            String path = step.name();
            if (!yml.contains(path + ".world")) continue;
            locations.put(step, readRaw(yml, path));
        }
    }

    private RawLocation readRaw(YamlConfiguration yml, String path) {
        return new RawLocation(
                yml.getString(path + ".world"),
                yml.getDouble(path + ".x"),
                yml.getDouble(path + ".y"),
                yml.getDouble(path + ".z"),
                (float) yml.getDouble(path + ".yaw"),
                (float) yml.getDouble(path + ".pitch")
        );
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();

        if (raceTrackId != null) {
            yml.set("race-track-id", raceTrackId);
        }

        if (postTutorialLocation != null) {
            writeRaw(yml, "post-tutorial", postTutorialLocation);
        }

        for (Map.Entry<TutorialStep, RawLocation> entry : locations.entrySet()) {
            writeRaw(yml, entry.getKey().name(), entry.getValue());
        }

        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save tutorial-locations.yml: " + e.getMessage());
        }
    }

    private void writeRaw(YamlConfiguration yml, String path, RawLocation loc) {
        yml.set(path + ".world", loc.world());
        yml.set(path + ".x", loc.x());
        yml.set(path + ".y", loc.y());
        yml.set(path + ".z", loc.z());
        yml.set(path + ".yaw", (double) loc.yaw());
        yml.set(path + ".pitch", (double) loc.pitch());
    }
}
