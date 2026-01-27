package me.pattrick.marbledrop.progression;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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

public final class MarbleRecyclerListener implements Listener {

    private final MarbleRecyclerManager recyclers;
    private final DustManager dust;

    private final NamespacedKey K_MARBLE;
    private final NamespacedKey K_RARITY;

    public MarbleRecyclerListener(Plugin plugin, MarbleRecyclerManager recyclers, DustManager dust) {
        this.recyclers = recyclers;
        this.dust = dust;

        this.K_MARBLE = new NamespacedKey(plugin, "marble");
        this.K_RARITY = new NamespacedKey(plugin, "rarity");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseRecycler(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = e.getClickedBlock();
        if (block == null) return;
        if (block.getType() != Material.GRINDSTONE) return;

        // Only marked grindstones are recyclers
        if (!recyclers.isRecycler(block.getLocation())) return;

        Player player = e.getPlayer();

        // Stop vanilla grindstone UI from opening
        e.setCancelled(true);

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || !hand.hasItemMeta()) {
            player.sendMessage(ChatColor.GRAY + "Hold a marble and " + ChatColor.YELLOW + "Shift + Right-click"
                    + ChatColor.GRAY + " to recycle it into dust.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        if (!isMarble(hand)) {
            player.sendMessage(ChatColor.RED + "That item is not a marble.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
            return;
        }

        String rarity = readRarity(hand);
        if (rarity == null) rarity = "COMMON";

        int amount = hand.getAmount();
        int per = dustReturnFor(rarity);
        int total = per * amount;

        // ✅ SAFETY: require sneak to confirm
        if (!player.isSneaking()) {
            player.sendMessage(ChatColor.GRAY + "Shift + Right-click to recycle "
                    + ChatColor.YELLOW + amount + ChatColor.GRAY + " marble" + (amount == 1 ? "" : "s")
                    + ChatColor.GRAY + " (" + ChatColor.AQUA + rarity + ChatColor.GRAY + ") for "
                    + ChatColor.GOLD + total + ChatColor.GRAY + " dust.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
            return;
        }

        // --- CONFIRMED RECYCLE (sneaking) ---

        // Remove item(s) first (anti-dupe)
        player.getInventory().setItemInMainHand(null);

        // Award dust
        dust.addDust(player, total);

        // Feedback
        var loc = block.getLocation().add(0.5, 1.0, 0.5);
        block.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, loc, 18, 0.25, 0.15, 0.25, 0.05, block.getBlockData());
        block.getWorld().spawnParticle(Particle.ASH, loc, 10, 0.25, 0.10, 0.25, 0.01);
        block.getWorld().spawnParticle(Particle.CRIT, loc, 10, 0.25, 0.10, 0.25, 0.10);

        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.9f, 1.05f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);

        player.sendMessage(ChatColor.GRAY + "Recycled marble"
                + ChatColor.GRAY + " (" + ChatColor.AQUA + rarity + ChatColor.GRAY + ") into "
                + ChatColor.GOLD + total + ChatColor.GRAY + " dust.");
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

    private int dustReturnFor(String rarity) {
        return switch (rarity) {
            case "UNCOMMON" -> 35;
            case "RARE" -> 80;
            case "EPIC" -> 160;
            case "LEGENDARY" -> 300;
            default -> 20; // COMMON
        };
    }
}
