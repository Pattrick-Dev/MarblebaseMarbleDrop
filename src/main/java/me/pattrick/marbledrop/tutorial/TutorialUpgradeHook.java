package me.pattrick.marbledrop.tutorial;

import me.pattrick.marbledrop.progression.upgrades.UpgradeMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Limits the tutorial's UPGRADE step to a single upgrade: as soon as the
 * player's marble stat total rises above the baseline snapshotted on
 * entering the step (the same signal TutorialProgressPoller uses to
 * decide the step is complete), their Upgrade Station GUI is force-closed
 * so they can't keep spending dust on further upgrades while the poller
 * catches up.
 * <p>
 * Deliberately NOT ignoreCancelled: UpgradeMenu.handleClick() always
 * calls setCancelled(true) on the click (it's a fake inventory, nothing
 * should actually be taken), so an ignoreCancelled handler here would
 * never run -- same class of bug as TutorialRecyclerHook's MONITOR
 * handler had to avoid.
 */
public final class TutorialUpgradeHook implements Listener {

    private final TutorialManager tutorialManager;

    public TutorialUpgradeHook(TutorialManager tutorialManager) {
        this.tutorialManager = tutorialManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUpgradeClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!UpgradeMenu.isUpgradeMenu(e)) return;

        if (!tutorialManager.isActive(player)) return;
        if (tutorialManager.getStep(player) != TutorialStep.UPGRADE) return;

        int baseline = tutorialManager.upgradeBaseline(player);
        int current = TutorialMarbleUtil.highestStatTotal(player);
        if (baseline >= 0 && current > baseline) {
            player.closeInventory();
        }
    }
}
