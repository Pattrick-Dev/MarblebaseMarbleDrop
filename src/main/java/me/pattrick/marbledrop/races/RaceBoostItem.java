package me.pattrick.marbledrop.races;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Builds/reads the PDC-tagged ItemStack handed to a real racer for the
 * duration of their race (see RaceBoostListener). Stack amount doubles as
 * the visible "charges remaining" count -- each successful boost consumes
 * one, so the player sees their remaining uses the same way they'd see
 * arrows or snowballs deplete, with no separate lore to keep in sync.
 */
public final class RaceBoostItem {

    private RaceBoostItem() {}

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "race_boost_item");
    }

    public static ItemStack create(Plugin plugin, int charges) {
        ItemStack item = new ItemStack(Material.FIREWORK_STAR, Math.max(1, charges));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Boost");
        meta.setLore(List.of(
                ChatColor.GRAY + "Right-click to fire a speed burst!",
                ChatColor.DARK_GRAY + "Consumes one charge -- time it well."
        ));
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isBoostItem(Plugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }
}
