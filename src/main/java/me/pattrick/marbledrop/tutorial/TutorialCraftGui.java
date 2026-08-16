package me.pattrick.marbledrop.tutorial;

import me.pattrick.marbledrop.progression.StationItems;
import me.pattrick.marbledrop.progression.StationRecipes;
import me.pattrick.marbledrop.progression.StationType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Fallback preview of the CURRENT recipe's 3x3 layout, used only when the
 * server has no craft-frame display configured (see TutorialCraftFrameManager
 * - that's the primary mechanism now). Pulls the same shapeIcons() data as
 * the frame manager so both stay in sync with the real registered recipe.
 * <p>
 * A real Inventory is inherently per-player, unlike a shared in-world
 * display, which is why this works as a fallback at all: two players on
 * different recipes at once each see their own correct layout.
 * <p>
 * Uses InventoryType.WORKBENCH so it visually matches a real crafting
 * table (slots 1-9 as the 3x3 matrix, slot 0 as the result) - but every
 * click in it is cancelled (see TutorialCraftHook.onGuiClick), and
 * TutorialCraftHook explicitly ignores CraftItemEvents from this title,
 * since a WORKBENCH inventory populated with valid ingredients would
 * otherwise let a click on the result slot actually "complete" a craft.
 */
final class TutorialCraftGui {

    static final String TITLE = ChatColor.DARK_GRAY + "Craft This";

    private TutorialCraftGui() {}

    static void open(Plugin plugin, Player player, StationType type) {
        Inventory inv = Bukkit.createInventory(null, InventoryType.WORKBENCH, TITLE);

        ItemStack[] icons = StationRecipes.shapeIcons(plugin, type);
        for (int i = 0; i < icons.length; i++) {
            if (icons[i] != null) inv.setItem(1 + i, icons[i]);
        }

        inv.setItem(0, StationItems.create(plugin, type));
        player.openInventory(inv);
    }

    /**
     * Text-only alternative to open() - used for every stage after the
     * first, since by then the player is likely still standing at (and
     * looking at) a real crafting table, and force-opening another
     * inventory would yank them out of it (see TutorialCraftHook).
     */
    static void sendChatPreview(Plugin plugin, Player player, StationType type) {
        ItemStack[] icons = StationRecipes.shapeIcons(plugin, type);

        player.sendMessage(ChatColor.YELLOW + "Next: craft the " + type.displayName() + ChatColor.YELLOW + ":");

        for (int row = 0; row < 3; row++) {
            StringBuilder line = new StringBuilder(ChatColor.GRAY.toString());
            for (int col = 0; col < 3; col++) {
                if (col > 0) line.append(ChatColor.DARK_GRAY + " | " + ChatColor.GRAY);
                ItemStack icon = icons[row * 3 + col];
                line.append(icon == null ? "-" : niceName(icon.getType()));
            }
            player.sendMessage(line.toString());
        }
    }

    private static String niceName(Material material) {
        String[] words = material.name().split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (out.length() > 0) out.append(' ');
            out.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return out.toString();
    }
}
