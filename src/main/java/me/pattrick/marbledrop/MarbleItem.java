package me.pattrick.marbledrop;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class MarbleItem {

    // Keys (stored on the item)
    private static NamespacedKey K_IS_MARBLE;
    private static NamespacedKey K_ID;
    private static NamespacedKey K_TEAM;
    private static NamespacedKey K_RARITY;

    private static NamespacedKey K_SPEED;
    private static NamespacedKey K_CONTROL;
    private static NamespacedKey K_MOMENTUM;
    private static NamespacedKey K_STABILITY;
    private static NamespacedKey K_LUCK;

    public static void init(Plugin plugin) {
        K_IS_MARBLE = new NamespacedKey(plugin, "is_marble");
        K_ID = new NamespacedKey(plugin, "marble_id");
        K_TEAM = new NamespacedKey(plugin, "marble_team");
        K_RARITY = new NamespacedKey(plugin, "marble_rarity");

        K_SPEED = new NamespacedKey(plugin, "stat_speed");
        K_CONTROL = new NamespacedKey(plugin, "stat_control");
        K_MOMENTUM = new NamespacedKey(plugin, "stat_momentum");
        K_STABILITY = new NamespacedKey(plugin, "stat_stability");
        K_LUCK = new NamespacedKey(plugin, "stat_luck");
    }

    public static boolean isMarble(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte flag = pdc.get(K_IS_MARBLE, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    public static void applyToMeta(ItemMeta meta, Marble marble) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        pdc.set(K_IS_MARBLE, PersistentDataType.BYTE, (byte) 1);

        // stable ID for the item
        String id = marble.id() != null ? marble.id() : UUID.randomUUID().toString();
        pdc.set(K_ID, PersistentDataType.STRING, id);

        pdc.set(K_TEAM, PersistentDataType.STRING, marble.team());
        pdc.set(K_RARITY, PersistentDataType.STRING, marble.rarity().name());

        pdc.set(K_SPEED, PersistentDataType.INTEGER, marble.stats().speed());
        pdc.set(K_CONTROL, PersistentDataType.INTEGER, marble.stats().control());
        pdc.set(K_MOMENTUM, PersistentDataType.INTEGER, marble.stats().momentum());
        pdc.set(K_STABILITY, PersistentDataType.INTEGER, marble.stats().stability());
        pdc.set(K_LUCK, PersistentDataType.INTEGER, marble.stats().luck());
    }

    public static Marble read(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte flag = pdc.get(K_IS_MARBLE, PersistentDataType.BYTE);
        if (flag == null || flag != (byte) 1) return null;

        String id = pdc.get(K_ID, PersistentDataType.STRING);
        String team = pdc.get(K_TEAM, PersistentDataType.STRING);
        String rarityStr = pdc.get(K_RARITY, PersistentDataType.STRING);

        Integer speed = pdc.get(K_SPEED, PersistentDataType.INTEGER);
        Integer control = pdc.get(K_CONTROL, PersistentDataType.INTEGER);
        Integer momentum = pdc.get(K_MOMENTUM, PersistentDataType.INTEGER);
        Integer stability = pdc.get(K_STABILITY, PersistentDataType.INTEGER);
        Integer luck = pdc.get(K_LUCK, PersistentDataType.INTEGER);

        if (team == null || rarityStr == null || speed == null || control == null || momentum == null || stability == null || luck == null) {
            return null; // malformed / partially missing
        }

        MarbleRarity rarity;
        try {
            rarity = MarbleRarity.valueOf(rarityStr);
        } catch (Exception ex) {
            rarity = MarbleRarity.COMMON;
        }

        MarbleStats stats = new MarbleStats(speed, control, momentum, stability, luck);

        // displayName is not stored here; you already set it on the meta.
        String displayName = meta.getDisplayName();

        return new Marble(id, displayName, team, rarity, stats);
    }
}
