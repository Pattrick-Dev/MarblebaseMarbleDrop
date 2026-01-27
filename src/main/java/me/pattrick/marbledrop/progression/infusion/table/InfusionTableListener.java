package me.pattrick.marbledrop.progression.infusion.table;

import me.pattrick.marbledrop.progression.DustManager;
import me.pattrick.marbledrop.progression.infusion.InfusionService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InfusionTableListener implements Listener {

    private static final boolean DEBUG = false;

    private final InfusionTableManager tables;
    private final DustManager dust;
    private final InfusionService infusion;

    private final Map<UUID, InfusionTableMenu> openMenus = new HashMap<>();

    public InfusionTableListener(InfusionTableManager tables, DustManager dust, InfusionService infusion) {
        this.tables = tables;
        this.dust = dust;
        this.infusion = infusion;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        if (b.getType() != Material.CAULDRON) return;
        if (!tables.isTable(b.getLocation())) return;

        e.setCancelled(true);

        Player player = e.getPlayer();
        if (DEBUG) player.sendMessage(ChatColor.GRAY + "[InfusionTable] Opening menu");

        InfusionTableMenu menu = new InfusionTableMenu(dust, infusion, b);
        openMenus.put(player.getUniqueId(), menu);
        menu.open(player);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        String titleStripped = ChatColor.stripColor(e.getView().getTitle());
        if (!"Infusion Table".equalsIgnoreCase(titleStripped)) return;

        Inventory top = e.getView().getTopInventory();
        int topSize = top.getSize();
        int rawSlot = e.getRawSlot();

        InfusionTableMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            if (DEBUG) player.sendMessage(ChatColor.RED + "[InfusionTable] menu == null");
            return;
        }

        // Shift-click from player inventory INTO catalyst slot
        if (rawSlot >= topSize && e.isShiftClick()) {
            ItemStack moving = e.getCurrentItem();
            if (moving == null || moving.getType().isAir()) return;

            ItemStack currentCat = top.getItem(InfusionTableMenu.SLOT_CATALYST);
            if (currentCat == null || currentCat.getType().isAir()) {
                e.setCancelled(true);
                top.setItem(InfusionTableMenu.SLOT_CATALYST, moving.clone());
                e.getClickedInventory().setItem(e.getSlot(), new ItemStack(Material.AIR));

                Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                        () -> menu.draw(player, top)
                );
            }
            return;
        }

        // Ignore clicks in player inventory
        if (rawSlot >= topSize) return;

        // Allow only catalyst slot to accept items/moves
        if (rawSlot == InfusionTableMenu.SLOT_CATALYST) {
            // Let the click happen, then redraw next tick so info updates
            Bukkit.getScheduler().runTask(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                    () -> menu.draw(player, top)
            );
            return;
        }

        // prevent taking background/buttons
        e.setCancelled(true);

        if (rawSlot == InfusionTableMenu.SLOT_MINUS) {
            menu.adjust(player, top, -50);
        } else if (rawSlot == InfusionTableMenu.SLOT_PLUS) {
            menu.adjust(player, top, +50);
        } else if (rawSlot == InfusionTableMenu.SLOT_CONFIRM) {
            menu.confirm(player, top);
        }
    }

    // Dragging items into catalyst slot
    @EventHandler
    public void onInvDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        String titleStripped = ChatColor.stripColor(e.getView().getTitle());
        if (!"Infusion Table".equalsIgnoreCase(titleStripped)) return;

        // If the drag touches the catalyst slot, redraw next tick
        if (!e.getRawSlots().contains(InfusionTableMenu.SLOT_CATALYST)) return;

        InfusionTableMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) return;

        Inventory top = e.getView().getTopInventory();
        Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                () -> menu.draw(player, top)
        );
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;

        String titleStripped = ChatColor.stripColor(e.getView().getTitle());
        if (!"Infusion Table".equalsIgnoreCase(titleStripped)) return;

        Inventory top = e.getView().getTopInventory();

        // return catalyst item if they left it in there
        ItemStack cat = top.getItem(InfusionTableMenu.SLOT_CATALYST);
        if (cat != null && !cat.getType().isAir()) {
            player.getInventory().addItem(cat);
            top.setItem(InfusionTableMenu.SLOT_CATALYST, new ItemStack(Material.AIR));
        }

        openMenus.remove(player.getUniqueId());
    }
}
