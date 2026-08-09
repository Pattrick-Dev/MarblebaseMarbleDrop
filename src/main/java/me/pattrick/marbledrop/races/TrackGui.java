package me.pattrick.marbledrop.races;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class TrackGui {

    private TrackGui() {}

    public static final String LIST_TITLE_PREFIX = ChatColor.DARK_AQUA + "Tracks";
    public static final String EDIT_TITLE_PREFIX = ChatColor.DARK_GREEN + "Edit Track: ";
    public static final String POINTS_TITLE_PREFIX = ChatColor.DARK_PURPLE + "Track Points: ";

    // Layout constants
    private static final int LIST_SIZE = 54;    // 6 rows
    private static final int PAGE_SLOTS = 45;   // slots 0-44 for items

    public static void openList(Player p, TrackManager tracks, int page) {
        if (page < 0) page = 0;

        List<String> ids = tracks.sortedIds();
        int total = ids.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SLOTS));
        if (page >= pages) page = pages - 1;

        Inventory inv = Bukkit.createInventory(p, LIST_SIZE,
                LIST_TITLE_PREFIX + ChatColor.GRAY + " (Page " + (page + 1) + "/" + pages + ")");

        // Track items
        int start = page * PAGE_SLOTS;
        int end = Math.min(total, start + PAGE_SLOTS);

        int slot = 0;
        for (int i = start; i < end; i++) {
            String id = ids.get(i);
            MarbleTrack t = tracks.getTrack(id);
            if (t == null) continue;

            inv.setItem(slot++, item(Material.PAPER, ChatColor.YELLOW + id,
                    List.of(ChatColor.GRAY + "World: " + t.getWorld().getName(),
                            ChatColor.GRAY + "Points: " + t.size(),
                            ChatColor.GRAY + "Watch: " + (t.getWatchLocation() != null ? ChatColor.GREEN + "set" : ChatColor.RED + "not set"),
                            "",
                            ChatColor.AQUA + "Click to edit")));
        }

        // Controls
        inv.setItem(45, item(Material.ARROW, ChatColor.AQUA + "Previous Page", List.of(ChatColor.GRAY + "Go back")));
        inv.setItem(49, item(Material.EMERALD_BLOCK, ChatColor.GREEN + "Create Track",
                List.of(ChatColor.GRAY + "Click, then type the id in chat")));
        inv.setItem(50, item(Material.ARROW, ChatColor.AQUA + "Next Page", List.of(ChatColor.GRAY + "Go forward")));
        inv.setItem(53, item(Material.BARRIER, ChatColor.RED + "Close", List.of()));

        inv.setItem(48, item(Material.PAPER, ChatColor.YELLOW + "Page " + (page + 1) + "/" + pages,
                List.of(ChatColor.GRAY + "Total tracks: " + total)));

        p.openInventory(inv);
    }

    public static void openEditor(Player p, TrackManager tracks, String id, TrackVisualizer visualizer) {
        MarbleTrack t = tracks.getTrack(id);
        if (t == null) {
            p.sendMessage(ChatColor.RED + "Track not found.");
            return;
        }

        Inventory inv = Bukkit.createInventory(p, 54, EDIT_TITLE_PREFIX + id);

        // Info
        inv.setItem(4, item(Material.MAP, ChatColor.YELLOW + "Track Info",
                List.of(ChatColor.GRAY + "ID: " + id,
                        ChatColor.GRAY + "World: " + t.getWorld().getName(),
                        ChatColor.GRAY + "Points: " + t.size(),
                        ChatColor.GRAY + "Watch: " + (t.getWatchLocation() != null ? ChatColor.GREEN + "set" : ChatColor.RED + "not set"))));

        // Point management
        inv.setItem(10, item(Material.LIME_WOOL, ChatColor.GREEN + "Add Point (Here)",
                List.of(ChatColor.GRAY + "Adds your current location as the next waypoint")));

        inv.setItem(11, item(Material.BLAZE_ROD, ChatColor.GOLD + "Get Build Kit",
                List.of(ChatColor.GRAY + "Point Tool, Undo, Preview, Watch, Finish",
                        ChatColor.DARK_GRAY + "(Tools are bound to this track)")));

        inv.setItem(12, item(Material.YELLOW_WOOL, ChatColor.YELLOW + "Undo Last Point",
                List.of(ChatColor.GRAY + "Removes the last waypoint")));

        inv.setItem(14, item(Material.BOOK, ChatColor.AQUA + "View Points",
                List.of(ChatColor.GRAY + "See every waypoint",
                        ChatColor.GRAY + "Click a point to teleport")));

        // Watch spot
        inv.setItem(15, item(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Set Watch Spot (Here)",
                List.of(ChatColor.GRAY + "Sets the spectate location to your position")));

        inv.setItem(16, item(Material.BARRIER, ChatColor.RED + "Clear Watch Spot",
                List.of(ChatColor.GRAY + "Removes the stored watch location")));

        // Visualizer
        inv.setItem(20, item(Material.FIREWORK_ROCKET, ChatColor.AQUA + "Show Track",
                List.of(ChatColor.GRAY + "Draws particles so you can preview the path")));

        inv.setItem(21, item(Material.GRAY_DYE, ChatColor.DARK_AQUA + "Hide Track",
                List.of(ChatColor.GRAY + "Stops drawing particles")));

        // Save/Delete
        inv.setItem(22, item(Material.WRITABLE_BOOK, ChatColor.GOLD + "Save",
                List.of(ChatColor.GRAY + "Writes tracks.yml")));

        inv.setItem(24, item(Material.REDSTONE_BLOCK, ChatColor.RED + "Delete Track",
                List.of(ChatColor.GRAY + "Permanent")));

        // Back/Close
        inv.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(ChatColor.GRAY + "Return to track list")));
        inv.setItem(53, item(Material.BARRIER, ChatColor.RED + "Close", List.of()));

        p.openInventory(inv);
    }

    public static void openPoints(Player p, TrackManager tracks, String id, int page) {
        MarbleTrack t = tracks.getTrack(id);
        if (t == null) {
            p.sendMessage(ChatColor.RED + "Track not found.");
            return;
        }

        if (page < 0) page = 0;

        List<Location> pts = t.getPoints();
        int total = pts.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SLOTS));
        if (page >= pages) page = pages - 1;

        Inventory inv = Bukkit.createInventory(p, 54,
                POINTS_TITLE_PREFIX + ChatColor.YELLOW + id + ChatColor.GRAY + " (Page " + (page + 1) + "/" + pages + ")");

        int start = page * PAGE_SLOTS;
        int end = Math.min(total, start + PAGE_SLOTS);

        int slot = 0;
        for (int i = start; i < end; i++) {
            Location loc = pts.get(i);
            inv.setItem(slot++, item(Material.PAPER, ChatColor.YELLOW + "Point #" + (i + 1),
                    List.of(ChatColor.GRAY + "X: " + fmt(loc.getX()),
                            ChatColor.GRAY + "Y: " + fmt(loc.getY()),
                            ChatColor.GRAY + "Z: " + fmt(loc.getZ()),
                            ChatColor.DARK_GRAY + "Yaw: " + fmt(loc.getYaw()) + " Pitch: " + fmt(loc.getPitch()),
                            "",
                            ChatColor.AQUA + "Click to teleport")));
        }

        inv.setItem(45, item(Material.ARROW, ChatColor.AQUA + "Previous Page", List.of(ChatColor.GRAY + "Go back")));
        inv.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(ChatColor.GRAY + "Return to editor")));
        inv.setItem(50, item(Material.ARROW, ChatColor.AQUA + "Next Page", List.of(ChatColor.GRAY + "Go forward")));
        inv.setItem(53, item(Material.BARRIER, ChatColor.RED + "Close", List.of()));

        inv.setItem(48, item(Material.MAP, ChatColor.YELLOW + "Points: " + total,
                List.of(ChatColor.GRAY + "Page " + (page + 1) + "/" + pages)));

        p.openInventory(inv);
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.US, "%.2f", d);
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
