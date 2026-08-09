package me.pattrick.marbledrop.progression;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Builds/reads the PDC-tagged ItemStacks that identify a crafted station item. */
public final class StationItems {

    private StationItems() {}

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "station_type");
    }

    public static ItemStack create(Plugin plugin, StationType type) {
        ItemStack item = new ItemStack(type.blockType());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.displayName());
        meta.setLore(type.lore());
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    /** Null if the item isn't a tagged station item (e.g. a plain vanilla block of the same material). */
    public static StationType readType(Plugin plugin, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;

        ItemMeta meta = stack.getItemMeta();
        String name = meta.getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
        if (name == null) return null;

        try {
            return StationType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
