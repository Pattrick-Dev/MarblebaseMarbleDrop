package me.pattrick.marbledrop.progression;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Cancels every click/drag in the /recipes preview -- see RecipesMenu, a
 * read-only display with no real crafting behind it -- and additionally
 * handles the Prev/Next nav arrows, which carry the page index to jump to
 * in a PersistentDataContainer tag rather than needing any separate
 * per-player "what page are they on" state here.
 */
public final class RecipesMenuListener implements Listener {

    private final Plugin plugin;

    public RecipesMenuListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().startsWith(RecipesMenu.TITLE_PREFIX)) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !(e.getWhoClicked() instanceof Player player)) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        Integer target = meta.getPersistentDataContainer().get(RecipesMenu.pageIndexKey(plugin), PersistentDataType.INTEGER);
        if (target == null) return;

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
        new RecipesMenu(plugin).open(player, target);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTitle().startsWith(RecipesMenu.TITLE_PREFIX)) {
            e.setCancelled(true);
        }
    }
}
