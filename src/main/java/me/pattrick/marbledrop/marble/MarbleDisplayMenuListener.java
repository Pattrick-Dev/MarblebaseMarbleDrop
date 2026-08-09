package me.pattrick.marbledrop.marble;

import me.pattrick.marbledrop.command.Commands;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Right-click your own placed marble (or any, if you're a
 * marbledrop.admin -- same rule as breaking one) to open
 * MarbleDisplayMenu and toggle its hologram/particles, or drill into
 * MarbleDisplayStyleMenu to pick a particle style. Which block a given
 * open menu belongs to is tracked in-memory per player (the same
 * "openMenus" shape InfusionTableListener already uses for its own menu)
 * -- shared across both menus since navigating between them is just
 * re-opening a different inventory for the same block.
 */
public final class MarbleDisplayMenuListener implements Listener {

    private final Map<UUID, Block> openFor = new HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // avoid double-fire (main hand + offhand)

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) return;

        BlockState state = block.getState();
        if (!(state instanceof Skull skull)) return;

        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        MarbleData data = MarbleItem.readFromContainer(pdc);
        if (data == null) return; // a normal head, not a marble display

        Player player = event.getPlayer();

        String placedByStr = pdc.get(MarbleKeys.PLACED_BY, PersistentDataType.STRING);
        UUID placedBy = placedByStr == null ? null : UUID.fromString(placedByStr);
        boolean isOwner = placedBy != null && placedBy.equals(player.getUniqueId());

        if (!isOwner && !player.hasPermission(Commands.ADMIN)) return; // silently let the click pass through as a normal interact

        event.setCancelled(true);
        openFor.put(player.getUniqueId(), block);
        MarbleDisplayMenu.open(player, data, pdc, skull.getPlayerProfile());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (MarbleDisplayMenu.TITLE.equals(title)) {
            handleMainClick(event);
        } else if (MarbleDisplayStyleMenu.TITLE.equals(title)) {
            handleStyleClick(event);
        }
    }

    private void handleMainClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        Block block = openFor.get(player.getUniqueId());
        if (block == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot == MarbleDisplayMenu.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        Skull skull = skullAt(block);
        if (skull == null) {
            player.closeInventory();
            return;
        }

        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        MarbleData data = MarbleItem.readFromContainer(pdc);
        if (data == null) {
            // The block changed out from under this open menu (broken, replaced, ...).
            player.closeInventory();
            return;
        }

        if (slot == MarbleDisplayMenu.SLOT_STYLE) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
            MarbleDisplayStyleMenu.open(player, MarbleDisplayMenu.styleOf(pdc));
            openFor.put(player.getUniqueId(), block);
            return;
        }

        if (slot != MarbleDisplayMenu.SLOT_HOLOGRAM && slot != MarbleDisplayMenu.SLOT_PARTICLES) return;

        if (slot == MarbleDisplayMenu.SLOT_HOLOGRAM) {
            boolean next = !MarbleDisplayMenu.isHologramEnabled(pdc);
            pdc.set(MarbleKeys.SHOW_HOLOGRAM, PersistentDataType.BYTE, (byte) (next ? 1 : 0));
        } else {
            boolean next = !MarbleDisplayMenu.isParticlesEnabled(pdc);
            pdc.set(MarbleKeys.SHOW_PARTICLES, PersistentDataType.BYTE, (byte) (next ? 1 : 0));
        }
        skull.update();

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);

        // Refresh in place so the toggled button's new state is visible immediately.
        MarbleDisplayMenu.open(player, data, pdc, skull.getPlayerProfile());
        openFor.put(player.getUniqueId(), block);
    }

    private void handleStyleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        Block block = openFor.get(player.getUniqueId());
        if (block == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();

        if (slot == MarbleDisplayStyleMenu.SLOT_BACK) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
            reopenMain(player, block);
            return;
        }

        MarbleParticleStyle chosen = MarbleDisplayStyleMenu.styleFor(slot);
        if (chosen == null) return;

        Skull skull = skullAt(block);
        if (skull == null) {
            player.closeInventory();
            return;
        }

        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        if (MarbleItem.readFromContainer(pdc) == null) {
            player.closeInventory();
            return;
        }

        pdc.set(MarbleKeys.PARTICLE_STYLE, PersistentDataType.STRING, chosen.name());
        skull.update();

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
        reopenMain(player, block);
    }

    private void reopenMain(Player player, Block block) {
        Skull skull = skullAt(block);
        if (skull == null) {
            player.closeInventory();
            return;
        }

        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        MarbleData data = MarbleItem.readFromContainer(pdc);
        if (data == null) {
            player.closeInventory();
            return;
        }

        MarbleDisplayMenu.open(player, data, pdc, skull.getPlayerProfile());
        openFor.put(player.getUniqueId(), block);
    }

    private Skull skullAt(Block block) {
        BlockState state = block.getState();
        return (state instanceof Skull skull) ? skull : null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (!MarbleDisplayMenu.TITLE.equals(title) && !MarbleDisplayStyleMenu.TITLE.equals(title)) return;
        openFor.remove(event.getPlayer().getUniqueId());
    }
}
