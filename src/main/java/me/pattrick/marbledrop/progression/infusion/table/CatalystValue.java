package me.pattrick.marbledrop.progression.infusion.table;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class CatalystValue {

    // value per item
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

    public static int valueOf(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;

        int per = VALUES.getOrDefault(item.getType(), 10); // default small value for “any item”
        return per * Math.max(1, item.getAmount());
    }
}
