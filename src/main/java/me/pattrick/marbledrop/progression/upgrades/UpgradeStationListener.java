package me.pattrick.marbledrop.progression.upgrades;

import me.pattrick.marbledrop.marble.MarbleData;
import me.pattrick.marbledrop.marble.MarbleItem;
import me.pattrick.marbledrop.progression.DustManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class UpgradeStationListener implements Listener {

    private final UpgradeStationManager stations;
    private final DustManager dust;
    private final Plugin plugin;

    public UpgradeStationListener(Plugin plugin, UpgradeStationManager stations, DustManager dust) {
        this.plugin = plugin;
        this.stations = stations;
        this.dust = dust;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseUpgradeStation(PlayerInteractEvent e) {
        // Debug
        Player player = e.getPlayer();
        player.sendMessage(ChatColor.DARK_GRAY + "[US-0] Fired: action=" + e.getAction() + " cancelled=" + e.isCancelled());

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Avoid double-fire (main hand + offhand)
        if (e.getHand() != EquipmentSlot.HAND) {
            player.sendMessage(ChatColor.DARK_GRAY + "[US-1] Ignored: hand=" + e.getHand());
            return;
        }

        Block block = e.getClickedBlock();
        if (block == null) {
            player.sendMessage(ChatColor.DARK_GRAY + "[US-2] Block null");
            return;
        }

        player.sendMessage(ChatColor.DARK_GRAY + "[US-3] Block type=" + block.getType());

        if (block.getType() != Material.SMITHING_TABLE) return;

        // Only marked smithing tables are upgrade stations
        boolean isStation = stations.isStation(block);
        player.sendMessage(ChatColor.DARK_GRAY + "[US-4] stations.isStation=" + isStation);
        if (!isStation) return;

        // Stop vanilla smithing UI from opening
        e.setCancelled(true);
        player.sendMessage(ChatColor.DARK_GRAY + "[US-5] Event cancelled");

        // Use the actual item used in the interaction (more reliable than main hand in some cases)
        ItemStack used = e.getItem();
        player.sendMessage(ChatColor.DARK_GRAY + "[US-6] Used item=" + (used == null ? "null" : used.getType().name()));

        if (used == null || used.getType().isAir() || !used.hasItemMeta()) {
            player.sendMessage(ChatColor.GRAY + "Hold a marble and " + ChatColor.YELLOW + "Right-click"
                    + ChatColor.GRAY + " to upgrade it.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            player.sendMessage(ChatColor.DARK_GRAY + "[US-7] No valid held item/meta");
            return;
        }

        boolean modernIsMarble = MarbleItem.isMarble(used);
        player.sendMessage(ChatColor.DARK_GRAY + "[US-8] MarbleItem.isMarble=" + modernIsMarble);

        if (!modernIsMarble) {
            player.sendMessage(ChatColor.RED + "That item is not a marble.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
            player.sendMessage(ChatColor.DARK_GRAY + "[US-9] Rejected: not marble");
            return;
        }

        MarbleData data = MarbleItem.read(used);
        player.sendMessage(ChatColor.DARK_GRAY + "[US-10] MarbleData read=" + (data != null));

        if (data == null) {
            player.sendMessage(ChatColor.RED + "That marble is missing data. (Try re-infusing.)");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
            player.sendMessage(ChatColor.DARK_GRAY + "[US-11] Rejected: data null");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "[US-12] SUCCESS -> opening UpgradeMenu (rarity=" + data.getRarity().name() + ")");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.15f);

        // Open the upgrade UI
        // (runTask is harmless here and can avoid edge cases with cancelled interactions)
        plugin.getServer().getScheduler().runTask(plugin, () -> UpgradeMenu.open(player, dust));
    }
}
