package me.pattrick.marbledrop.tutorial;

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
 * Deliberately uses a DIFFERENT title from the real TasksMenu.TITLE
 * ("Tasks") so it can never collide with TasksMenuListener's exact
 * title match, or with our own click-guard here.
 */
final class TutorialTaskGui {

    static final String TITLE = ChatColor.DARK_GRAY + "Tutorial Task";

    private TutorialTaskGui() {}

    static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE);

        ItemStack item = new ItemStack(Material.WHITE_WOOL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Kill 1 Sheep");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "A sheep has been spawned near you.");
            lore.add(ChatColor.GRAY + "Use the weapon you were given!");
            lore.add("");
            lore.add(ChatColor.GRAY + "Reward: " + ChatColor.GOLD + "50 Dust");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        inv.setItem(4, item);
        player.openInventory(inv);
    }
}
