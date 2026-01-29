package me.pattrick.marbledrop;

import me.pattrick.marbledrop.marble.MarbleRarity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class MdConfig {

    public record Range(int min, int max) {}

    private final JavaPlugin plugin;

    // ---- cached values ----
    private boolean debugEnabled;

    private double holoNameRadius;
    private String infusionHoloName;
    private double infusionHoloYOffset;
    private String recyclerHoloName;
    private double recyclerHoloYOffset;

    private int infusionDailyCap;

    private int animTotalTicks;
    private int animHoldTicks;
    private int animRevealEarlyTicks;
    private double animBobAmplitude;
    private double animBobSpeed;
    private double animStartingTurns;

    private boolean catalystMarbleStatBased;
    private int catalystDefaultPerItem;
    private final EnumMap<Material, Integer> catalystMaterialValues = new EnumMap<>(Material.class);
    private final EnumMap<MarbleRarity, Range> marbleValueRange = new EnumMap<>(MarbleRarity.class);

    public MdConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        debugEnabled = c.getBoolean("debug.enabled", false);

        holoNameRadius = c.getDouble("holograms.name-visible-radius", 8.0);

        infusionHoloName = color(c.getString("holograms.infusion.name", "&5✦ &dInfusion Cauldron &5✦"));
        infusionHoloYOffset = c.getDouble("holograms.infusion.y-offset", 1.25);

        recyclerHoloName = color(c.getString("holograms.recycler.name", "&8✦ &6Marble Recycler &8✦"));
        recyclerHoloYOffset = c.getDouble("holograms.recycler.y-offset", 1.15);

        infusionDailyCap = c.getInt("infusion.daily-cap", 5);

        animTotalTicks = c.getInt("infusion.animation.total-ticks", 120);
        animHoldTicks = c.getInt("infusion.animation.hold-ticks", 20);
        animRevealEarlyTicks = c.getInt("infusion.animation.reveal-early-ticks", 10);

        animBobAmplitude = c.getDouble("infusion.animation.bob.amplitude", 0.10);
        animBobSpeed = c.getDouble("infusion.animation.bob.speed", 0.22);
        animStartingTurns = c.getDouble("infusion.animation.spin.starting-turns", 7.0);

        catalystMarbleStatBased = c.getBoolean("infusion.catalyst.marble-stat-based", true);
        catalystDefaultPerItem = c.getInt("infusion.catalyst.default-per-item", 10);

        // material values
        catalystMaterialValues.clear();
        ConfigurationSection valuesSec = c.getConfigurationSection("infusion.catalyst.values");
        if (valuesSec != null) {
            for (String key : valuesSec.getKeys(false)) {
                String matName = key.toUpperCase(Locale.ROOT);
                Material mat = Material.matchMaterial(matName);
                if (mat == null) {
                    plugin.getLogger().warning("[Config] Unknown material in infusion.catalyst.values: " + key);
                    continue;
                }
                int v = valuesSec.getInt(key, catalystDefaultPerItem);
                catalystMaterialValues.put(mat, v);
            }
        }

        // rarity ranges
        marbleValueRange.clear();
        ConfigurationSection rangeSec = c.getConfigurationSection("infusion.catalyst.marble-value-range");
        if (rangeSec != null) {
            for (String key : rangeSec.getKeys(false)) {
                MarbleRarity rarity = parseRarity(key);
                if (rarity == null) {
                    plugin.getLogger().warning("[Config] Unknown rarity in infusion.catalyst.marble-value-range: " + key);
                    continue;
                }
                ConfigurationSection r = rangeSec.getConfigurationSection(key);
                if (r == null) continue;

                int min = r.getInt("min", defaultMin(rarity));
                int max = r.getInt("max", defaultMax(rarity));
                if (max < min) {
                    plugin.getLogger().warning("[Config] marble-value-range " + key + " has max < min. Swapping.");
                    int t = min; min = max; max = t;
                }
                marbleValueRange.put(rarity, new Range(min, max));
            }
        }

        // ensure defaults exist even if config omits them
        for (MarbleRarity r : MarbleRarity.values()) {
            marbleValueRange.putIfAbsent(r, new Range(defaultMin(r), defaultMax(r)));
        }
    }

    // ---- getters ----
    public boolean debugEnabled() { return debugEnabled; }

    public double hologramNameRadius() { return holoNameRadius; }
    public String infusionHologramName() { return infusionHoloName; }
    public double infusionHologramYOffset() { return infusionHoloYOffset; }
    public String recyclerHologramName() { return recyclerHoloName; }
    public double recyclerHologramYOffset() { return recyclerHoloYOffset; }

    public int infusionDailyCap() { return infusionDailyCap; }

    public int infusionAnimTotalTicks() { return animTotalTicks; }
    public int infusionAnimHoldTicks() { return animHoldTicks; }
    public int infusionAnimRevealEarlyTicks() { return animRevealEarlyTicks; }
    public double infusionAnimBobAmplitude() { return animBobAmplitude; }
    public double infusionAnimBobSpeed() { return animBobSpeed; }
    public double infusionAnimStartingTurns() { return animStartingTurns; }

    public boolean catalystMarbleStatBased() { return catalystMarbleStatBased; }
    public int catalystDefaultPerItem() { return catalystDefaultPerItem; }

    public int catalystMaterialValue(Material mat) {
        if (mat == null) return 0;
        return catalystMaterialValues.getOrDefault(mat, catalystDefaultPerItem);
    }

    public Range marbleCatalystRange(MarbleRarity rarity) {
        if (rarity == null) rarity = MarbleRarity.COMMON;
        return marbleValueRange.getOrDefault(rarity, new Range(defaultMin(rarity), defaultMax(rarity)));
    }

    // ---- helpers ----
    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private MarbleRarity parseRarity(String s) {
        if (s == null) return null;
        try {
            return MarbleRarity.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private int defaultMin(MarbleRarity r) {
        return switch (r) {
            case COMMON -> 8;
            case UNCOMMON -> 12;
            case RARE -> 18;
            case EPIC -> 25;
            case LEGENDARY -> 35;
        };
    }

    private int defaultMax(MarbleRarity r) {
        return switch (r) {
            case COMMON -> 25;
            case UNCOMMON -> 40;
            case RARE -> 60;
            case EPIC -> 85;
            case LEGENDARY -> 120;
        };
    }
}
