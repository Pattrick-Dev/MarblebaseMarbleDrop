package me.pattrick.marbledrop.progression.infusion.table;

import me.pattrick.marbledrop.Main;
import me.pattrick.marbledrop.marble.MarbleData;
import me.pattrick.marbledrop.marble.MarbleItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class CatalystValue {

    private CatalystValue() {}

    public static int valueOf(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(CatalystValue.class);

        // --- Marble catalyst (based on stats) ---
        if (MarbleItem.isMarble(item)) {
            int v = marbleCatalystValue(plugin, item);
            return v * Math.max(1, item.getAmount());
        }

        // --- Normal item catalyst (material values) ---
        int per = materialValue(plugin, item.getType());
        return per * Math.max(1, item.getAmount());
    }

    private static int marbleCatalystValue(JavaPlugin plugin, ItemStack item) {
        // defaults
        int divisor = cfgInt(plugin, "infusion.catalyst.marble.stat-sum-divisor", 5);
        int min = cfgInt(plugin, "infusion.catalyst.marble.min", 10);
        int max = cfgInt(plugin, "infusion.catalyst.marble.max", 1000);

        MarbleData data = null;
        try {
            data = MarbleItem.read(item);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Infusion] Failed to read marble catalyst PDC: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        if (data == null || data.getStats() == null) {
            plugin.getLogger().warning("[Infusion] Marble catalyst detected but MarbleItem.read() returned null stats. Using fallback default-item-value.");
            return cfgInt(plugin, "infusion.catalyst.default-item-value", 10);
        }

        int sum = data.getStats().total();
        int d = Math.max(1, divisor);
        int raw = Math.max(0, sum / d);

        int clamped = raw;
        if (clamped < min) clamped = min;
        if (clamped > max) clamped = max;

        return clamped;
    }

    private static int materialValue(JavaPlugin plugin, Material mat) {
        int defaultValue = cfgInt(plugin, "infusion.catalyst.default-item-value", 10);

        // support config map:
        // infusion:
        //   catalyst:
        //     material-values:
        //       DIAMOND: 200
        //       EMERALD: 180
        ConfigurationSection sec = cfgSection(plugin, "infusion.catalyst.material-values");
        if (sec == null) return defaultValue;

        String key = mat.name().toUpperCase(Locale.ROOT);
        if (!sec.contains(key)) return defaultValue;

        int v = sec.getInt(key, defaultValue);
        return Math.max(0, v);
    }

// ✅ Replace your existing cfgInt/cfgSection (and any similar ones) with these:

    private static int cfgInt(JavaPlugin plugin, String path, int def) {
        try {
            return plugin.getConfig().getInt(path, def);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Config] Failed to read int '" + path + "': " + t.getMessage());
            return def;
        }
    }

    private static boolean cfgBool(JavaPlugin plugin, String path, boolean def) {
        try {
            return plugin.getConfig().getBoolean(path, def);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Config] Failed to read boolean '" + path + "': " + t.getMessage());
            return def;
        }
    }

    private static double cfgDouble(JavaPlugin plugin, String path, double def) {
        try {
            return plugin.getConfig().getDouble(path, def);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Config] Failed to read double '" + path + "': " + t.getMessage());
            return def;
        }
    }

    private static String cfgString(JavaPlugin plugin, String path, String def) {
        try {
            String v = plugin.getConfig().getString(path);
            return (v == null) ? def : v;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Config] Failed to read string '" + path + "': " + t.getMessage());
            return def;
        }
    }

    private static ConfigurationSection cfgSection(JavaPlugin plugin, String path) {
        try {
            return plugin.getConfig().getConfigurationSection(path);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Config] Failed to read section '" + path + "': " + t.getMessage());
            return null;
        }
    }

}
