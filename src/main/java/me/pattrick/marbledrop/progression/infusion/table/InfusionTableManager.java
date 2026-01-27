package me.pattrick.marbledrop.progression.infusion.table;

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

public final class InfusionTableManager {

    private final Plugin plugin;
    private final File file;
    private FileConfiguration cfg;

    private final Set<String> keys = new HashSet<>();

    public InfusionTableManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "infusion_tables.yml");
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
        if (cfg.isConfigurationSection("tables")) {
            keys.addAll(cfg.getConfigurationSection("tables").getKeys(false));
        }
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Made public so other systems can key markers consistently
    public String keyOf(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public boolean isTable(Location loc) {
        return keys.contains(keyOf(loc));
    }

    public boolean addTable(Location loc) {
        String k = keyOf(loc);
        if (keys.contains(k)) return false;

        keys.add(k);
        cfg.set("tables." + k + ".world", loc.getWorld().getName());
        cfg.set("tables." + k + ".x", loc.getBlockX());
        cfg.set("tables." + k + ".y", loc.getBlockY());
        cfg.set("tables." + k + ".z", loc.getBlockZ());
        save();
        return true;
    }

    public boolean removeTable(Location loc) {
        String k = keyOf(loc);
        if (!keys.contains(k)) return false;

        keys.remove(k);
        cfg.set("tables." + k, null);
        save();
        return true;
    }

    /**
     * NEW: Convenience accessor for ambient visuals.
     * Returns block locations of all infusion tables.
     */
    public Set<Location> getTables() {
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
            } catch (NumberFormatException ignored) {
                // ignore bad entries
            }
        }
        return out;
    }
}
