package me.pattrick.marbledrop.races;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TrackGuiListener implements Listener {

    private final Plugin plugin;
    private final TrackManager tracks;
    private final TrackVisualizer visualizer;

    private final Map<UUID, Boolean> awaitingNewId = new HashMap<>();

    private static final Pattern PAGE_PATTERN = Pattern.compile("Page\\s+(\\d+)\\s*/\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

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

        // Track list
        if (title.startsWith(TrackGui.LIST_TITLE_PREFIX)) {
            e.setCancelled(true);

            int page = parsePageIndex(title);

            int slot = e.getRawSlot();

            // Clicking a track entry (slots 0-44)
            if (slot >= 0 && slot <= 44) {
                ItemStack it = e.getCurrentItem();
                if (it == null || it.getItemMeta() == null) return;

                String name = ChatColor.stripColor(it.getItemMeta().getDisplayName());
                if (name == null || name.isBlank()) return;

                String id = name.toLowerCase();
                if (tracks.getTrack(id) != null) {
                    TrackGui.openEditor(p, tracks, id, visualizer);
                }
                return;
            }

            // Previous page
            if (slot == 45) {
                TrackGui.openList(p, tracks, page - 1);
                return;
            }

            // Create
            if (slot == 49) {
                awaitingNewId.put(p.getUniqueId(), true);
                p.closeInventory();
                p.sendMessage(ChatColor.GREEN + "Type a new track id in chat (letters/numbers/_/-). Type 'cancel' to abort.");
                return;
            }

            // Next page
            if (slot == 50) {
                TrackGui.openList(p, tracks, page + 1);
                return;
            }

            // Close
            if (slot == 53) {
                p.closeInventory();
                return;
            }

            return;
        }

        // Track editor
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
                case 11 -> {
                    // Give the point tool bound to this track
                    TrackPointToolListener.giveTool(plugin, p, id);
                    p.sendMessage(ChatColor.GOLD + "Point Tool given for track '" + id + "'.");
                }
                case 12 -> {
                    if (!tracks.undoLast(id)) p.sendMessage(ChatColor.RED + "No points to undo.");
                    else p.sendMessage(ChatColor.YELLOW + "Undid last point. Total: " + tracks.getTrack(id).size());
                    TrackGui.openEditor(p, tracks, id, visualizer);
                }
                case 14 -> TrackGui.openPoints(p, tracks, id, 0);

                case 15 -> {
                    if (!tracks.setWatchLocation(id, p.getLocation())) {
                        p.sendMessage(ChatColor.RED + "Couldn't set watch spot (wrong world?).");
                    } else {
                        p.sendMessage(ChatColor.LIGHT_PURPLE + "Set watch spot for '" + id + "'.");
                    }
                    TrackGui.openEditor(p, tracks, id, visualizer);
                }

                case 16 -> {
                    tracks.clearWatchLocation(id);
                    p.sendMessage(ChatColor.YELLOW + "Cleared watch spot for '" + id + "'.");
                    TrackGui.openEditor(p, tracks, id, visualizer);
                }

                case 20 -> {
                    visualizer.show(p, id);
                    p.sendMessage(ChatColor.AQUA + "Showing track '" + id + "'.");
                }
                case 21 -> {
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
                    TrackGui.openList(p, tracks, 0);
                }
                case 49 -> TrackGui.openList(p, tracks, 0);
                case 53 -> p.closeInventory();
            }

            return;
        }

        // Points list
        if (title.startsWith(TrackGui.POINTS_TITLE_PREFIX)) {
            e.setCancelled(true);

            String stripped = ChatColor.stripColor(title);

            // "Track Points: <id> (Page X/Y)"
            String id = extractPointsTrackId(stripped);
            if (id == null) {
                p.closeInventory();
                return;
            }

            MarbleTrack t = tracks.getTrack(id);
            if (t == null) {
                p.sendMessage(ChatColor.RED + "Track no longer exists.");
                p.closeInventory();
                return;
            }

            int page = parsePageIndex(title);
            int slot = e.getRawSlot();

            // Click point (0-44)
            if (slot >= 0 && slot <= 44) {
                ItemStack it = e.getCurrentItem();
                if (it == null || it.getItemMeta() == null) return;

                String dn = ChatColor.stripColor(it.getItemMeta().getDisplayName());
                if (dn == null) return;

                // "Point #N"
                int idx = parsePointIndex(dn);
                if (idx < 0) return;

                if (idx >= 0 && idx < t.getPoints().size()) {
                    Location loc = t.getPoints().get(idx);
                    p.teleport(loc);
                    p.sendMessage(ChatColor.AQUA + "Teleported to point #" + (idx + 1) + " on '" + id + "'.");
                }
                return;
            }

            if (slot == 45) {
                TrackGui.openPoints(p, tracks, id, page - 1);
                return;
            }

            if (slot == 49) {
                TrackGui.openEditor(p, tracks, id, visualizer);
                return;
            }

            if (slot == 50) {
                TrackGui.openPoints(p, tracks, id, page + 1);
                return;
            }

            if (slot == 53) {
                p.closeInventory();
                return;
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
            plugin.getServer().getScheduler().runTask(plugin, () -> TrackGui.openList(p, tracks, 0));
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
                TrackGui.openList(p, tracks, 0);
                return;
            }

            p.sendMessage(ChatColor.GREEN + "Created track '" + trackId + "'.");
            TrackGui.openEditor(p, tracks, trackId, visualizer);
        });
    }

    private int parsePageIndex(String title) {
        String stripped = ChatColor.stripColor(title);
        Matcher m = PAGE_PATTERN.matcher(stripped);
        if (!m.find()) return 0;
        try {
            int pageShown = Integer.parseInt(m.group(1)); // 1-based
            return Math.max(0, pageShown - 1);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String extractPointsTrackId(String strippedTitle) {
        // "Track Points: <id> (Page X/Y)"
        // remove prefix
        if (!strippedTitle.startsWith("Track Points: ")) return null;
        String rest = strippedTitle.substring("Track Points: ".length());
        int idxParen = rest.indexOf(" (");
        if (idxParen <= 0) return null;
        String id = rest.substring(0, idxParen).trim();
        if (id.isEmpty()) return null;
        return id.toLowerCase();
    }

    private int parsePointIndex(String displayName) {
        // "Point #N"
        String s = displayName.trim();
        int hash = s.indexOf('#');
        if (hash < 0) return -1;
        String num = s.substring(hash + 1).trim();
        try {
            int n = Integer.parseInt(num);
            return n - 1;
        } catch (Exception ignored) {
            return -1;
        }
    }
}
