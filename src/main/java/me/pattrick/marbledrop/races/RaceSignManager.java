package me.pattrick.marbledrop.races;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class RaceSignManager {

    public record SignKey(String world, int x, int y, int z) {
        public static SignKey from(Location loc) {
            if (loc == null || loc.getWorld() == null) return null;
            return new SignKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x + 0.5, y + 0.5, z + 0.5);
        }
        public String path() {
            return world + "," + x + "," + y + "," + z;
        }
    }

    public record RaceSign(String trackId, String action) { }

    private final Plugin plugin;

    private final Map<SignKey, RaceSign> signs = new HashMap<>();

    private final File file;
    private final FileConfiguration cfg;

    public RaceSignManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "race-signs.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public void reload() {
        signs.clear();
        cfg.setDefaults(null);
        load();
    }

    public void save() {
        cfg.set("signs", null);
        for (Map.Entry<SignKey, RaceSign> e : signs.entrySet()) {
            String key = e.getKey().path();
            cfg.set("signs." + key + ".trackId", e.getValue().trackId());
            cfg.set("signs." + key + ".action", e.getValue().action());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save race-signs.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            // ensure folder exists
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            save();
            return;
        }

        var sec = cfg.getConfigurationSection("signs");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            String trackId = cfg.getString("signs." + key + ".trackId", "menu");
            String action = cfg.getString("signs." + key + ".action", "menu");

            SignKey sk = parseKey(key);
            if (sk == null) continue;

            signs.put(sk, new RaceSign(
                    trackId == null ? "menu" : trackId.toLowerCase(),
                    action == null ? "menu" : action.toLowerCase()
            ));
        }
    }

    private SignKey parseKey(String s) {
        try {
            String[] parts = s.split(",");
            if (parts.length != 4) return null;
            String world = parts[0];
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new SignKey(world, x, y, z);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void set(Location loc, String trackId, String action) {
        SignKey key = SignKey.from(loc);
        if (key == null) return;

        if (trackId == null || trackId.isBlank()) trackId = "menu";
        if (action == null || action.isBlank()) action = "menu";

        signs.put(key, new RaceSign(trackId.toLowerCase(), action.toLowerCase()));
        save();
    }

    public void remove(Location loc) {
        SignKey key = SignKey.from(loc);
        if (key == null) return;

        if (signs.remove(key) != null) {
            save();
        }
    }

    public RaceSign get(Location loc) {
        SignKey key = SignKey.from(loc);
        if (key == null) return null;
        return signs.get(key);
    }

    public boolean isRaceSign(Location loc) {
        return get(loc) != null;
    }
}
