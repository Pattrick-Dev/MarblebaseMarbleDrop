package me.pattrick.marbledrop.races;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TrackGuiListener implements Listener {

    private final Plugin plugin;
    private final TrackManager tracks;
    private final TrackVisualizer visualizer;

    private final Map<UUID, Boolean> awaitingNewId = new HashMap<>();

    public TrackGuiListener(Plugin plugin, TrackManager tracks, TrackVisualizer visualizer) {
        this.plugin = plugin;
        this.tracks = tracks;
        this.visualizer = visualizer;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        InventoryView view = e.getView();
        String title = view.getTitle();

        if (title.equals(TrackGui.LIST_TITLE)) {
            e.setCancelled(true);

            int slot = e.getRawSlot();
            if (slot == 11) {
                awaitingNewId.put(p.getUniqueId(), true);
                p.closeInventory();
                p.sendMessage(ChatColor.GREEN + "Type a new track id in chat (letters/numbers/_/-). Type 'cancel' to abort.");
                return;
            }

            if (slot >= 13 && slot <= 17 && e.getCurrentItem() != null) {
                String name = e.getCurrentItem().getItemMeta() != null
                        ? ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName())
                        : null;

                if (name != null && tracks.getTrack(name.toLowerCase()) != null) {
                    TrackGui.openEditor(p, tracks, name.toLowerCase(), visualizer);
                }
                return;
            }

            if (slot == 26) p.closeInventory();
            return;
        }

        if (title.startsWith(TrackGui.EDIT_TITLE_PREFIX)) {
            e.setCancelled(true);

            String id = ChatColor.stripColor(title.substring(TrackGui.EDIT_TITLE_PREFIX.length())).toLowerCase();
            if (tracks.getTrack(id) == null) {
                p.sendMessage(ChatColor.RED + "Track no longer exists.");
                p.closeInventory();
                return;
            }

            int slot = e.getRawSlot();

            switch (slot) {
                case 10 -> {
                    tracks.addPoint(id, p.getLocation());
                    p.sendMessage(ChatColor.GREEN + "Added point. Total: " + tracks.getTrack(id).size());
                    TrackGui.openEditor(p, tracks, id, visualizer);
                }
                case 12 -> {
                    if (!tracks.undoLast(id)) p.sendMessage(ChatColor.RED + "No points to undo.");
                    else p.sendMessage(ChatColor.YELLOW + "Undid last point. Total: " + tracks.getTrack(id).size());
                    TrackGui.openEditor(p, tracks, id, visualizer);
                }
                case 14 -> {
                    visualizer.show(p, id);
                    p.sendMessage(ChatColor.AQUA + "Showing track '" + id + "'.");
                }
                case 16 -> {
                    visualizer.hide(p);
                    p.sendMessage(ChatColor.DARK_AQUA + "Hid track particles.");
                }
                case 22 -> {
                    tracks.saveNow();
                    p.sendMessage(ChatColor.GOLD + "Saved tracks.yml");
                }
                case 24 -> {
                    tracks.removeTrack(id);
                    visualizer.hide(p);
                    p.sendMessage(ChatColor.RED + "Deleted track '" + id + "'.");
                    TrackGui.openList(p, tracks);
                }
                case 26 -> p.closeInventory();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uid = p.getUniqueId();

        if (!awaitingNewId.containsKey(uid)) return;

        e.setCancelled(true);

        String msg = e.getMessage().trim();

        if (msg.equalsIgnoreCase("cancel")) {
            awaitingNewId.remove(uid);
            p.sendMessage(ChatColor.YELLOW + "Cancelled track creation.");
            return;
        }

        String trackId = msg.toLowerCase().replaceAll("[^a-z0-9_\\-]", "");
        if (trackId.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Invalid id. Use letters/numbers/_/- only. Try again or type 'cancel'.");
            return;
        }

        awaitingNewId.remove(uid);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!tracks.createTrack(trackId, p.getWorld())) {
                p.sendMessage(ChatColor.RED + "Track already exists: " + trackId);
                TrackGui.openList(p, tracks);
                return;
            }

            p.sendMessage(ChatColor.GREEN + "Created track '" + trackId + "'.");
            TrackGui.openEditor(p, tracks, trackId, visualizer);
        });
    }
}
