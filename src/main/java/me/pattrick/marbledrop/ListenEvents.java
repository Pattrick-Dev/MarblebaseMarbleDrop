package me.pattrick.marbledrop;

import org.bukkit.inventory.AnvilInventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryClickEvent;
import java.util.List;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Sound;
import org.bukkit.Material;
import java.util.Random;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.UUID;
import java.util.HashMap;
import org.bukkit.event.Listener;

public class ListenEvents implements Listener
{
    public static HashMap<UUID, Long> cooldown;
    
    public ListenEvents() {
        ListenEvents.cooldown = new HashMap<UUID, Long>();
    }
    
    @EventHandler
    public void OnPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (!ListenEvents.cooldown.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "You are able to find a marble!");
        }
        else {
            final long timeElapsed = System.currentTimeMillis() - ListenEvents.cooldown.get(player.getUniqueId());
            if (timeElapsed >= 86400000L) {
                player.sendMessage(ChatColor.GREEN + "You are able to find a marble!");
            }
            else {
                player.sendMessage(ChatColor.RED + "You cannot find another marble for " + (86400000L - timeElapsed) / 1000L / 60L + " minutes");
            }
        }
    }
    
    public void CooldownCheck(Player p) {
        if (ListenEvents.cooldown.containsKey(p.getUniqueId())) {
            final long timeElapsed = System.currentTimeMillis() - ListenEvents.cooldown.get(p.getUniqueId());
            p.sendMessage(ChatColor.RED + "You cannot gain another marble for " + (86400000L - timeElapsed) / 1000L / 60L + " minutes");
        }
        else if (!ListenEvents.cooldown.containsKey(p.getUniqueId())) {
            p.sendMessage(ChatColor.GREEN + "You are able to get a marble!");
            p.sendMessage("" + ListenEvents.cooldown);
        }
        else {
            p.sendMessage("how");
        }
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        final File dataFolder = Bukkit.getPluginManager().getPlugin("MarbleBaseMD").getDataFolder();
        final File filePath = new File(dataFolder, "config.yml");
        final FileConfiguration config = YamlConfiguration.loadConfiguration(filePath);
        final String chance = config.getString("drop-chance");
        final boolean debug = config.getBoolean("debug-mode");
        final Player player = event.getPlayer();
        final Random r = new Random();
        final double randomInt = r.nextDouble() * 100.0;
        if (event.getBlock().getType() == Material.TALL_GRASS || event.getBlock().getType() == Material.TALL_GRASS || event.getBlock().getType() == Material.KELP || event.getBlock().getType() == Material.KELP_PLANT || event.getBlock().getType() == Material.SEAGRASS) {
            return;
        }
        try {
            final double chanceDouble = Double.parseDouble(chance);
            if (debug && player.hasPermission("marbledrop.debug")) {
                player.sendMessage(ChatColor.GRAY + "Current odds: " + randomInt + " <= " + chanceDouble);
            }
            if (randomInt <= chanceDouble) {
                if (!ListenEvents.cooldown.containsKey(player.getUniqueId())) {
                    ListenEvents.cooldown.put(player.getUniqueId(), System.currentTimeMillis());
                    player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "You found a marble!");
                    player.getWorld().dropItemNaturally(event.getBlock().getLocation(), HeadDatabase.getMarbleHead(player.getDisplayName()));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
                else {
                    final long timeElapsed = System.currentTimeMillis() - ListenEvents.cooldown.get(player.getUniqueId());
                    if (timeElapsed >= 86400000L) {
                        ListenEvents.cooldown.put(player.getUniqueId(), System.currentTimeMillis());
                        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "You found a marble!");
                        player.getWorld().dropItemNaturally(event.getBlock().getLocation(), HeadDatabase.getMarbleHead(player.getDisplayName()));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        final Player player = event.getPlayer();
        final Block blockPlaced = event.getBlock();
        final Material material = blockPlaced.getType();
        final ItemStack holdingItemMainHand = player.getInventory().getItemInMainHand();
        final ItemMeta holdingItemMainHandMeta = holdingItemMainHand.getItemMeta();
        if (holdingItemMainHandMeta == null) {
            return;
        }
        if (material.equals(Material.PLAYER_HEAD) || material.equals(Material.PLAYER_WALL_HEAD) && holdingItemMainHandMeta.hasLore()) {
            final List<String> headLore = holdingItemMainHandMeta.getLore();
            if (headLore.contains("§8Marblebase Marble")) {
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Don't place your marbles!");
                player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.0f, 1.0f);
            }
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onOffHandBlockPlace(BlockPlaceEvent e) {
        final Player player = e.getPlayer();
        final Block blockPlaced = e.getBlock();
        final Material material = blockPlaced.getType();
        final ItemStack holdingItemMainHand = player.getInventory().getItemInOffHand();
        final ItemMeta holdingItemMainHandMeta = holdingItemMainHand.getItemMeta();
        if (holdingItemMainHandMeta == null) {
            return;
        }
        if (material.equals(Material.PLAYER_HEAD) || material.equals(Material.PLAYER_WALL_HEAD) && holdingItemMainHandMeta.hasLore()) {
            final List<String> headLore = holdingItemMainHandMeta.getLore();
            if (headLore.contains("§8Marblebase Marble")) {
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Don't place your marbles!");
                player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.0f, 1.0f);
            }
            e.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onHeadRename(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player) {
        	if (!e.getCurrentItem().getItemMeta().hasLore()) {
        		return;
        	}
            if (e.getClick().isShiftClick() && e.getCurrentItem().getItemMeta().getLore().contains("�8Marblebase Marble") && e.getInventory().getType() == InventoryType.CRAFTING) {
                e.setCancelled(true);
            }
            if (e.getCursor().getItemMeta().getLore().contains("§8Marblebase Marble") && e.getSlotType() == InventoryType.SlotType.ARMOR) {
                e.setCancelled(true);
            }
        }
        final Player player = (Player) e.getWhoClicked();
        if (e.getInventory() instanceof AnvilInventory) {
            if (e.getSlotType() != InventoryType.SlotType.RESULT) {
                return;
            }
            if (e.getCurrentItem().getItemMeta().getLore() != null) {
                final List<String> lore = e.getCurrentItem().getItemMeta().getLore();
                if (lore.contains("§8Marblebase Marble")) {
                    player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.0f, 1.0f);
                    player.closeInventory();
                    player.sendMessage(ChatColor.RED + "Do not rename your marbles!");
                    e.setCancelled(true);
                    player.updateInventory();
                }
            }
        }
    }
}
