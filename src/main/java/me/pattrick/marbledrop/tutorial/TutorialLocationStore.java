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
 */
public final class TutorialLocationStore {

    private final Plugin plugin;
    private final File file;
    private final Map<TutorialStep, Location> locations = new EnumMap<>(TutorialStep.class);
    private String raceTrackId;
    private Location postTutorialLocation;

    public TutorialLocationStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tutorial-locations.yml");
        load();
    }

    public Location get(TutorialStep step) {
        return locations.get(step);
    }

    public boolean has(TutorialStep step) {
        return locations.containsKey(step);
    }

    public void set(TutorialStep step, Location location) {
        locations.put(step, location.clone());
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
        return postTutorialLocation;
    }

    public void setPostTutorialLocation(Location location) {
        this.postTutorialLocation = location.clone();
        save();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        this.raceTrackId = yml.getString("race-track-id", null);

        if (yml.contains("post-tutorial.world")) {
            String worldName = yml.getString("post-tutorial.world");
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            if (world != null) {
                this.postTutorialLocation = new Location(
                        world,
                        yml.getDouble("post-tutorial.x"),
                        yml.getDouble("post-tutorial.y"),
                        yml.getDouble("post-tutorial.z"),
                        (float) yml.getDouble("post-tutorial.yaw"),
                        (float) yml.getDouble("post-tutorial.pitch")
                );
            } else {
                plugin.getLogger().warning("Post-tutorial location references missing world '" + worldName + "', skipping.");
            }
        }

        for (TutorialStep step : TutorialStep.values()) {
            String path = step.name();
            if (!yml.contains(path + ".world")) continue;

            String worldName = yml.getString(path + ".world");
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            if (world == null) {
                plugin.getLogger().warning("Tutorial location for " + path +
                        " references missing world '" + worldName + "', skipping.");
                continue;
            }

            double x = yml.getDouble(path + ".x");
            double y = yml.getDouble(path + ".y");
            double z = yml.getDouble(path + ".z");
            float yaw = (float) yml.getDouble(path + ".yaw");
            float pitch = (float) yml.getDouble(path + ".pitch");

            locations.put(step, new Location(world, x, y, z, yaw, pitch));
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();

        if (raceTrackId != null) {
            yml.set("race-track-id", raceTrackId);
        }

        if (postTutorialLocation != null) {
            yml.set("post-tutorial.world", postTutorialLocation.getWorld().getName());
            yml.set("post-tutorial.x", postTutorialLocation.getX());
            yml.set("post-tutorial.y", postTutorialLocation.getY());
            yml.set("post-tutorial.z", postTutorialLocation.getZ());
            yml.set("post-tutorial.yaw", (double) postTutorialLocation.getYaw());
            yml.set("post-tutorial.pitch", (double) postTutorialLocation.getPitch());
        }

        for (Map.Entry<TutorialStep, Location> entry : locations.entrySet()) {
            String path = entry.getKey().name();
            Location loc = entry.getValue();

            yml.set(path + ".world", loc.getWorld().getName());
            yml.set(path + ".x", loc.getX());
            yml.set(path + ".y", loc.getY());
            yml.set(path + ".z", loc.getZ());
            yml.set(path + ".yaw", (double) loc.getYaw());
            yml.set(path + ".pitch", (double) loc.getPitch());
        }

        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save tutorial-locations.yml: " + e.getMessage());
        }
    }
}
