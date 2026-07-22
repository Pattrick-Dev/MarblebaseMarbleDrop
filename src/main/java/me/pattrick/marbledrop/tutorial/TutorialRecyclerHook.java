package me.pattrick.marbledrop.tutorial;

import me.pattrick.marbledrop.marble.MarbleItem;
import me.pattrick.marbledrop.progression.MarbleRecyclerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Runs at MONITOR priority, i.e. strictly after the real
 * MarbleRecyclerListener (HIGHEST) has already consumed the marble and
 * paid out dust. We don't touch that flow at all -- we just observe
 * that a genuine recycle happened during the RECYCLER tutorial step,
 * and, uniquely for the tutorial, hand a copy of that marble back so
 * the player still has one for the race step.
 * <p>
 * This is a deliberate one-time tutorial exception to normal recycler
 * economy (recycle should be a one-way trade). It's bounded and safe
 * because the tutorial is single-run per player (reset/skip are
 * admin-only), not something a player can trigger repeatedly.
 * <p>
 * Deliberately NOT ignoreCancelled: MarbleRecyclerListener always calls
 * setCancelled(true) on a valid recycler click (to stop the vanilla
 * grindstone UI), regardless of whether the recycle actually succeeded.
 * If this handler ignored cancelled events it would never run at all.
 */
public final class TutorialRecyclerHook implements Listener {

    private final Plugin plugin;
    private final TutorialManager tutorialManager;
    private final MarbleRecyclerManager recyclers;

    public TutorialRecyclerHook(Plugin plugin, TutorialManager tutorialManager, MarbleRecyclerManager recyclers) {
        this.plugin = plugin;
        this.tutorialManager = tutorialManager;
        this.recyclers = recyclers;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRecycleObserved(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player player = e.getPlayer();
        if (!tutorialManager.isActive(player)) return;
        if (tutorialManager.getStep(player) != TutorialStep.RECYCLER) return;

        Block block = e.getClickedBlock();
        if (block == null || block.getType() != Material.GRINDSTONE) return;
        if (!recyclers.isRecycler(block.getLocation())) return;
        if (!player.isSneaking()) return; // only the confirmed (sneak) attempt actually recycles

        ItemStack used = e.getItem();
        if (used == null || !MarbleItem.isMarble(used)) return;

        ItemStack toReturn = used.clone();
        toReturn.setAmount(1);

        // Delayed a few seconds so the real recycler's own "Recycled marble
        // ... into X dust" message has time to be read before the tutorial
        // piles the marble-back message on top of it.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.getInventory().addItem(toReturn);
            player.sendMessage(ChatColor.GREEN + "Since this is the tutorial, here's your marble back " +
                    ChatColor.GRAY + "(you'll need it for the race).");
            tutorialManager.completeStep(player, TutorialStep.RECYCLER);
        }, 60L);
    }
}
