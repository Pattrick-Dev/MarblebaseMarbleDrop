package me.pattrick.marbledrop.tutorial;

import me.pattrick.marbledrop.marble.MarbleData;
import me.pattrick.marbledrop.marble.MarbleItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Small read-only helpers for scanning a player's inventory for marbles.
 * Used by the tutorial's completion pollers, task handler, and race
 * service. Not a general-purpose utility outside the tutorial package.
 */
final class TutorialMarbleUtil {

    private TutorialMarbleUtil() {}

    static int countMarbles(Player player) {
        int count = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && MarbleItem.isMarble(it)) count += it.getAmount();
        }
        return count;
    }

    /** First marble found, checking main hand first, then the rest of the inventory. Null if none. */
    static ItemStack findFirstMarble(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (MarbleItem.isMarble(hand)) return hand;

        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && MarbleItem.isMarble(it)) return it;
        }
        return null;
    }

    /** Highest total stat value across any marble the player is carrying, or -1 if they have none. */
    static int highestStatTotal(Player player) {
        int best = -1;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it == null || !MarbleItem.isMarble(it)) continue;
            MarbleData data = MarbleItem.read(it);
            if (data == null) continue;
            int total = data.getStats().total();
            if (total > best) best = total;
        }
        return best;
    }
}
