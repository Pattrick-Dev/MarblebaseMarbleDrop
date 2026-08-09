package me.pattrick.marbledrop.progression.infusion;

import me.pattrick.marbledrop.Main;
import me.pattrick.marbledrop.MdConfig;
import me.pattrick.marbledrop.marble.MarbleData;
import me.pattrick.marbledrop.marble.MarbleItem;
import me.pattrick.marbledrop.marble.MarbleRarity;
import me.pattrick.marbledrop.marble.MarbleStat;
import me.pattrick.marbledrop.marble.MarbleStats;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Everything a catalyst item (the item dropped in the Infusion Cauldron's
 * center slot) contributes to an infusion, computed once via {@link #of}.
 * Replaces the old flat-int-only CatalystValue -- on top of the existing
 * rarityValue/rarityBias mechanic, a catalyst can now also soft-skew which
 * stats roll higher (statAffinity) and which team the marble is more
 * likely to come from (teamBias). All of these are soft biases (extra
 * rerolls / weighted odds), never guarantees -- see InfusionService,
 * StatRoller#rollStats(rarity, statAffinity), and Sampler#main(preferredTeam).
 */
public record CatalystProfile(
        int rarityValue,
        MarbleRarity rarityBias,
        Map<MarbleStat, Double> statAffinity,
        String teamBias
) {

    public static final CatalystProfile EMPTY = new CatalystProfile(0, null, Map.of(), null);

    public static CatalystProfile of(ItemStack item) {
        if (item == null || item.getType().isAir()) return EMPTY;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(CatalystProfile.class);
        MdConfig cfg = (plugin instanceof Main m) ? m.cfg() : null;
        int amount = Math.max(1, item.getAmount());

        if (MarbleItem.isMarble(item)) {
            MarbleData data = MarbleItem.read(item);
            if (data == null) {
                int fallback = (cfg != null ? cfg.catalystDefaultPerItem() : 10) * amount;
                return new CatalystProfile(fallback, null, Map.of(), null);
            }

            MarbleRarity rarity = data.getRarity() == null ? MarbleRarity.COMMON : data.getRarity();
            MdConfig.Range range = cfg != null ? cfg.marbleCatalystRange(rarity) : new MdConfig.Range(10, 1000);
            boolean statBased = cfg == null || cfg.catalystMarbleStatBased();

            MarbleStats stats = data.getStats();
            int statTotal = stats == null ? 0 : stats.total();

            int perItem;
            if (statBased) {
                double fraction = Math.min(1.0, Math.max(0.0, statTotal / 500.0));
                perItem = (int) Math.round(range.min() + fraction * (range.max() - range.min()));
            } else {
                perItem = (range.min() + range.max()) / 2;
            }

            Map<MarbleStat, Double> affinity = new EnumMap<>(MarbleStat.class);
            if (stats != null) {
                affinity.putAll(rankedAffinity(stats));
            }

            // Naming trap: getTeamKey() holds the raw human-readable team
            // string (e.g. "Mellow Yellow") that matches heads.yml's
            // `team:` field -- getMarbleKey() holds the sanitized key
            // instead. See InfusionService#infuseToItem's MarbleData
            // construction for where this naming originates.
            String teamBias = data.getTeamKey();

            return new CatalystProfile(perItem * amount, rarity, affinity, teamBias);
        }

        int perItem = cfg != null ? cfg.catalystMaterialValue(item.getType()) : 10;

        Map<MarbleStat, Double> affinity = new EnumMap<>(MarbleStat.class);
        if (cfg != null) {
            for (MarbleStat stat : cfg.catalystStatAffinity(item.getType())) {
                affinity.put(stat, 1.0);
            }
        }

        return new CatalystProfile(perItem * amount, null, affinity, null);
    }

    /**
     * Ranks a marble catalyst's 5 stats highest-to-lowest and boosts only
     * the top 3 -- its best stat gets weight 1.0 (strongest reroll-take-
     * best chance in StatRoller), 2nd/3rd taper down from there, and its
     * bottom 2 stats get 0.0 (roll plain, unbiased). Ties keep MarbleStat's
     * declared enum order (SPEED, ACCEL, HANDLING, STABILITY, BOOST) since
     * Arrays.sort is stable -- deterministic rather than proportional-to-
     * magnitude, so a catalyst's strongest stats are always favored
     * regardless of how close together its other stats happen to be.
     */
    private static Map<MarbleStat, Double> rankedAffinity(MarbleStats stats) {
        MarbleStat[] byRank = MarbleStat.values().clone();
        Arrays.sort(byRank, (a, b) -> Integer.compare(stats.get(b), stats.get(a)));

        final int boosted = 3;
        Map<MarbleStat, Double> affinity = new EnumMap<>(MarbleStat.class);
        for (int rank = 0; rank < byRank.length; rank++) {
            double weight = (rank < boosted) ? (boosted - rank) / (double) boosted : 0.0;
            affinity.put(byRank[rank], weight);
        }
        return affinity;
    }
}
