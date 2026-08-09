package me.pattrick.marbledrop.races;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class RaceLoadoutGuiListener implements Listener {

    private final TrackManager tracks;
    private final RaceManager races;

    public RaceLoadoutGuiListener(TrackManager tracks, RaceManager races) {
        this.tracks = tracks;
        this.races = races;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView() == null) return;

        String trackId = RaceLoadoutGui.trackIdFromTitle(e.getView().getTitle());
        if (trackId == null) return;

        e.setCancelled(true);

        int slot = e.getRawSlot();

        if (slot == RaceLoadoutGui.SLOT_BACK) {
            RaceGui.open(p, tracks, races);
            return;
        }

        RaceLoadout chosen = switch (slot) {
            case RaceLoadoutGui.SLOT_AGGRESSIVE -> RaceLoadout.AGGRESSIVE;
            case RaceLoadoutGui.SLOT_BALANCED -> RaceLoadout.BALANCED;
            case RaceLoadoutGui.SLOT_DEFENSIVE -> RaceLoadout.DEFENSIVE;
            default -> null;
        };
        if (chosen == null) return;

        races.setLoadout(trackId, p.getUniqueId(), chosen);

        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
        p.sendMessage(ChatColor.AQUA + "Loadout set to " + ChatColor.YELLOW + chosen.label() + ChatColor.AQUA + " for '" + trackId + "'.");
        RaceLoadoutGui.open(p, races, trackId);
    }
}
