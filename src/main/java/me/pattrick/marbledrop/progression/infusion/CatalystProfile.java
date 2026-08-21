package me.pattrick.marbledrop.progression.infusion;

import me.pattrick.marbledrop.Main;
import me.pattrick.marbledrop.MdConfig;
import me.pattrick.marbledrop.marble.MarbleItem;
import me.pattrick.marbledrop.marble.MarbleStat;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

/**
 * Everything a catalyst item (the item dropped in the Infusion Cauldron's
 * center slot) contributes to an infusion, computed once via {@link #of}.
 * Only crafting-material catalysts are supported - marbles cannot be used
 * as catalysts (see CatalystProfile#invalidReason and
 * InfusionTableListener's slot guard, which bounces marbles back to the
 * player before they can be placed).
 * <p>
 * rarityValue scales with the full stack placed in the catalyst slot (the
 * whole stack is consumed on a successful infusion - see
 * InfusionTableMenu#confirm), and is a soft bias, never a guarantee. It's
 * capped at {@link #MAX_RARITY_VALUE} - InfusionTableListener refuses to
 * let a stack worth more than the cap sit in the slot at all, so a player
 * can never spend more than the cap can actually use.
 */
public record CatalystProfile(
        int rarityValue,
        Map<MarbleStat, Double> statAffinity
) {

    public static final CatalystProfile EMPTY = new CatalystProfile(0, Map.of());

    /**
     * Hard ceiling on how much a single catalyst item can add to an
     * infusion's effective value. Independent of whatever config.yml sets
     * per-material - without this, a future high-value material (or a
     * misconfigured one) could single-handedly buy the best rarity band
     * regardless of dust actually spent. InfusionTableListener also uses
     * this to refuse placing a catalyst worth more than the cap, so
     * players don't waste an expensive item for no extra benefit.
     */
    public static final int MAX_RARITY_VALUE = 150;

    public static CatalystProfile of(ItemStack item) {
        if (item == null || item.getType().isAir()) return EMPTY;
        if (MarbleItem.isMarble(item)) return EMPTY;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(CatalystProfile.class);
        MdConfig cfg = (plugin instanceof Main m) ? m.cfg() : null;
        int amount = Math.max(1, item.getAmount());

        // Value scales with the whole stack - InfusionTableMenu#confirm
        // consumes the entire stack placed in the slot (not just 1 item),
        // so this has to match what actually gets spent.
        int perItem = cfg != null ? cfg.catalystMaterialValue(item.getType()) : 10;

        Map<MarbleStat, Double> affinity = new EnumMap<>(MarbleStat.class);
        if (cfg != null) {
            for (MarbleStat stat : cfg.catalystStatAffinity(item.getType())) {
                affinity.put(stat, 1.0);
            }
        }

        return new CatalystProfile(perItem * amount, affinity);
    }

    /**
     * Null if this item is fine to use as a catalyst; otherwise a
     * player-facing reason it was refused. Used by InfusionTableListener
     * to bounce invalid items out of the catalyst slot instead of letting
     * them sit there (and potentially get consumed) for no real benefit.
     */
    public static String invalidReason(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;

        if (MarbleItem.isMarble(item)) {
            return "Marbles can't be used as catalysts.";
        }

        int raw = of(item).rarityValue();
        if (raw > MAX_RARITY_VALUE) {
            return "That stack is worth " + raw + ", but the cap is "
                    + MAX_RARITY_VALUE + " - the extra value would be wasted. Use fewer items or a cheaper material.";
        }

        return null;
    }
}
