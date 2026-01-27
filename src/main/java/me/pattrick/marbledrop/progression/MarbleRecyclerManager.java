package me.pattrick.marbledrop.progression;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class MarbleRecyclerManager {

    private final Plugin plugin;
    private final File file;
    private FileConfiguration cfg;

    private final Set<String> keys = new HashSet<>();

    public MarbleRecyclerManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "recyclers.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);

        keys.clear();
        if (cfg.isConfigurationSection("recyclers")) {
            keys.addAll(cfg.getConfigurationSection("recyclers").getKeys(false));
        }
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String keyOf(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public boolean isRecycler(Location loc) {
        return keys.contains(keyOf(loc));
    }

    public boolean addRecycler(Location loc) {
        String k = keyOf(loc);
        if (keys.contains(k)) return false;

        keys.add(k);
        cfg.set("recyclers." + k + ".world", loc.getWorld().getName());
        cfg.set("recyclers." + k + ".x", loc.getBlockX());
        cfg.set("recyclers." + k + ".y", loc.getBlockY());
        cfg.set("recyclers." + k + ".z", loc.getBlockZ());
        save();
        return true;
    }

    public boolean removeRecycler(Location loc) {
        String k = keyOf(loc);
        if (!keys.contains(k)) return false;

        keys.remove(k);
        cfg.set("recyclers." + k, null);
        save();
        return true;
    }

    public int count() {
        return keys.size();
    }

    public Set<Location> getRecyclers() {
        Set<Location> out = new HashSet<>();
        for (String k : keys) {
            String[] parts = k.split(",");
            if (parts.length != 4) continue;

            World w = Bukkit.getWorld(parts[0]);
            if (w == null) continue;

            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                out.add(new Location(w, x, y, z));
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }
}
