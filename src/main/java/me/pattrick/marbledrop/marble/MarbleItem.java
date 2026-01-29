package me.pattrick.marbledrop.marble;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MarbleItem {
    private MarbleItem() {}

    public static boolean isMarble(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(MarbleKeys.MARBLE_ID, PersistentDataType.STRING)
                && pdc.has(MarbleKeys.MARBLE_KEY, PersistentDataType.STRING)
                && pdc.has(MarbleKeys.RARITY, PersistentDataType.STRING);
    }

    public static MarbleData read(ItemStack item) {
        if (!isMarble(item)) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        int schema = getInt(pdc, MarbleKeys.SCHEMA, MarbleData.SCHEMA_VERSION);

        // If later you bump schema, you’ll migrate here based on `schema`.
        UUID id = UUID.fromString(pdc.get(MarbleKeys.MARBLE_ID, PersistentDataType.STRING));

        String marbleKey = pdc.get(MarbleKeys.MARBLE_KEY, PersistentDataType.STRING);
        String teamKey = pdc.getOrDefault(MarbleKeys.TEAM_KEY, PersistentDataType.STRING, "");

        MarbleRarity rarity = MarbleRarity.valueOf(
                pdc.get(MarbleKeys.RARITY, PersistentDataType.STRING)
        );

        int speed = getInt(pdc, MarbleKeys.SPEED, 0);
        int accel = getInt(pdc, MarbleKeys.ACCEL, 0);
        int handling = getInt(pdc, MarbleKeys.HANDLING, 0);
        int stability = getInt(pdc, MarbleKeys.STABILITY, 0);
        int boost = getInt(pdc, MarbleKeys.BOOST, 0);

        MarbleStats stats = new MarbleStats(speed, accel, handling, stability, boost);

        String foundByStr = pdc.getOrDefault(MarbleKeys.FOUND_BY, PersistentDataType.STRING, "");
        UUID foundBy = foundByStr.isEmpty() ? null : UUID.fromString(foundByStr);

        long createdAt = getLong(pdc, MarbleKeys.CREATED_AT, 0L);

        int xp = getInt(pdc, MarbleKeys.XP, 0);
        int level = getInt(pdc, MarbleKeys.LEVEL, 1);

        return new MarbleData(id, marbleKey, teamKey, rarity, stats, foundBy, createdAt, xp, level);
    }

    /**
     * Writes modern marble PDC and ALSO keeps lore in sync with stats.
     * This prevents “upgrade works but lore doesn't change”.
     */
    public static void write(ItemStack item, MarbleData data) {
        if (item == null) return;
        if (data == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        pdc.set(MarbleKeys.SCHEMA, PersistentDataType.INTEGER, MarbleData.SCHEMA_VERSION);

        pdc.set(MarbleKeys.MARBLE_ID, PersistentDataType.STRING, data.getId().toString());
        pdc.set(MarbleKeys.MARBLE_KEY, PersistentDataType.STRING, data.getMarbleKey());
        pdc.set(MarbleKeys.TEAM_KEY, PersistentDataType.STRING, data.getTeamKey());
        pdc.set(MarbleKeys.RARITY, PersistentDataType.STRING, data.getRarity().name());

        pdc.set(MarbleKeys.SPEED, PersistentDataType.INTEGER, data.getStats().get(MarbleStat.SPEED));
        pdc.set(MarbleKeys.ACCEL, PersistentDataType.INTEGER, data.getStats().get(MarbleStat.ACCEL));
        pdc.set(MarbleKeys.HANDLING, PersistentDataType.INTEGER, data.getStats().get(MarbleStat.HANDLING));
        pdc.set(MarbleKeys.STABILITY, PersistentDataType.INTEGER, data.getStats().get(MarbleStat.STABILITY));
        pdc.set(MarbleKeys.BOOST, PersistentDataType.INTEGER, data.getStats().get(MarbleStat.BOOST));

        if (data.getFoundBy() != null) {
            pdc.set(MarbleKeys.FOUND_BY, PersistentDataType.STRING, data.getFoundBy().toString());
        }

        pdc.set(MarbleKeys.CREATED_AT, PersistentDataType.LONG, data.getCreatedAt());
        pdc.set(MarbleKeys.XP, PersistentDataType.INTEGER, data.getXp());
        pdc.set(MarbleKeys.LEVEL, PersistentDataType.INTEGER, data.getLevel());

        // ✅ Keep lore synced to the real stats
        syncLore(meta, data);

        item.setItemMeta(meta);
    }

    private static void syncLore(ItemMeta meta, MarbleData data) {
        if (meta == null || data == null) return;

        String team = data.getTeamKey();
        if (team == null || team.trim().isEmpty()) team = "Neutral";

        MarbleRarity rarity = data.getRarity();
        if (rarity == null) rarity = MarbleRarity.COMMON;

        int speed = data.getStats().get(MarbleStat.SPEED);
        int accel = data.getStats().get(MarbleStat.ACCEL);
        int handling = data.getStats().get(MarbleStat.HANDLING);
        int stability = data.getStats().get(MarbleStat.STABILITY);
        int boost = data.getStats().get(MarbleStat.BOOST);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Team: " + ChatColor.WHITE + team);
        lore.add(ChatColor.GRAY + "Rarity: " + rarityColor(rarity) + rarity.name());
        lore.add("");
        lore.add(ChatColor.GRAY + "Speed: " + ChatColor.WHITE + speed);
        lore.add(ChatColor.GRAY + "Accel: " + ChatColor.WHITE + accel);
        lore.add(ChatColor.GRAY + "Handling: " + ChatColor.WHITE + handling);
        lore.add(ChatColor.GRAY + "Stability: " + ChatColor.WHITE + stability);
        lore.add(ChatColor.GRAY + "Boost: " + ChatColor.WHITE + boost);

        meta.setLore(lore);
    }

    private static String rarityColor(MarbleRarity r) {
        if (r == null) return ChatColor.WHITE.toString();
        return switch (r) {
            case COMMON -> ChatColor.WHITE.toString();
            case UNCOMMON -> ChatColor.GREEN.toString();
            case RARE -> ChatColor.AQUA.toString();
            case EPIC -> ChatColor.LIGHT_PURPLE.toString();
            case LEGENDARY -> ChatColor.GOLD.toString();
        };
    }

    private static int getInt(PersistentDataContainer pdc, org.bukkit.NamespacedKey key, int def) {
        Integer v = pdc.get(key, PersistentDataType.INTEGER);
        return (v != null) ? v : def;
    }

    private static long getLong(PersistentDataContainer pdc, org.bukkit.NamespacedKey key, long def) {
        Long v = pdc.get(key, PersistentDataType.LONG);
        return (v != null) ? v : def;
    }
}
