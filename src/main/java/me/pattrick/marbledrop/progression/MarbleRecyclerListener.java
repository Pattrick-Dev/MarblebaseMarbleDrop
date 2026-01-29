package me.pattrick.marbledrop.progression;

import me.pattrick.marbledrop.marble.MarbleData;
import me.pattrick.marbledrop.marble.MarbleItem;
import me.pattrick.marbledrop.marble.MarbleRarity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
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

public final class MarbleRecyclerListener implements Listener {

    private final MarbleRecyclerManager recyclers;
    private final DustManager dust;
    @SuppressWarnings("unused")
    private final Plugin plugin;

    public MarbleRecyclerListener(Plugin plugin, MarbleRecyclerManager recyclers, DustManager dust) {
        this.plugin = plugin;
        this.recyclers = recyclers;
        this.dust = dust;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseRecycler(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Avoid double-fire (main hand + offhand)
        if (e.getHand() != EquipmentSlot.HAND) return;

        Block block = e.getClickedBlock();
        if (block == null) return;
        if (block.getType() != Material.GRINDSTONE) return;

        // Only marked grindstones are recyclers
        if (!recyclers.isRecycler(block.getLocation())) return;

        Player player = e.getPlayer();

        // Stop vanilla grindstone UI from opening
        e.setCancelled(true);

        // Use the item actually used in the interaction (more reliable than main hand in some cases)
        ItemStack hand = e.getItem();
        if (hand == null || hand.getType().isAir() || !hand.hasItemMeta()) {
            player.sendMessage(ChatColor.GRAY + "Hold a marble and " + ChatColor.YELLOW + "Shift + Right-click"
                    + ChatColor.GRAY + " to recycle it into dust.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        // MODERN marble check
        if (!MarbleItem.isMarble(hand)) {
            player.sendMessage(ChatColor.RED + "That item is not a marble.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
            return;
        }

        MarbleData data = MarbleItem.read(hand);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "That marble is missing data. (Try re-infusing.)");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
            return;
        }

        MarbleRarity rarityEnum = data.getRarity();
        String rarity = (rarityEnum != null ? rarityEnum.name() : "COMMON");

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
        // IMPORTANT: remove the exact stack in the player's main hand
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
