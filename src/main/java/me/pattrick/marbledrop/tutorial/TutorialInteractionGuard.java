package me.pattrick.marbledrop.tutorial;

import me.pattrick.marbledrop.marble.MarbleItem;
import me.pattrick.marbledrop.progression.MarbleRecyclerManager;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableManager;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableMenu;
import me.pattrick.marbledrop.progression.upgrades.UpgradeStationManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * /md command gating alone doesn't stop a player from just walking up
 * to any Infusion Table / Upgrade Station / Recycler in the world and
 * right-clicking it directly -- those systems trigger off
 * PlayerInteractEvent, independent of any command. This closes that
 * gap for the tutorial specifically: if a player is mid-tutorial and
 * not yet on the matching step, the interaction is cancelled here
 * (LOW priority, before the real listeners) with a reminder instead.
 * <p>
 * Once a player reaches the matching step, or once they finish the
 * tutorial entirely, this gets out of the way completely.
 * <p>
 * Note: InfusionTableListener had to be changed to
 * {@code ignoreCancelled = true} for this to actually stop it --
 * without that, Bukkit would still call it even though we cancelled
 * the event first. Recycler/Upgrade listeners already had that set.
 */
public final class TutorialInteractionGuard implements Listener {

    private final TutorialManager tutorialManager;
    private final InfusionTableManager infusionTables;
    private final UpgradeStationManager upgradeStations;
    private final MarbleRecyclerManager recyclers;

    public TutorialInteractionGuard(TutorialManager tutorialManager,
                                     InfusionTableManager infusionTables,
                                     UpgradeStationManager upgradeStations,
                                     MarbleRecyclerManager recyclers) {
        this.tutorialManager = tutorialManager;
        this.infusionTables = infusionTables;
        this.upgradeStations = upgradeStations;
        this.recyclers = recyclers;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = e.getPlayer();
        if (!tutorialManager.isActive(player)) return;

        Block block = e.getClickedBlock();
        if (block == null) return;

        TutorialStep required = requiredStepFor(block);
        if (required == null) return; // not a progression station block at all

        TutorialStep current = tutorialManager.getStep(player);
        if (current == required) return; // exactly the step they're supposed to be doing -- allow

        e.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Finish the current tutorial step first: " +
                ChatColor.YELLOW + current.title());
        player.sendMessage(ChatColor.GRAY + current.hint());
    }

    /**
     * A player who drops their marble and lets it despawn (vanilla items
     * despawn after 5 minutes) has no way to recover -- Upgrade/Recycler/
     * Race all require having a marble in hand. Rather than relying on a
     * region plugin, just block dropping a marble outright while the
     * tutorial is active; nothing about the tutorial requires dropping
     * items on the ground.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent e) {
        Player player = e.getPlayer();
        if (!tutorialManager.isActive(player)) return;
        if (!MarbleItem.isMarble(e.getItemDrop().getItemStack())) return;

        e.setCancelled(true);
        player.sendMessage(ChatColor.RED + "You can't drop your marble during the tutorial.");
    }

    /**
     * The infusion amount is fixed at its default (50) during the
     * tutorial so a first-time player can't fiddle with it or accidentally
     * queue up more than they have dust for -- just place a catalyst (or
     * not) and click Infuse. Runs before InfusionTableListener's own
     * click handling (LOW < that handler's default NORMAL priority); see
     * the preCancelled capture added there for how this actually takes
     * effect only on the +/- buttons, leaving catalyst/confirm untouched.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInfusionMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!InfusionTableMenu.TITLE.equals(e.getView().getTitle())) return;

        if (!tutorialManager.isActive(player)) return;
        if (tutorialManager.getStep(player) != TutorialStep.INFUSION) return;

        int rawSlot = e.getRawSlot();
        if (rawSlot != InfusionTableMenu.SLOT_MINUS && rawSlot != InfusionTableMenu.SLOT_PLUS) return;

        e.setCancelled(true);
        player.sendMessage(ChatColor.GRAY + "The infusion amount is fixed for the tutorial -- just click Infuse.");
    }

    private TutorialStep requiredStepFor(Block block) {
        Material type = block.getType();

        if (type == Material.CAULDRON && infusionTables.isTable(block.getLocation())) {
            return TutorialStep.INFUSION;
        }
        if (type == Material.SMITHING_TABLE && upgradeStations.isStation(block)) {
            return TutorialStep.UPGRADE;
        }
        if (type == Material.GRINDSTONE && recyclers.isRecycler(block.getLocation())) {
            return TutorialStep.RECYCLER;
        }
        return null;
    }
}
