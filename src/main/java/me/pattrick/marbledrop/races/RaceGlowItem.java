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
 * duration of their race (see RaceGlowListener). A plain on/off toggle,
 * not a limited resource like RaceBoostItem - right-click flips whether
 * the player's own marble renders with a glowing outline (visible even
 * through terrain and other marbles). Purely about being able to actually
 * spot your own marble in a crowded field, not a competitive lever.
 */
public final class RaceGlowItem {

    private RaceGlowItem() {}

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "race_glow_item");
    }

    public static ItemStack create(Plugin plugin) {
        return create(plugin, false);
    }

    /** Builds a fresh item already reflecting {@code glowing} - used by the overlay path, which shows a new fake item each toggle rather than mutating a real one. */
    public static ItemStack create(Plugin plugin, boolean glowing) {
        ItemStack item = new ItemStack(Material.GLOW_INK_SAC, 1);
        applyMeta(plugin, item, glowing);
        return item;
    }

    public static boolean isGlowItem(Plugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    /** Updates the held item's name/lore to reflect the new state - called by the listener right after a successful toggle. */
    public static void updateState(Plugin plugin, ItemStack item, boolean glowing) {
        applyMeta(plugin, item, glowing);
    }

    private static void applyMeta(Plugin plugin, ItemStack item, boolean glowing) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName((glowing ? ChatColor.GREEN : ChatColor.AQUA) + "" + ChatColor.BOLD + "Glow: " + (glowing ? "ON" : "OFF"));
        meta.setLore(List.of(
                ChatColor.GRAY + "Right-click to toggle a glowing outline",
                ChatColor.GRAY + "on your own marble.",
                ChatColor.DARK_GRAY + "Handy for spotting yourself in a crowd."
        ));
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }
}
