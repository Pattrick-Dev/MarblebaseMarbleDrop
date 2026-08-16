package me.pattrick.marbledrop.tutorial;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * Stores the 9 real-world locations of the admin's built 3x3 item frame
 * display (slot 0-8, row-major, top-left to bottom-right as viewed from
 * in front of the frames), persisted to tutorial-craft-frames.yml.
 * Populated in one shot via /md tutorial setcraftframes from a
 * WorldEdit/FAWE selection - see TutorialCraftFrameManager.setupFromSelection.
 */
public final class TutorialCraftFrameStore {

    public static final int SLOT_COUNT = 9;

    private final Plugin plugin;
    private final File file;
    private final Location[] slots = new Location[SLOT_COUNT];

    public TutorialCraftFrameStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tutorial-craft-frames.yml");
        load();
    }

    public Location get(int slot) {
        return slots[slot];
    }

    public void set(int slot, Location location) {
        slots[slot] = location.clone();
    }

    public boolean isComplete() {
        for (Location loc : slots) {
            if (loc == null) return false;
        }
        return true;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        for (int i = 0; i < SLOT_COUNT; i++) {
            String path = "slots." + i;
            if (!yml.contains(path + ".world")) continue;

            String worldName = yml.getString(path + ".world");
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            if (world == null) {
                plugin.getLogger().warning("Craft frame slot " + i + " references missing world '" + worldName + "', skipping.");
                continue;
            }

            slots[i] = new Location(world,
                    yml.getDouble(path + ".x"),
                    yml.getDouble(path + ".y"),
                    yml.getDouble(path + ".z"));
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();

        for (int i = 0; i < SLOT_COUNT; i++) {
            Location loc = slots[i];
            if (loc == null) continue;

            String path = "slots." + i;
            yml.set(path + ".world", loc.getWorld().getName());
            yml.set(path + ".x", loc.getX());
            yml.set(path + ".y", loc.getY());
            yml.set(path + ".z", loc.getZ());
        }

        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save tutorial-craft-frames.yml: " + e.getMessage());
        }
    }
}
