package me.pattrick.marbledrop.progression.upgrades;

import me.pattrick.marbledrop.progression.DustManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

public final class UpgradeStationListener implements Listener {

    private static final boolean DEBUG = true;

    private final UpgradeStationManager stations;
    private final DustManager dust;

    private final NamespacedKey K_MARBLE;
    private final NamespacedKey K_RARITY;

    public UpgradeStationListener(Plugin plugin, UpgradeStationManager stations, DustManager dust) {
        this.stations = stations;
        this.dust = dust;

        this.K_MARBLE = new NamespacedKey(plugin, "marble");
        this.K_RARITY = new NamespacedKey(plugin, "rarity");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUseUpgradeStation(PlayerInteractEvent e) {

        Player player = e.getPlayer();

        if (DEBUG) {
            player.sendMessage(ChatColor.DARK_GRAY + "[US-0] Fired: action=" + e.getAction()
                    + " cancelled=" + e.isCancelled());
        }

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            if (DEBUG) player.sendMessage(ChatColor.DARK_GRAY + "[US-1] Not RIGHT_CLICK_BLOCK");
            return;
        }

        Block block = e.getClickedBlock();
        if (block == null) {
            if (DEBUG) player.sendMessage(ChatColor.DARK_GRAY + "[US-2] Clicked block is null");
            return;
        }

        if (DEBUG) {
            player.sendMessage(ChatColor.DARK_GRAY + "[US-3] Block type=" + block.getType());
        }

        if (block.getType() != Material.SMITHING_TABLE) {
            if (DEBUG) player.sendMessage(ChatColor.DARK_GRAY + "[US-4] Not a smithing table");
            return;
        }

        boolean isStation = stations.isStation(block);
        if (DEBUG) {
            player.sendMessage(ChatColor.DARK_GRAY + "[US-5] stations.isStation=" + isStation);
        }

        if (!isStation) return;

        // Stop vanilla smithing UI
        e.setCancelled(true);
        if (DEBUG) player.sendMessage(ChatColor.DARK_GRAY + "[US-6] Event cancelled");

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (DEBUG) {
            player.sendMessage(ChatColor.DARK_GRAY + "[US-7] Main hand="
                    + (hand == null ? "null" : hand.getType().name()));
        }

        if (hand == null || hand.getType().isAir()) {
            if (DEBUG) player.sendMessage(ChatColor.DARK_GRAY + "[US-8] Hand is null/air");
            player.sendMessage(ChatColor.GRAY + "Hold a marble and right-click to upgrade it.");
            return;
        }

        ItemMeta meta = hand.getItemMeta();
        if (meta == null) {
            if (DEBUG) player.sendMessage(ChatColor.DARK_GRAY + "[US-9] ItemMeta is null");
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Byte marbleFlag = pdc.get(K_MARBLE, PersistentDataType.BYTE);
        String rarityRaw = pdc.get(K_RARITY, PersistentDataType.STRING);

        if (DEBUG) {
            player.sendMessage(ChatColor.DARK_GRAY + "[US-10] PDC marble="
                    + (marbleFlag == null ? "null" : marbleFlag));
            player.sendMessage(ChatColor.DARK_GRAY + "[US-11] PDC rarity="
                    + (rarityRaw == null ? "null" : rarityRaw));
        }

        if (!isMarble(hand)) {
            if (DEBUG) player.sendMessage(ChatColor.DARK_GRAY + "[US-12] isMarble=false");
            player.sendMessage(ChatColor.RED + "That item is not a marble.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
            return;
        }

        String rarity = readRarity(hand);
        if (rarity == null) rarity = "COMMON";

        if (DEBUG) {
            player.sendMessage(ChatColor.GREEN + "[US-13] SUCCESS → opening UpgradeMenu (rarity=" + rarity + ")");
        }

        UpgradeMenu.open(player, dust);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.15f);
    }

    private boolean isMarble(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte flag = pdc.get(K_MARBLE, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    private String readRarity(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String rarityStr = pdc.get(K_RARITY, PersistentDataType.STRING);
        if (rarityStr != null && !rarityStr.isEmpty()) {
            return normalizeRarity(rarityStr);
        }

        List<String> lore = meta.getLore();
        if (lore != null) {
            for (String line : lore) {
                if (line == null) continue;
                String stripped = ChatColor.stripColor(line);
                if (stripped == null) continue;

                if (stripped.toLowerCase(Locale.ROOT).startsWith("rarity:")) {
                    String after = stripped.substring("rarity:".length()).trim();
                    return normalizeRarity(after);
                }
            }
        }

        return null;
    }

    private String normalizeRarity(String raw) {
        String cleaned = ChatColor.stripColor(raw);
        if (cleaned == null) cleaned = raw;

        cleaned = cleaned.trim().replace(' ', '_').toUpperCase(Locale.ROOT);

        return switch (cleaned) {
            case "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY" -> cleaned;
            default -> "COMMON";
        };
    }
}
