package me.pattrick.marbledrop.progression;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Read-only preview of a single station recipe at a time, available any
 * time via /recipes -- Prev/Next arrows (top-left/top-right) cycle
 * through the three.
 * <p>
 * An earlier version crammed all three recipes side by side into one
 * screen. A chest inventory is always exactly 9 columns wide, though, and
 * three 3-wide grids already fill every column between them -- no amount
 * of border/filler around the outside edges fixes two recipes'
 * ingredients literally touching in the middle. Showing one recipe per
 * page sidesteps the problem entirely: every recipe gets the whole
 * screen to itself, with real empty space all around it.
 * <p>
 * Pulls the same shapeIcons() data StationRecipes registers the real
 * crafting recipes from (and that the tutorial's own preview and
 * craft-frame display use), so this can never drift out of sync with
 * what's actually craftable.
 * <p>
 * Every click is cancelled by RecipesMenuListener, which also reads the
 * Prev/Next arrows' embedded target-page tag to flip pages -- this is a
 * preview, not a real crafting grid, so nothing in it should ever be
 * takeable.
 */
public final class RecipesMenu {

    public static final String TITLE_PREFIX = ChatColor.DARK_GRAY + "Marble Drop Recipes";

    private final Plugin plugin;
    private final NamespacedKey pageIndexKey;

    public RecipesMenu(Plugin plugin) {
        this.plugin = plugin;
        this.pageIndexKey = pageIndexKey(plugin);
    }

    /** Shared so RecipesMenuListener can read the same tag off a clicked nav arrow without needing its own RecipesMenu instance. */
    public static NamespacedKey pageIndexKey(Plugin plugin) {
        return new NamespacedKey(plugin, "recipes_menu_page_index");
    }

    public void open(Player player, int index) {
        StationType[] types = StationType.values();
        int i = Math.floorMod(index, types.length);
        StationType type = types[i];

        String title = TITLE_PREFIX + ChatColor.DARK_GRAY + " (" + (i + 1) + "/" + types.length + ")";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, filler());
        }

        inv.setItem(0, navItem(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "<- Previous Recipe", Math.floorMod(i - 1, types.length)));
        inv.setItem(4, StationItems.create(plugin, type));
        inv.setItem(8, navItem(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Next Recipe ->", Math.floorMod(i + 1, types.length)));

        // Grid sits in rows 2-4 (of 0-5) and cols 3-5 (of 0-8) -- centered
        // on both axes, with a full spacer row above (row 1) and below
        // (row 5) mirroring the header row's own margin, and centered
        // under the header icon at col 4.
        ItemStack[] icons = StationRecipes.shapeIcons(plugin, type);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                ItemStack icon = icons[row * 3 + col];
                if (icon != null) inv.setItem(slot(2 + row, 3 + col), icon);
            }
        }

        player.openInventory(inv);
    }

    private static int slot(int row, int col) {
        return row * 9 + col;
    }

    private ItemStack navItem(Material material, String name, int targetIndex) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(pageIndexKey, PersistentDataType.INTEGER, targetIndex);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + " ");
        item.setItemMeta(meta);
        return item;
    }
}
