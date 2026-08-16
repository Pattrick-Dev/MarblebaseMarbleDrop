package me.pattrick.marbledrop;

import me.pattrick.marbledrop.marble.MarbleRarity;
import me.pattrick.marbledrop.marble.MarbleStat;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MdConfig {

    public record Range(int min, int max) {}

    private final JavaPlugin plugin;

    // ---- cached values ----
    private boolean debugEnabled;

    private String discordWebhookUrl;
    private int discordUpdateIntervalSeconds;

    private double holoNameRadius;
    private String infusionHoloName;
    private double infusionHoloYOffset;
    private String recyclerHoloName;
    private double recyclerHoloYOffset;
    private String upgradeHoloName;
    private double upgradeHoloYOffset;

    private int infusionDailyCap;

    private int animTotalTicks;
    private int animHoldTicks;
    private int animRevealEarlyTicks;
    private double animBobAmplitude;
    private double animBobSpeed;
    private double animStartingTurns;

    private double raceWallSearchRadius;

    private boolean scheduledRaceEnabled;
    private List<Integer> scheduledRaceAnnounceMinutesBefore;
    private int scheduledRaceWinnerDust;
    private int scheduledRaceWinnerDustVsAi;
    private List<Integer> scheduledRacePlacePercentages;
    private int scheduledRaceAiFillCount;
    private int scheduledRaceAiShowCount;
    private List<LocalTime> scheduledRaceDailyTimes = List.of();

    private boolean catalystMarbleStatBased;
    private int catalystDefaultPerItem;
    private double catalystTeamBiasChance;
    private final EnumMap<Material, Integer> catalystMaterialValues = new EnumMap<>(Material.class);
    private final EnumMap<MarbleRarity, Range> marbleValueRange = new EnumMap<>(MarbleRarity.class);
    private final EnumMap<Material, EnumSet<MarbleStat>> catalystStatAffinity = new EnumMap<>(Material.class);

    public MdConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        debugEnabled = c.getBoolean("debug.enabled", false);

        discordWebhookUrl = c.getString("discord.webhook-url", "");
        discordUpdateIntervalSeconds = c.getInt("discord.update-interval-seconds", 20);

        holoNameRadius = c.getDouble("holograms.name-visible-radius", 8.0);

        infusionHoloName = color(c.getString("holograms.infusion.name", "&5✦ &dInfusion Cauldron &5✦"));
        infusionHoloYOffset = c.getDouble("holograms.infusion.y-offset", 1.25);

        recyclerHoloName = color(c.getString("holograms.recycler.name", "&8✦ &6Marble Recycler &8✦"));
        recyclerHoloYOffset = c.getDouble("holograms.recycler.y-offset", 1.15);

        upgradeHoloName = color(c.getString("holograms.upgrade.name", "&b✦ &dUpgrade Station &b✦"));
        upgradeHoloYOffset = c.getDouble("holograms.upgrade.y-offset", 1.2);

        raceWallSearchRadius = c.getDouble("races.wall-search-radius", 2.5);

        scheduledRaceEnabled = c.getBoolean("races.scheduled.enabled", true);
        scheduledRaceAnnounceMinutesBefore = c.getIntegerList("races.scheduled.announce-minutes-before");
        if (scheduledRaceAnnounceMinutesBefore.isEmpty()) {
            scheduledRaceAnnounceMinutesBefore = List.of(10, 5, 1);
        }
        scheduledRaceWinnerDust = c.getInt("races.scheduled.winner-dust", 50);
        scheduledRaceWinnerDustVsAi = c.getInt("races.scheduled.winner-dust-vs-ai", 15);
        scheduledRacePlacePercentages = c.getIntegerList("races.scheduled.place-dust-percentages");
        if (scheduledRacePlacePercentages.isEmpty()) {
            scheduledRacePlacePercentages = List.of(100, 50, 25);
        }
        scheduledRaceAiFillCount = c.getInt("races.scheduled.ai-fill-count", 3);
        scheduledRaceAiShowCount = c.getInt("races.scheduled.ai-show-count", 4);

        List<LocalTime> dailyTimes = new ArrayList<>();
        for (String raw : c.getStringList("races.scheduled.daily-times")) {
            try {
                dailyTimes.add(LocalTime.parse(raw.trim()));
            } catch (DateTimeParseException ex) {
                plugin.getLogger().warning("[Config] Unparsable time in races.scheduled.daily-times: '" + raw + "' (expected HH:mm)");
            }
        }
        scheduledRaceDailyTimes = dailyTimes;

        infusionDailyCap = c.getInt("infusion.daily-cap", 5);

        animTotalTicks = c.getInt("infusion.animation.total-ticks", 120);
        animHoldTicks = c.getInt("infusion.animation.hold-ticks", 20);
        animRevealEarlyTicks = c.getInt("infusion.animation.reveal-early-ticks", 10);

        animBobAmplitude = c.getDouble("infusion.animation.bob.amplitude", 0.10);
        animBobSpeed = c.getDouble("infusion.animation.bob.speed", 0.22);
        animStartingTurns = c.getDouble("infusion.animation.spin.starting-turns", 7.0);

        catalystMarbleStatBased = c.getBoolean("infusion.catalyst.marble-stat-based", true);
        catalystDefaultPerItem = c.getInt("infusion.catalyst.default-per-item", 10);
        catalystTeamBiasChance = c.getDouble("infusion.catalyst.team-bias-chance", 0.5);

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

        // stat affinity (which stats a material catalyst biases upward)
        catalystStatAffinity.clear();
        ConfigurationSection affinitySec = c.getConfigurationSection("infusion.catalyst.stat-affinity");
        if (affinitySec != null) {
            for (String key : affinitySec.getKeys(false)) {
                String matName = key.toUpperCase(Locale.ROOT);
                Material mat = Material.matchMaterial(matName);
                if (mat == null) {
                    plugin.getLogger().warning("[Config] Unknown material in infusion.catalyst.stat-affinity: " + key);
                    continue;
                }

                EnumSet<MarbleStat> stats = EnumSet.noneOf(MarbleStat.class);
                for (String statName : affinitySec.getStringList(key)) {
                    try {
                        stats.add(MarbleStat.valueOf(statName.trim().toUpperCase(Locale.ROOT)));
                    } catch (Exception ignored) {
                        plugin.getLogger().warning("[Config] Unknown stat '" + statName + "' in infusion.catalyst.stat-affinity." + key);
                    }
                }
                if (!stats.isEmpty()) {
                    catalystStatAffinity.put(mat, stats);
                }
            }
        }
    }

    // ---- getters ----
    public boolean debugEnabled() { return debugEnabled; }

    public String discordWebhookUrl() { return discordWebhookUrl; }
    public int discordUpdateIntervalSeconds() { return discordUpdateIntervalSeconds; }

    public double raceWallSearchRadius() { return raceWallSearchRadius; }

    public boolean scheduledRaceEnabled() { return scheduledRaceEnabled; }
    public List<Integer> scheduledRaceAnnounceMinutesBefore() { return scheduledRaceAnnounceMinutesBefore; }
    public int scheduledRaceWinnerDust() { return scheduledRaceWinnerDust; }
    public int scheduledRaceWinnerDustVsAi() { return scheduledRaceWinnerDustVsAi; }

    /**
     * Dust for a given 0-indexed finish place, tapered off from baseDust by
     * races.scheduled.place-dust-percentages (e.g. [100, 50, 25] pays 1st
     * place in full, 2nd half, 3rd a quarter). Places beyond the configured
     * list earn nothing - this is what stops one player snowballing further
     * ahead every race just by always placing 1st.
     */
    public int scheduledRaceDustForPlace(int baseDust, int placeIndex) {
        if (placeIndex < 0 || placeIndex >= scheduledRacePlacePercentages.size()) return 0;
        int percent = scheduledRacePlacePercentages.get(placeIndex);
        return Math.round(baseDust * (percent / 100f));
    }
    public int scheduledRaceAiFillCount() { return scheduledRaceAiFillCount; }
    public int scheduledRaceAiShowCount() { return scheduledRaceAiShowCount; }
    public List<LocalTime> scheduledRaceDailyTimes() { return scheduledRaceDailyTimes; }

    public double hologramNameRadius() { return holoNameRadius; }
    public String infusionHologramName() { return infusionHoloName; }
    public double infusionHologramYOffset() { return infusionHoloYOffset; }
    public String recyclerHologramName() { return recyclerHoloName; }
    public double recyclerHologramYOffset() { return recyclerHoloYOffset; }
    public String upgradeHologramName() { return upgradeHoloName; }
    public double upgradeHologramYOffset() { return upgradeHoloYOffset; }

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

    public double catalystTeamBiasChance() { return catalystTeamBiasChance; }

    /** Which stats a material catalyst biases upward. Empty (never null) if the material has no configured affinity. */
    public EnumSet<MarbleStat> catalystStatAffinity(Material mat) {
        if (mat == null) return EnumSet.noneOf(MarbleStat.class);
        EnumSet<MarbleStat> stats = catalystStatAffinity.get(mat);
        return stats == null ? EnumSet.noneOf(MarbleStat.class) : EnumSet.copyOf(stats);
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
