package me.pattrick.marbledrop;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class ListenEvents implements Listener {

    public static HashMap<UUID, Long> cooldown;

    private final JavaPlugin plugin;
    private final NamespacedKey marbleKey;

    public ListenEvents() {
        ListenEvents.cooldown = new HashMap<>();
        this.plugin = JavaPlugin.getProvidingPlugin(getClass());
        this.marbleKey = new NamespacedKey(plugin, "marble");
    }

    /**
     * PDC-only marble identifier.
     * No lore fallback since you have no legacy items.
     */
    private boolean isMarble(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Byte flag = meta.getPersistentDataContainer().get(marbleKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    /**
     * Ensures an item is tagged as a marble via PDC.
     */
    private ItemStack tagAsMarble(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(marbleKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    private void syncInventoryNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    @EventHandler
    public void OnPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (!ListenEvents.cooldown.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "You are able to find a marble!");
        } else {
            final long timeElapsed = System.currentTimeMillis() - ListenEvents.cooldown.get(player.getUniqueId());
            if (timeElapsed >= 86400000L) {
                player.sendMessage(ChatColor.GREEN + "You are able to find a marble!");
            } else {
                player.sendMessage(ChatColor.RED + "You cannot find another marble for " + (86400000L - timeElapsed) / 1000L / 60L + " minutes");
            }
        }
    }

    public void CooldownCheck(Player p) {
        if (ListenEvents.cooldown.containsKey(p.getUniqueId())) {
            final long timeElapsed = System.currentTimeMillis() - ListenEvents.cooldown.get(p.getUniqueId());
            p.sendMessage(ChatColor.RED + "You cannot gain another marble for " + (86400000L - timeElapsed) / 1000L / 60L + " minutes");
        } else if (!ListenEvents.cooldown.containsKey(p.getUniqueId())) {
            p.sendMessage(ChatColor.GREEN + "You are able to get a marble!");
            p.sendMessage("" + ListenEvents.cooldown);
        } else {
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

        if (event.getBlock().getType() == Material.TALL_GRASS
                || event.getBlock().getType() == Material.KELP
                || event.getBlock().getType() == Material.KELP_PLANT
                || event.getBlock().getType() == Material.SEAGRASS) {
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

                    ItemStack marble = HeadDatabase.getMarbleHead(player.getDisplayName());
                    marble = tagAsMarble(marble);

                    player.getWorld().dropItemNaturally(event.getBlock().getLocation(), marble);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                } else {
                    final long timeElapsed = System.currentTimeMillis() - ListenEvents.cooldown.get(player.getUniqueId());
                    if (timeElapsed >= 86400000L) {
                        ListenEvents.cooldown.put(player.getUniqueId(), System.currentTimeMillis());
                        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "You found a marble!");

                        ItemStack marble = HeadDatabase.getMarbleHead(player.getDisplayName());
                        marble = tagAsMarble(marble);

                        player.getWorld().dropItemNaturally(event.getBlock().getLocation(), marble);
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Prevent placing marbles. Uses the actual item used to place.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        final Player player = event.getPlayer();
        final Block blockPlaced = event.getBlock();
        final Material placedType = blockPlaced.getType();

        final ItemStack used = event.getItemInHand();

        if ((placedType == Material.PLAYER_HEAD || placedType == Material.PLAYER_WALL_HEAD) && isMarble(used)) {
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Don't place your marbles!");
            player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.0f, 1.0f);
            event.setCancelled(true);
        }
    }

    /**
     * Block anvil renames BEFORE the player can take the result.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getItem(0);
        ItemStack right = event.getInventory().getItem(1);

        if (isMarble(left) || isMarble(right)) {
            event.setResult(null);
        }
    }

    /**
     * Prevent placing marbles in the helmet slot.
     */
    @EventHandler
    public void onHelmetClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        if (e.getSlotType() == InventoryType.SlotType.ARMOR && e.getSlot() == 39) {
            if (isMarble(e.getCursor())) {
                e.setCancelled(true);
            }
        }

        if (e.isShiftClick() && isMarble(e.getCurrentItem())) {
            e.setCancelled(true);
        }
    }
}
