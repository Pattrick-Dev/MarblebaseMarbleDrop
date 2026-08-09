package me.pattrick.marbledrop;

import me.pattrick.marbledrop.marble.MarbleItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

public class ListenEvents implements Listener {

    public ListenEvents() {}

    /**
     * Block anvil renames BEFORE the player can take the result.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getItem(0);
        ItemStack right = event.getInventory().getItem(1);

        if (MarbleItem.isMarble(left) || MarbleItem.isMarble(right)) {
            event.setResult(null);
        }
    }

    /**
     * Prevent placing marbles in the helmet slot, and prevent shift-click equipping.
     */
    @EventHandler
    public void onHelmetClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        // Helmet slot in player inventory is slot 39
        if (e.getSlotType() == InventoryType.SlotType.ARMOR && e.getSlot() == 39) {
            if (MarbleItem.isMarble(e.getCursor())) {
                e.setCancelled(true);
            }
        }

        // Shift-click protection (prevents quick-equipping / moving)
        if (e.isShiftClick() && MarbleItem.isMarble(e.getCurrentItem())) {
            e.setCancelled(true);
        }
    }
}
