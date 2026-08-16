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

    // Block-state only - who placed this marble as a display, so only
    // they (or an admin) can break it back. Not part of MarbleData/the
    // item's own PDC; only ever read/written directly on a placed
    // skull's PersistentDataContainer - see MarblePlacementListener.
    public static NamespacedKey PLACED_BY;

    // Block-state only - a snapshot of the marble item's display name at
    // the moment it was placed. MarbleItem#write() has never touched
    // display name (only PDC + lore); it's set once wherever a marble is
    // first minted and normally just rides along on the ItemStack
    // forever. Placing as a block loses it like every other un-PDC'd
    // ItemMeta field, so it has to be captured and restored explicitly.
    public static NamespacedKey PLACED_NAME;

    // Block-state only - per-placement display toggles (byte, 1/0),
    // defaulting to enabled when absent. See MarbleDisplayMenu/
    // MarbleDisplayAmbient.
    public static NamespacedKey SHOW_HOLOGRAM;
    public static NamespacedKey SHOW_PARTICLES;

    // Block-state only - whether the hologram's " (RARITY)" suffix is
    // shown, independent of SHOW_HOLOGRAM itself. See MarbleDisplayMenu/
    // MarbleDisplayAmbient#holoName.
    public static NamespacedKey SHOW_RARITY;

    // Block-state only - which MarbleParticleStyle (by enum name) a
    // placed marble shows when SHOW_PARTICLES is on. Absent/unrecognized
    // falls back to MarbleParticleStyle.RARITY_DUST.
    public static NamespacedKey PARTICLE_STYLE;

    public static void init(JavaPlugin plugin) {
        SCHEMA = new NamespacedKey(plugin, "schema");

        MARBLE_ID = new NamespacedKey(plugin, "marble_id");
        MARBLE_KEY = new NamespacedKey(plugin, "marble_key");

        // ✅ Single source of truth (matches the rest of your project)
        TEAM_KEY = new NamespacedKey(plugin, "marble_team");

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

        PLACED_BY = new NamespacedKey(plugin, "placed_by");
        PLACED_NAME = new NamespacedKey(plugin, "placed_name");

        SHOW_HOLOGRAM = new NamespacedKey(plugin, "placed_show_hologram");
        SHOW_PARTICLES = new NamespacedKey(plugin, "placed_show_particles");
        SHOW_RARITY = new NamespacedKey(plugin, "placed_show_rarity");
        PARTICLE_STYLE = new NamespacedKey(plugin, "placed_particle_style");
    }
}
