package me.pattrick.marbledrop.tutorial;

import me.pattrick.marbledrop.progression.StationItems;
import me.pattrick.marbledrop.progression.StationType;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Set;

/**
 * Crafting has no /md command to gate/intercept the way TASKS ("/tasks")
 * and RACE ("/md race") do - it happens entirely through the vanilla
 * crafting-table UI. This is the CRAFT step's only completion path,
 * observing (MONITOR, after anything else) for a genuine station item
 * coming out of the crafting grid, analogous to TutorialRecyclerHook.
 * <p>
 * The step is meant to teach all three recipes without letting the
 * player walk away with free stations, so each crafted result is taken
 * back the tick after it's made (checking both the cursor, where a
 * normal click leaves it, and the inventory, where a shift-click
 * auto-collects it) and progress toward "all three" is tracked per
 * player via TutorialManager.recordCraft.
 */
public final class TutorialCraftHook implements Listener {

    private final Plugin plugin;
    private final TutorialManager tutorialManager;

    public TutorialCraftHook(Plugin plugin, TutorialManager tutorialManager) {
        this.plugin = plugin;
        this.tutorialManager = tutorialManager;
    }

    /**
     * Purely cosmetic preview GUI - nothing in it should be takeable, and
     * (see onCraft below) its result slot must never be treated as a real
     * craft even though it's a WORKBENCH inventory populated with valid
     * ingredients.
     */
    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (!TutorialCraftGui.TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!tutorialManager.isActive(player)) return;
        if (tutorialManager.getStep(player) != TutorialStep.CRAFT) return;
        if (e.getRecipe() == null) return;

        // The preview GUI (TutorialCraftGui) is deliberately populated with
        // a real, valid ingredient layout so it looks right - without this
        // check, clicking ITS result slot would look like a genuine craft.
        if (TutorialCraftGui.TITLE.equals(e.getView().getTitle())) return;

        ItemStack result = e.getRecipe().getResult();
        StationType type = StationItems.readType(plugin, result);
        if (type == null) return;

        // Deferred a tick: the crafted item only lands on the cursor
        // (normal click) or in the inventory (shift-click auto-collect)
        // once this event finishes, and touching the player's inventory
        // or swapping their open view while the original crafting-table
        // transaction is still mid-flight corrupts it - that's what let
        // ingredients survive a craft instead of being consumed. Recording
        // progress and handing over the next recipe both wait here too,
        // for the same reason.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            ItemStack cursor = player.getItemOnCursor();
            if (StationItems.readType(plugin, cursor) != null) {
                player.setItemOnCursor(null);
            }
            player.getInventory().removeItem(StationItems.create(plugin, type));

            Set<StationType> crafted = tutorialManager.recordCraft(player, type);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            if (crafted.size() >= StationType.values().length) {
                tutorialManager.completeStep(player, TutorialStep.CRAFT);
                return;
            }

            player.sendMessage(ChatColor.GREEN + "Learned to craft the " + type.displayName()
                    + ChatColor.GREEN + "! (" + crafted.size() + "/" + StationType.values().length + ")");
            tutorialManager.giveNextCraftIngredients(player);

            // freshEntry=false: if no item-frame display is configured this
            // falls back to chat text rather than a GUI, since the player is
            // still standing at (and likely still looking at) the real
            // crafting table and forcing a new inventory open would yank
            // them out of it. The frame display itself is unaffected either
            // way - it's world-space entities, not an inventory swap.
            tutorialManager.showCraftPreviewForCurrentTarget(player, false);
        });
    }
}
