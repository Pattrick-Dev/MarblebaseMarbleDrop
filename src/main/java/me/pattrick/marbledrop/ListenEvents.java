package me.pattrick.marbledrop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

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
     * Prevent placing marble heads. Uses the actual item used to place.
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
     * Prevent right-click equipping marbles as a helmet.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRightClickEquip(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!isMarble(item)) return;

        if (item.getType() == Material.PLAYER_HEAD) {
            Player player = event.getPlayer();
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Don't wear your marbles!");
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

    @EventHandler
    public void onHeadRename(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) {
            return;
        }

        final ItemStack current = e.getCurrentItem();
        final ItemStack cursor = e.getCursor();
        final ClickType click = e.getClick();

        // 1) Shift-click in crafting/player inventory (common source of "disappears" via desync)
        if (click.isShiftClick()
                && e.getInventory().getType() == InventoryType.CRAFTING
                && isMarble(current)) {
            e.setCancelled(true);
            syncInventoryNextTick(player);
            return;
        }

        // 2) Prevent placing marbles into armor slots
        if (e.getSlotType() == InventoryType.SlotType.ARMOR && isMarble(cursor)) {
            e.setCancelled(true);
            syncInventoryNextTick(player);
            return;
        }

        // 3) Prevent NUMBER_KEY hotbar swaps that move items without using cursor
        if (click == ClickType.NUMBER_KEY) {
            int hotbarButton = e.getHotbarButton(); // 0-8
            ItemStack hotbarItem = (hotbarButton >= 0) ? player.getInventory().getItem(hotbarButton) : null;

            // If either the hotbar item or the clicked slot item is a marble, block it
            if (isMarble(hotbarItem) || isMarble(current)) {
                e.setCancelled(true);
                syncInventoryNextTick(player);
                return;
            }
        }

        // 4) Prevent double-click collect-to-cursor from scooping marbles and “losing” them client-side
        if (click == ClickType.DOUBLE_CLICK) {
            if (isMarble(cursor) || isMarble(current)) {
                e.setCancelled(true);
                syncInventoryNextTick(player);
                return;
            }
        }

        // 5) Extra safety: if they're interacting with an anvil and a marble is involved, block it
        if (e.getInventory() instanceof AnvilInventory) {
            if (isMarble(current) || isMarble(cursor)) {
                player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.0f, 1.0f);
                e.setCancelled(true);
                syncInventoryNextTick(player);
            }
        }
    }

    /**
     * Prevent dragging marbles into armor slots (drag events bypass InventoryClickEvent slotType logic).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = e.getOldCursor();
        if (!isMarble(cursor)) return;

        // If any of the dragged-to slots are armor slots in the player's inventory, cancel.
        Inventory top = e.getView().getTopInventory();
        Inventory bottom = e.getView().getBottomInventory();

        // Raw slots: bottom inventory armor slots are within the view; easiest is to block if any target is an armor slot type.
        // We can approximate by checking if the drag affects the bottom inventory and the slot index is within typical armor positions.
        // Safer: if any target slot maps to SlotType.ARMOR, cancel (but DragEvent doesn't give SlotType directly).
        // Minimal practical fix: cancel any drag of marble while bottom inventory is involved.
        Set<Integer> rawSlots = e.getRawSlots();
        int topSize = top.getSize();

        for (int raw : rawSlots) {
            // raw >= topSize means it's in the player's inventory area
            if (raw >= topSize) {
                e.setCancelled(true);
                syncInventoryNextTick(player);
                return;
            }
        }
    }
}
