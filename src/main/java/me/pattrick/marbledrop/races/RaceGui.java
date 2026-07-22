package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.marble.MarbleItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class RaceGui {

    private RaceGui() {}

    public static final String TITLE = ChatColor.DARK_PURPLE + "Marble Races";

    public static void open(Player p, TrackManager tracks, RaceManager races) {
        Inventory inv = Bukkit.createInventory(p, 54, TITLE);

        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.GOLD + "How to Join",
                List.of(
                        ChatColor.GRAY + "1) Hold a marble in your main hand",
                        ChatColor.GRAY + "2) Click an OPEN track to join",
                        ChatColor.GRAY + "Right-click to leave",
                        "",
                        ChatColor.DARK_GRAY + "Tracks must be opened by admins."
                )));

        // Open tracks list
        List<String> open = races.openTrackIds();
        int slot = 9;

        if (open.isEmpty()) {
            inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "No Open Tracks",
                    List.of(ChatColor.GRAY + "An admin needs to open a track.")));
        } else {
            for (String id : open) {
                if (slot >= 45) break;

                MarbleTrack t = tracks.getTrack(id);
                int count = races.lobbyCount(id);

                boolean joined = races.hasEntry(id, p.getUniqueId());
                boolean running = races.isRunning(id);

                Material mat = running ? Material.RED_CONCRETE : (joined ? Material.LIGHT_BLUE_CONCRETE : Material.LIME_CONCRETE);
                String status = running ? (ChatColor.RED + "RUNNING") : (ChatColor.GREEN + "OPEN");

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Status: " + status);
                lore.add(ChatColor.GRAY + "Entries: " + ChatColor.WHITE + count + ChatColor.DARK_GRAY + "/" + ChatColor.WHITE + RaceManager.MAX_ENTRIES_PER_TRACK);
                if (t != null) {
                    lore.add(ChatColor.GRAY + "Points: " + ChatColor.WHITE + t.size());
                    lore.add(ChatColor.GRAY + "World: " + ChatColor.WHITE + t.getWorld().getName());
                }
                lore.add("");

                if (running) {
                    lore.add(ChatColor.RED + "Race is currently running.");
                } else if (joined) {
                    lore.add(ChatColor.AQUA + "Right-click to leave");
                } else {
                    lore.add(ChatColor.GREEN + "Click to join");
                    lore.add(ChatColor.DARK_GRAY + "(Hold a marble first)");
                }

                inv.setItem(slot++, item(mat, ChatColor.YELLOW + id, lore));
            }
        }

        inv.setItem(49, item(Material.SLIME_BALL, ChatColor.AQUA + "Refresh", List.of(ChatColor.GRAY + "Re-open the menu")));
        inv.setItem(53, item(Material.BARRIER, ChatColor.RED + "Close", List.of()));

        p.openInventory(inv);
    }

    public static boolean isValidJoinItem(ItemStack it) {
        if (it == null) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        String name = meta.getDisplayName();
        return name != null && !ChatColor.stripColor(name).isBlank();
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
