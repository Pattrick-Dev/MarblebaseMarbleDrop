package me.pattrick.marbledrop.marble;

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

/**
 * Tracks the block locations of every currently-placed marble display, so
 * MarbleDisplayAmbient has something to iterate without scanning loaded
 * chunks for player-head blocks every tick. Same shape as
 * MarbleRecyclerManager/InfusionTableManager/UpgradeStationManager --
 * a YAML-backed set of "world,x,y,z" keys.
 */
public final class PlacedMarbleManager {

    private final Plugin plugin;
    private final File file;
    private FileConfiguration cfg;

    private final Set<String> keys = new HashSet<>();

    public PlacedMarbleManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "placed-marbles.yml");
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
        if (cfg.isConfigurationSection("marbles")) {
            keys.addAll(cfg.getConfigurationSection("marbles").getKeys(false));
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

    public boolean isPlacedMarble(Location loc) {
        return keys.contains(keyOf(loc));
    }

    public boolean addMarble(Location loc) {
        String k = keyOf(loc);
        if (keys.contains(k)) return false;

        keys.add(k);
        cfg.set("marbles." + k + ".world", loc.getWorld().getName());
        cfg.set("marbles." + k + ".x", loc.getBlockX());
        cfg.set("marbles." + k + ".y", loc.getBlockY());
        cfg.set("marbles." + k + ".z", loc.getBlockZ());
        save();
        return true;
    }

    public boolean removeMarble(Location loc) {
        String k = keyOf(loc);
        if (!keys.contains(k)) return false;

        keys.remove(k);
        cfg.set("marbles." + k, null);
        save();
        return true;
    }

    public int count() {
        return keys.size();
    }

    public Set<Location> getMarbles() {
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
