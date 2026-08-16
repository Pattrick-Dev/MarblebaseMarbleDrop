package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.progression.TaskManager;
import me.pattrick.marbledrop.progression.TaskTrigger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Handles right-clicking the Boost item (see RaceBoostItem) - fires a
 * real, player-timed speed impulse on that player's own marble for the
 * race they're currently in.
 * <p>
 * Primary path (ProtocolLib installed): reads the raw client packet
 * (USE_ITEM for air, USE_ITEM_ON for a block) and cancels it before it
 * ever reaches the server's own packet handler. This matters because of a
 * vanilla anti-desync behavior: after processing a right-click packet
 * (ServerboundUseItemOnPacket), the server unconditionally re-sends the
 * player's real held-slot content back to the client afterward
 * (InventoryMenu.forceHeldSlot()) - regardless of whether the
 * Bukkit-level PlayerInteractEvent for that click got cancelled. With the
 * inventory overlay active (see RaceInventoryOverlay), that resync
 * immediately overwrites our fake Boost item with whatever's really in
 * that slot (the player's marble), which is why the old
 * PlayerInteractEvent-only version of this listener showed the boost item
 * "reverting" to the marble right after every click. Cancelling the raw
 * packet stops the server from ever running that handler in the first
 * place, so the forced resync never fires.
 * <p>
 * Fallback path (ProtocolLib absent, or overlay null): the original
 * PlayerInteractEvent handling - fine there since without the overlay
 * there's no fake item for a stray resync to clobber.
 * <p>
 * Charges are tracked as real server-side state on RaceManager (see
 * getBoostCharges()/setBoostCharges()) rather than the item's own stack
 * amount - with the inventory overlay active there's no real ItemStack to
 * read a stack size off in the first place, so the count has to live
 * somewhere real regardless of which detection path above is active.
 * <p>
 * The actual packet-reading logic lives in RaceBoostPacketBridge, a
 * separate class this one only ever constructs from inside the
 * ProtocolLib-present branch below - see TrackBuildToolsListener's
 * javadoc for why that split is required (this class is a real Bukkit
 * Listener, unconditionally constructed/registered in Main, and must
 * never itself contain ProtocolLib-referencing bytecode).
 */
public final class RaceBoostListener implements Listener {

    private final Plugin plugin;
    private final RaceManager raceManager;
    private final RaceInventoryOverlay overlay;
    private final TaskManager taskManager;

    public RaceBoostListener(Plugin plugin, RaceManager raceManager, RaceInventoryOverlay overlay, TaskManager taskManager) {
        this.plugin = plugin;
        this.raceManager = raceManager;
        this.overlay = overlay;
        this.taskManager = taskManager;

        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            new RaceBoostPacketBridge(plugin, this::isHoldingBoostItem, this::handleBoost);
        }
    }

    /** Fallback only - see the class javadoc. Never fires for a boost-item click when the packet listener above already handled (and cancelled) it. */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = e.getPlayer();
        if (!isHoldingBoostItem(player)) return;

        e.setCancelled(true);
        handleBoost(player);
    }

    private boolean isHoldingBoostItem(Player player) {
        int heldSlot = player.getInventory().getHeldItemSlot();
        return (overlay != null)
                ? overlay.isFakeSlot(player, heldSlot, RaceInventoryOverlay.TAG_BOOST)
                : RaceBoostItem.isBoostItem(plugin, player.getInventory().getItemInMainHand());
    }

    private void handleBoost(Player player) {
        MarbleRunner runner = raceManager.getMyRunner(player.getUniqueId());
        if (runner == null) {
            player.sendMessage(ChatColor.RED + "Your race has already finished - no marble left to boost.");
            return;
        }

        int remaining = raceManager.getBoostCharges(player.getUniqueId());
        if (remaining <= 0) {
            player.sendMessage(ChatColor.RED + "Out of boost charges.");
            return;
        }

        runner.triggerBoost();
        remaining--;
        raceManager.setBoostCharges(player.getUniqueId(), remaining);
        if (taskManager != null) taskManager.increment(player, TaskTrigger.USE_BOOST, 1);

        int heldSlot = player.getInventory().getHeldItemSlot();
        if (overlay != null) {
            if (remaining <= 0) {
                // Not overlay.clear() - that resyncs the real slot, which
                // would reveal whatever's actually underneath (the
                // player's marble, since a race never touches the real
                // item there - see RaceInventoryOverlay). Staying on the
                // fake-slot path with an empty item keeps the slot showing
                // nothing while still tagged TAG_BOOST, so a stray click
                // here still gets intercepted as "out of charges" instead
                // of falling through to a real world interaction.
                overlay.show(player, heldSlot, RaceInventoryOverlay.TAG_BOOST, new ItemStack(Material.AIR));
            } else {
                overlay.show(player, heldSlot, RaceInventoryOverlay.TAG_BOOST, RaceBoostItem.create(plugin, remaining));
            }
        } else {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (remaining <= 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                item.setAmount(remaining);
            }
        }

        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1.4f);
        player.spawnParticle(Particle.FIREWORK, player.getLocation(), 12, 0.2, 0.2, 0.2, 0.05);
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "BOOST! " + ChatColor.GRAY + "(" + remaining + " left)");
    }
}
