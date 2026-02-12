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

public final class TrackGui {

    private TrackGui() {}

    public static final String LIST_TITLE = ChatColor.DARK_AQUA + "Tracks";
    public static final String EDIT_TITLE_PREFIX = ChatColor.DARK_GREEN + "Edit Track: ";

    public static void openList(Player p, TrackManager tracks) {
        Inventory inv = Bukkit.createInventory(p, 27, LIST_TITLE);

        // Create button
        inv.setItem(11, item(Material.EMERALD_BLOCK, ChatColor.GREEN + "Create Track",
                List.of(ChatColor.GRAY + "Click to create a new track")));

        // Existing tracks (up to 9 shown for now, KISS)
        int slot = 13;
        for (String id : tracks.ids()) {
            if (slot > 17) break;
            MarbleTrack t = tracks.getTrack(id);
            inv.setItem(slot++, item(Material.PAPER, ChatColor.YELLOW + id,
                    List.of(ChatColor.GRAY + "World: " + t.getWorld().getName(),
                            ChatColor.GRAY + "Points: " + t.size(),
                            "",
                            ChatColor.AQUA + "Click to edit")));
        }

        inv.setItem(26, item(Material.BARRIER, ChatColor.RED + "Close", List.of()));

        p.openInventory(inv);
    }

    public static void openEditor(Player p, TrackManager tracks, String id, TrackVisualizer visualizer) {
        MarbleTrack t = tracks.getTrack(id);
        if (t == null) {
            p.sendMessage(ChatColor.RED + "Track not found.");
            return;
        }

        Inventory inv = Bukkit.createInventory(p, 27, EDIT_TITLE_PREFIX + id);

        inv.setItem(10, item(Material.LIME_WOOL, ChatColor.GREEN + "Add Point",
                List.of(ChatColor.GRAY + "Adds your current location as the next waypoint")));

        inv.setItem(12, item(Material.YELLOW_WOOL, ChatColor.YELLOW + "Undo Last Point",
                List.of(ChatColor.GRAY + "Removes the last waypoint")));

        inv.setItem(14, item(Material.ENDER_EYE, ChatColor.AQUA + "Show Track",
                List.of(ChatColor.GRAY + "Draws particles so you can preview the path")));

        inv.setItem(16, item(Material.ENDER_PEARL, ChatColor.DARK_AQUA + "Hide Track",
                List.of(ChatColor.GRAY + "Stops drawing particles")));

        inv.setItem(22, item(Material.WRITABLE_BOOK, ChatColor.GOLD + "Save",
                List.of(ChatColor.GRAY + "Writes tracks.yml")));

        inv.setItem(24, item(Material.REDSTONE_BLOCK, ChatColor.RED + "Delete Track",
                List.of(ChatColor.GRAY + "Permanent")));

        inv.setItem(26, item(Material.BARRIER, ChatColor.RED + "Exit", List.of()));

        // info
        inv.setItem(4, item(Material.MAP, ChatColor.YELLOW + "Track Info",
                List.of(ChatColor.GRAY + "ID: " + id,
                        ChatColor.GRAY + "World: " + t.getWorld().getName(),
                        ChatColor.GRAY + "Points: " + t.size())));

        p.openInventory(inv);
    }

    private static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(new ArrayList<>(lore));
            it.setItemMeta(meta);
        }
        return it;
    }
}
