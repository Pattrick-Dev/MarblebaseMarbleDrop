package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.marble.MarbleItem;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class RaceGuiListener implements Listener {

    private final TrackManager tracks;
    private final RaceManager races;

    public RaceGuiListener(TrackManager tracks, RaceManager races) {
        this.tracks = tracks;
        this.races = races;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView() == null) return;

        String title = e.getView().getTitle();
        if (!RaceGui.TITLE.equals(title)) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int slot = e.getRawSlot();

        // refresh
        if (slot == 49) {
            RaceGui.open(p, tracks, races);
            return;
        }

        // close
        if (slot == 53) {
            p.closeInventory();
            return;
        }

        // only track items live in 9..44
        if (slot < 9 || slot >= 45) return;

        if (clicked.getItemMeta() == null || clicked.getItemMeta().getDisplayName() == null) return;
        String trackId = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).toLowerCase();
        if (trackId.isBlank()) return;

        if (races.isRunning(trackId)) {
            p.sendMessage(ChatColor.RED + "That race is currently running.");
            return;
        }

        // Right click to leave
        if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
            races.leave(p, trackId);
            RaceGui.open(p, tracks, races);
            return;
        }

        // Already joined - left click opens the loadout picker instead of
        // re-attempting to join (which would just fail with "already entered").
        if (races.hasEntry(trackId, p.getUniqueId())) {
            RaceLoadoutGui.open(p, races, trackId);
            return;
        }

        // Left click to join
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir() || !MarbleItem.isMarble(held)) {
            p.sendMessage(ChatColor.RED + "Hold a marble in your main hand first.");
            return;
        }

        races.enter(p, trackId, held);
        RaceGui.open(p, tracks, races);
    }
}
