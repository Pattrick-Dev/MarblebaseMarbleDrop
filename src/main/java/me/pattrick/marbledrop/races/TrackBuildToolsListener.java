package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.command.Commands;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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

/**
 * Handles right-clicks on every TrackCreationKit tool (point, undo,
 * preview, watch, finish), for both the ProtocolLib overlay path (fake
 * items, detected by held hotbar slot + tag) and the real-item fallback
 * (detected by material + exact display name).
 * <p>
 * Primary path (ProtocolLib installed): mirrors RaceBoostListener --
 * reads the raw client packet (USE_ITEM for air, USE_ITEM_ON for a
 * block) and cancels it before vanilla's own handler ever runs, which
 * avoids the forced held-slot resync that would otherwise immediately
 * overwrite the fake tool icon with whatever's really in that slot (the
 * player's real, untouched item). The Point Tool needs to know which
 * block was targeted; rather than parse that out of the raw packet
 * (version-fragile), the handler just re-raycasts server-side via
 * {@code getTargetBlockExact}, the same call the station listeners
 * elsewhere in this codebase already use.
 * <p>
 * Fallback path (ProtocolLib absent, or overlay null): plain
 * PlayerInteractEvent handling against a real item.
 * <p>
 * Which track a tool acts on is tracked as real server-side state on
 * TrackBuildInventoryManager (activeTrackId()) rather than read off the
 * item itself - there's no real item to read it from in the overlay
 * path, and only one build session is supported per player at a time
 * regardless of path, so nothing is lost by keying off the player alone.
 * <p>
 * The actual packet-reading logic lives in TrackBuildToolsPacketBridge,
 * a separate class this one only ever constructs from inside the
 * ProtocolLib-present branch below. This class itself must never contain
 * ProtocolLib-referencing bytecode anywhere (not even behind an if-guard)
 * - it's a real Bukkit Listener, unconditionally constructed and passed
 * to registerEvents() in Main, and that alone is enough to throw
 * NoClassDefFoundError on a server without ProtocolLib if the class
 * itself references ProtocolLib types anywhere in its own bytecode.
 */
public final class TrackBuildToolsListener implements Listener {

    private final Plugin plugin;
    private final TrackManager tracks;
    private final TrackVisualizer visualizer;
    private final TrackBuildInventoryManager buildInventory;
    private final RaceInventoryOverlay overlay;

    public TrackBuildToolsListener(Plugin plugin, TrackManager tracks, TrackVisualizer visualizer,
                                    TrackBuildInventoryManager buildInventory) {
        this.plugin = plugin;
        this.tracks = tracks;
        this.visualizer = visualizer;
        this.buildInventory = buildInventory;
        this.overlay = buildInventory.overlay();

        if (overlay != null && Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            new TrackBuildToolsPacketBridge(plugin, overlay, this::handle);
        }
    }

    /** Fallback only - see the class javadoc. Never fires for a tool click when the packet listener above already handled (and cancelled) it. */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.RIGHT_CLICK_AIR) return;

        TrackCreationKit.Tool tool = TrackCreationKit.Tool.matchReal(e.getItem());
        if (tool == null) return;

        e.setCancelled(true);
        handle(e.getPlayer(), tool);
    }

    private void handle(Player p, TrackCreationKit.Tool tool) {
        String trackId = buildInventory.activeTrackId(p);
        if (trackId == null) return; // not (or no longer) in a build session

        if (!Commands.requireAdmin(p)) return;
        if (tracks.getTrack(trackId) == null) {
            p.sendMessage(ChatColor.RED + "Track no longer exists: " + trackId);
            return;
        }

        switch (tool) {
            case POINT -> handlePoint(p, trackId);
            case UNDO -> handleUndo(p, trackId);
            case PREVIEW -> handlePreview(p, trackId);
            case WATCH -> handleWatch(p, trackId);
            case FINISH -> handleFinish(p, trackId);
        }
    }

    private void handlePoint(Player p, String trackId) {
        Block clicked = p.getTargetBlockExact(6);
        if (clicked == null) {
            p.sendMessage(ChatColor.RED + "Look at a block within 6 blocks to add a point there.");
            return;
        }

        Location loc = clicked.getLocation().add(0.5, 1.0, 0.5);
        loc.setYaw(p.getLocation().getYaw());
        loc.setPitch(p.getLocation().getPitch());

        if (!tracks.addPoint(trackId, loc)) {
            p.sendMessage(ChatColor.RED + "Failed to add point.");
            return;
        }

        int count = tracks.getTrack(trackId).size();
        p.sendMessage(ChatColor.GREEN + "Added point #" + count + " to '" + trackId + "'.");
    }

    private void handleUndo(Player p, String trackId) {
        if (!tracks.undoLast(trackId)) {
            p.sendMessage(ChatColor.RED + "No points to undo.");
        } else {
            p.sendMessage(ChatColor.YELLOW + "Undid last point. Total: " + tracks.getTrack(trackId).size());
        }
    }

    private void handlePreview(Player p, String trackId) {
        if (visualizer.isShowing(p)) {
            visualizer.hide(p);
        } else {
            visualizer.show(p, trackId);
        }
    }

    private void handleWatch(Player p, String trackId) {
        if (!tracks.setWatchLocation(trackId, p.getLocation())) {
            p.sendMessage(ChatColor.RED + "Couldn't set watch spot (wrong world?).");
        } else {
            p.sendMessage(ChatColor.LIGHT_PURPLE + "Set watch spot for '" + trackId + "'.");
        }
    }

    private void handleFinish(Player p, String trackId) {
        int count = tracks.getTrack(trackId).size();
        visualizer.hide(p);
        buildInventory.exit(p);

        p.sendMessage(ChatColor.GREEN + "Finished building '" + ChatColor.YELLOW + trackId
                + ChatColor.GREEN + "' - " + ChatColor.YELLOW + count + ChatColor.GREEN + " point(s).");
        p.sendMessage(ChatColor.GRAY + "Use " + ChatColor.AQUA + "/md track" + ChatColor.GRAY
                + " to set laps, enable auto-race, or make further changes.");
    }
}
