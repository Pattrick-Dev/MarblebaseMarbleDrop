package me.pattrick.marbledrop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives a player items without letting the resulting inventory change
 * award a vanilla advancement (e.g. "Diamonds!", "Acquire Hardware") --
 * those criteria are purely inventory-content-based and fire regardless
 * of how the item got there, so a plugin handing someone a diamond pops
 * the same toast as mining one. Scoped to items THIS plugin hands out
 * directly (tutorial rewards, admin "give" commands) -- normal survival
 * mining/crafting is completely unaffected.
 */
public final class SilentGive implements Listener {

    private static final Set<UUID> suppressing = ConcurrentHashMap.newKeySet();

    public static void register(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new SilentGive(), plugin);
    }

    public static void give(Plugin plugin, Player player, ItemStack... items) {
        UUID id = player.getUniqueId();
        suppressing.add(id);
        player.getInventory().addItem(items);

        // The advancement-done flush happens a tick or two after the
        // inventory change is broadcast -- keep suppressing for a short
        // window, then let normal advancement checks resume as usual.
        Bukkit.getScheduler().runTaskLater(plugin, () -> suppressing.remove(id), 5L);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        Player player = e.getPlayer();
        if (!suppressing.contains(player.getUniqueId())) return;

        var progress = player.getAdvancementProgress(e.getAdvancement());
        for (String criterion : e.getAdvancement().getCriteria()) {
            progress.revokeCriteria(criterion);
        }
    }
}
