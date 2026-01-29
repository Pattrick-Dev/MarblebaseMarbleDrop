package me.pattrick.marbledrop.progression.infusion.table;

import me.pattrick.marbledrop.marble.MarbleData;
import me.pattrick.marbledrop.marble.MarbleItem;
import me.pattrick.marbledrop.marble.MarbleRarity;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class CatalystValue {

    // value per item (non-marble catalysts)
    private static final Map<Material, Integer> VALUES = Map.ofEntries(
            Map.entry(Material.DIAMOND, 200),
            Map.entry(Material.EMERALD, 180),
            Map.entry(Material.NETHERITE_INGOT, 800),
            Map.entry(Material.NETHERITE_SCRAP, 300),
            Map.entry(Material.GOLD_INGOT, 90),
            Map.entry(Material.IRON_INGOT, 45),
            Map.entry(Material.AMETHYST_SHARD, 70),
            Map.entry(Material.PRISMARINE_CRYSTALS, 60),
            Map.entry(Material.BLAZE_ROD, 85),
            Map.entry(Material.ENDER_PEARL, 75),
            Map.entry(Material.SHULKER_SHELL, 250)
    );

    private CatalystValue() {}

    /**
     * Catalyst value rules:
     * - If item is a Marble (PDC), value is based on that marble's stats (and scaled by rarity caps).
     * - Otherwise use the material table.
     * - If missing from table, default is 10 per item.
     * - Never fails silently: logs if marble read fails unexpectedly.
     */
    public static int valueOf(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;

        // ✅ MARBLE catalysts: stat-based value
        if (MarbleItem.isMarble(item)) {
            try {
                MarbleData data = MarbleItem.read(item);
                if (data == null) {
                    loud("[Infusion] CatalystValue: item claims marble PDC but MarbleItem.read() returned null. Defaulting to 10.");
                    return 10 * Math.max(1, item.getAmount());
                }

                int total = data.getStats().total();
                MarbleRarity r = data.getRarity();
                if (r == null) r = MarbleRarity.COMMON;

                // Scale by how “close to cap” this marble is for its rarity
                // quality: 0.0 .. 1.0
                double quality = (r.getTotalCap() <= 0) ? 0.0 : Math.min(1.0, Math.max(0.0, total / (double) r.getTotalCap()));

                // Base range per rarity (tuneable)
                int min = switch (r) {
                    case COMMON -> 8;
                    case UNCOMMON -> 12;
                    case RARE -> 18;
                    case EPIC -> 25;
                    case LEGENDARY -> 35;
                };

                int max = switch (r) {
                    case COMMON -> 25;
                    case UNCOMMON -> 40;
                    case RARE -> 60;
                    case EPIC -> 85;
                    case LEGENDARY -> 120;
                };

                int perMarble = (int) Math.round(min + (max - min) * quality);

                // Safety clamp
                if (perMarble < 0) perMarble = 0;

                return perMarble * Math.max(1, item.getAmount());

            } catch (Exception ex) {
                loud("[Infusion] CatalystValue: failed to compute marble catalyst value (" +
                        ex.getClass().getSimpleName() + ": " + ex.getMessage() + "). Defaulting to 10.");
                return 10 * Math.max(1, item.getAmount());
            }
        }

        // Non-marble catalysts: material-based
        int per = VALUES.getOrDefault(item.getType(), 10); // default small value for “any item”
        return per * Math.max(1, item.getAmount());
    }

    private static void loud(String msg) {
        try {
            JavaPlugin.getProvidingPlugin(CatalystValue.class).getLogger().warning(msg);
        } catch (Exception ignored) {
            // last resort: do nothing (logger unavailable very early)
        }
    }
}
