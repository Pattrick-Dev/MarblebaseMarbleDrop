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
 * <p>
 * Coordinates are kept as raw (world name, x/y/z) data and the {@link World}
 * is only resolved on demand in {@link #get} - NOT once at load() time. A
 * world created by another plugin isn't guaranteed to be loaded yet when
 * this plugin enables; resolving eagerly at boot meant a slot whose world
 * loaded a moment too late was silently dropped for the rest of that server
 * session, which made isComplete() report false and silently fell the whole
 * CRAFT step display back to the (fully non-interactive, click-cancelling)
 * TutorialCraftGui popup instead - see TutorialLocationStore for the same
 * fix and the fuller writeup of why this class of bug happens.
 */
public final class TutorialCraftFrameStore {

    public static final int SLOT_COUNT = 9;

    private final Plugin plugin;
    private final File file;
    private final RawLocation[] slots = new RawLocation[SLOT_COUNT];

    private record RawLocation(String world, double x, double y, double z) {
        static RawLocation of(Location loc) {
            return new RawLocation(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        }
    }

    public TutorialCraftFrameStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tutorial-craft-frames.yml");
        load();
    }

    public Location get(int slot) {
        RawLocation raw = slots[slot];
        if (raw == null) return null;

        World world = Bukkit.getWorld(raw.world());
        if (world == null) {
            plugin.getLogger().warning("Craft frame slot " + slot + " references world '" + raw.world() +
                    "', which isn't currently loaded - skipping.");
            return null;
        }
        return new Location(world, raw.x(), raw.y(), raw.z());
    }

    public void set(int slot, Location location) {
        slots[slot] = RawLocation.of(location);
    }

    public boolean isComplete() {
        for (RawLocation loc : slots) {
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

            slots[i] = new RawLocation(
                    yml.getString(path + ".world"),
                    yml.getDouble(path + ".x"),
                    yml.getDouble(path + ".y"),
                    yml.getDouble(path + ".z"));
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();

        for (int i = 0; i < SLOT_COUNT; i++) {
            RawLocation loc = slots[i];
            if (loc == null) continue;

            String path = "slots." + i;
            yml.set(path + ".world", loc.world());
            yml.set(path + ".x", loc.x());
            yml.set(path + ".y", loc.y());
            yml.set(path + ".z", loc.z());
        }

        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save tutorial-craft-frames.yml: " + e.getMessage());
        }
    }
}
