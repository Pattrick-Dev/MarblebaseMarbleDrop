package me.pattrick.marbledrop;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class ListenEvents implements Listener {



    private final JavaPlugin plugin;

    // Legacy + new system keys (support both during transition)
    private final NamespacedKey marbleKey;    // "marble" (legacy)
    private final NamespacedKey isMarbleKey;  // "is_marble" (new system)

    public ListenEvents() {
        this.plugin = JavaPlugin.getProvidingPlugin(getClass());

        this.marbleKey = new NamespacedKey(plugin, "marble");
        this.isMarbleKey = new NamespacedKey(plugin, "is_marble");
    }

    /**
     * PDC-only marble identifier.
     * Supports BOTH legacy ("marble") and new system ("is_marble") flags.
     */
    private boolean isMarble(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Byte legacy = pdc.get(marbleKey, PersistentDataType.BYTE);
        Byte modern = pdc.get(isMarbleKey, PersistentDataType.BYTE);

        return (legacy != null && legacy == (byte) 1) || (modern != null && modern == (byte) 1);
    }

    /**
     * Ensures an item is tagged as a marble via PDC.
     * Sets BOTH legacy and new flags for compatibility.
     */
    private ItemStack tagAsMarble(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(marbleKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(isMarbleKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Prevent placing marbles. Uses the actual item used to place.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        final Player player = event.getPlayer();
        final Block blockPlaced = event.getBlock();
        final Material placedType = blockPlaced.getType();

        final ItemStack used = event.getItemInHand();

        if ((placedType == Material.PLAYER_HEAD || placedType == Material.PLAYER_WALL_HEAD) && isMarble(used)) {
            event.setCancelled(true);
        }
    }

    /**
     * Block anvil renames BEFORE the player can take the result.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getItem(0);
        ItemStack right = event.getInventory().getItem(1);

        if (isMarble(left) || isMarble(right)) {
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
            if (isMarble(e.getCursor())) {
                e.setCancelled(true);
            }
        }

        // Shift-click protection (prevents quick-equipping / moving)
        if (e.isShiftClick() && isMarble(e.getCurrentItem())) {
            e.setCancelled(true);
        }
    }
}
