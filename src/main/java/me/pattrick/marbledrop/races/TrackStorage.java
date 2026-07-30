package me.pattrick.marbledrop.races;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class TrackStorage {

    private final Plugin plugin;
    private final File file;

    public TrackStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tracks.yml");
    }

    public Map<String, MarbleTrack> loadAll() {
        Map<String, MarbleTrack> out = new HashMap<>();

        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            if (!file.exists()) file.createNewFile();

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = cfg.getConfigurationSection("tracks");
            if (root == null) return out;

            for (String id : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(id);
                if (sec == null) continue;

                String worldName = sec.getString("world");
                if (worldName == null || worldName.isEmpty()) continue;

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("[MarbleDrop] Track '" + id + "' world not loaded: " + worldName);
                    continue;
                }

                MarbleTrack track = new MarbleTrack(id, world);
                track.setAutoRaceEligible(sec.getBoolean("auto-race-eligible", false));

                // ✅ Optional watch location
                ConfigurationSection watch = sec.getConfigurationSection("watch");
                if (watch != null) {
                    double x = watch.getDouble("x");
                    double y = watch.getDouble("y");
                    double z = watch.getDouble("z");
                    float yaw = (float) watch.getDouble("yaw");
                    float pitch = (float) watch.getDouble("pitch");
                    track.setWatchLocation(new Location(world, x, y, z, yaw, pitch));
                }

                List<Map<?, ?>> points = sec.getMapList("points");
                for (Map<?, ?> m : points) {
                    double x = asDouble(m.get("x"));
                    double y = asDouble(m.get("y"));
                    double z = asDouble(m.get("z"));
                    float yaw = (float) asDouble(m.get("yaw"));
                    float pitch = (float) asDouble(m.get("pitch"));

                    track.addPoint(new Location(world, x, y, z, yaw, pitch));
                }

                out.put(id.toLowerCase(), track);
            }

        } catch (Exception ex) {
            plugin.getLogger().severe("[MarbleDrop] Failed to load tracks.yml: " + ex.getMessage());
            ex.printStackTrace();
        }

        return out;
    }

    public void saveAll(Collection<MarbleTrack> tracks) {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            if (!file.exists()) file.createNewFile();

            YamlConfiguration cfg = new YamlConfiguration();
            ConfigurationSection root = cfg.createSection("tracks");

            for (MarbleTrack t : tracks) {
                if (t == null) continue;

                ConfigurationSection sec = root.createSection(t.getId().toLowerCase());
                sec.set("world", t.getWorld().getName());
                sec.set("auto-race-eligible", t.isAutoRaceEligible());

                List<Map<String, Object>> pts = new ArrayList<>();
                for (Location loc : t.getPoints()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("x", loc.getX());
                    m.put("y", loc.getY());
                    m.put("z", loc.getZ());
                    m.put("yaw", loc.getYaw());
                    m.put("pitch", loc.getPitch());
                    pts.add(m);
                }

                sec.set("points", pts);

                // ✅ Save watch location
                Location w = t.getWatchLocation();
                if (w != null) {
                    ConfigurationSection watch = sec.createSection("watch");
                    watch.set("x", w.getX());
                    watch.set("y", w.getY());
                    watch.set("z", w.getZ());
                    watch.set("yaw", w.getYaw());
                    watch.set("pitch", w.getPitch());
                }
            }

            cfg.save(file);

        } catch (IOException ex) {
            plugin.getLogger().severe("[MarbleDrop] Failed to save tracks.yml: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static double asDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception ignored) {
            return 0.0;
        }
    }
}
