package me.pattrick.marbledrop.races;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets an entrant pick a RaceLoadout for their still-in-lobby entry. The
 * track id is encoded in the inventory title (same idiom RaceGui already
 * uses for track tiles' display names) rather than tracked separately, so
 * this needs no per-player session state of its own.
 */
public final class RaceLoadoutGui {

    private RaceLoadoutGui() {}

    public static final String TITLE_PREFIX = ChatColor.DARK_PURPLE + "Loadout: ";

    public static final int SLOT_AGGRESSIVE = 11;
    public static final int SLOT_BALANCED = 13;
    public static final int SLOT_DEFENSIVE = 15;
    public static final int SLOT_BACK = 22;

    public static String titleFor(String trackId) {
        return TITLE_PREFIX + trackId;
    }

    /** The track id this GUI's title encodes, or null if the title isn't one of ours. */
    public static String trackIdFromTitle(String title) {
        if (title == null || !title.startsWith(TITLE_PREFIX)) return null;
        String id = title.substring(TITLE_PREFIX.length());
        return id.isBlank() ? null : id;
    }

    public static void open(Player p, RaceManager races, String trackId) {
        RaceLoadout current = races.getLoadout(trackId, p.getUniqueId());

        Inventory inv = Bukkit.createInventory(p, 27, titleFor(trackId));

        inv.setItem(SLOT_AGGRESSIVE, loadoutItem(RaceLoadout.AGGRESSIVE, Material.BLAZE_POWDER, current));
        inv.setItem(SLOT_BALANCED, loadoutItem(RaceLoadout.BALANCED, Material.IRON_INGOT, current));
        inv.setItem(SLOT_DEFENSIVE, loadoutItem(RaceLoadout.DEFENSIVE, Material.SHIELD, current));

        inv.setItem(SLOT_BACK, item(Material.BARRIER, ChatColor.RED + "Back",
                List.of(ChatColor.GRAY + "Return to the race menu")));

        p.openInventory(inv);
    }

    private static ItemStack loadoutItem(RaceLoadout loadout, Material mat, RaceLoadout current) {
        boolean selected = loadout == current;

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + loadout.description());
        lore.add("");
        lore.add(selected ? ChatColor.GREEN + "✔ Selected" : ChatColor.DARK_GRAY + "Click to select");

        return item(mat, (selected ? ChatColor.GREEN : ChatColor.YELLOW) + loadout.label(), lore);
    }

    private static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }
}
