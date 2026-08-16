package me.pattrick.marbledrop.marble;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Full-grid picker for MarbleParticleStyle, opened from MarbleDisplayMenu's
 * "Particle Style" item. All 15 styles are laid out at once (instead of
 * cycling one at a time) so they're browsable; the currently selected one
 * is highlighted green. Back returns to MarbleDisplayMenu (see
 * MarbleDisplayMenuListener, which owns both menus' click handling).
 */
public final class MarbleDisplayStyleMenu {

    public static final String TITLE = ChatColor.DARK_GRAY + "Select Particle Style";

    public static final int SLOT_BACK = 31;

    // Two full rows of a 4-row (36-slot) inventory - 8 + 8 = 16, one per
    // style, centered with an empty border column on both sides.
    private static final int[] STYLE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16, 17,
            19, 20, 21, 22, 23, 24, 25, 26
    };

    private MarbleDisplayStyleMenu() {}

    public static MarbleParticleStyle styleFor(int slot) {
        MarbleParticleStyle[] values = MarbleParticleStyle.values();
        for (int i = 0; i < STYLE_SLOTS.length && i < values.length; i++) {
            if (STYLE_SLOTS[i] == slot) return values[i];
        }
        return null;
    }

    public static void open(Player player, MarbleParticleStyle current) {
        Inventory inv = Bukkit.createInventory(null, 36, TITLE);

        MarbleParticleStyle[] values = MarbleParticleStyle.values();
        for (int i = 0; i < values.length && i < STYLE_SLOTS.length; i++) {
            inv.setItem(STYLE_SLOTS[i], styleItem(values[i], values[i] == current));
        }

        inv.setItem(SLOT_BACK, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of()));

        player.openInventory(inv);
    }

    private static ItemStack styleItem(MarbleParticleStyle style, boolean selected) {
        String name = (selected ? ChatColor.GREEN : ChatColor.AQUA) + style.displayName();
        List<String> lore = selected
                ? List.of(ChatColor.GREEN + "✔ Selected")
                : List.of(ChatColor.GRAY + "Click to select");
        return item(style.icon(), name, lore);
    }

    private static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }
}
