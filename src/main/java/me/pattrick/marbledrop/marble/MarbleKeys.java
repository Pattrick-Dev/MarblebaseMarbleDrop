package me.pattrick.marbledrop.marble;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class MarbleKeys {
    private MarbleKeys() {}

    public static NamespacedKey SCHEMA;
    public static NamespacedKey MARBLE_ID;
    public static NamespacedKey MARBLE_KEY;
    public static NamespacedKey TEAM_KEY;
    public static NamespacedKey RARITY;

    public static NamespacedKey SPEED;
    public static NamespacedKey ACCEL;
    public static NamespacedKey HANDLING;
    public static NamespacedKey STABILITY;
    public static NamespacedKey BOOST;

    public static NamespacedKey FOUND_BY;
    public static NamespacedKey CREATED_AT;

    public static NamespacedKey XP;
    public static NamespacedKey LEVEL;

    public static void init(JavaPlugin plugin) {
        SCHEMA = new NamespacedKey(plugin, "schema");

        MARBLE_ID = new NamespacedKey(plugin, "marble_id");
        MARBLE_KEY = new NamespacedKey(plugin, "marble_key");
        TEAM_KEY = new NamespacedKey(plugin, "team_key");
        RARITY = new NamespacedKey(plugin, "rarity");

        SPEED = new NamespacedKey(plugin, "stat_speed");
        ACCEL = new NamespacedKey(plugin, "stat_accel");
        HANDLING = new NamespacedKey(plugin, "stat_handling");
        STABILITY = new NamespacedKey(plugin, "stat_stability");
        BOOST = new NamespacedKey(plugin, "stat_boost");

        FOUND_BY = new NamespacedKey(plugin, "found_by");
        CREATED_AT = new NamespacedKey(plugin, "created_at");

        XP = new NamespacedKey(plugin, "xp");
        LEVEL = new NamespacedKey(plugin, "level");
    }
}
