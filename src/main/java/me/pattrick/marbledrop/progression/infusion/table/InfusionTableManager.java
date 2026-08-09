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
    private final Set<String> privateKeys = new HashSet<>();

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
        privateKeys.clear();
        if (cfg.isConfigurationSection("tables")) {
            for (String k : cfg.getConfigurationSection("tables").getKeys(false)) {
                keys.add(k);
                if (cfg.getBoolean("tables." + k + ".private", false)) {
                    privateKeys.add(k);
                }
            }
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
        privateKeys.remove(k);
        cfg.set("tables." + k, null);
        save();
        return true;
    }

    /**
     * A "private" table skips the usual per-block lock and animates only
     * for the player using it (see InfusionTableProcess) instead of
     * broadcasting to everyone nearby -- lets more than one person use the
     * exact same physical table at once without colliding. Nothing about
     * the actual infusion (dust/catalyst cost, marble rolled) changes;
     * this only affects the shared animation/lock, which is otherwise
     * purely cosmetic. Intended for a dedicated onboarding table, but
     * works the same for anyone who uses it.
     */
    public boolean isPrivate(Location loc) {
        return privateKeys.contains(keyOf(loc));
    }

    public void setPrivate(Location loc, boolean isPrivate) {
        String k = keyOf(loc);
        if (!keys.contains(k)) return;

        if (isPrivate) {
            privateKeys.add(k);
        } else {
            privateKeys.remove(k);
        }
        cfg.set("tables." + k + ".private", isPrivate ? true : null);
        save();
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

    /** How many infusion tables are currently registered -- see StationCommands.count(). */
    public int count() {
        return keys.size();
    }
}
