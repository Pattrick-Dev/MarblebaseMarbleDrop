package me.pattrick.marbledrop.races;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class TrackPointToolListener implements Listener {

    private static final String PERM = "marbledrop.admin";
    private static final String TOOL_NAME = ChatColor.GOLD + "Track Point Tool";

    private final Plugin plugin;
    private final TrackManager tracks;

    private final NamespacedKey keyTrackId;

    public TrackPointToolListener(Plugin plugin, TrackManager tracks) {
        this.plugin = plugin;
        this.tracks = tracks;
        this.keyTrackId = new NamespacedKey(plugin, "track_point_tool_id");
    }

    public static void giveTool(Plugin plugin, Player p, String trackId) {
        if (plugin == null || p == null || trackId == null || trackId.isBlank()) return;

        NamespacedKey key = new NamespacedKey(plugin, "track_point_tool_id");

        ItemStack it = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TOOL_NAME);
            meta.setLore(List.of(
                    ChatColor.GRAY + "Track: " + ChatColor.YELLOW + trackId.toLowerCase(),
                    ChatColor.GRAY + "Right-click a block to add a point there",
                    ChatColor.DARK_GRAY + "Admin tool"
            ));

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(key, PersistentDataType.STRING, trackId.toLowerCase());

            it.setItemMeta(meta);
        }

        p.getInventory().addItem(it);
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        // Only respond to main-hand to avoid double-firing
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player p = e.getPlayer();
        if (p == null) return;

        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK) return; // must be a clicked block for a precise point

        ItemStack held = e.getItem();
        if (held == null || held.getType() != Material.BLAZE_ROD) return;

        ItemMeta meta = held.getItemMeta();
        if (meta == null) return;

        if (meta.getDisplayName() == null || !meta.getDisplayName().equals(TOOL_NAME)) return;

        if (!p.hasPermission(PERM)) {
            p.sendMessage(ChatColor.RED + "You don't have permission to use this.");
            return;
        }

        String trackId = meta.getPersistentDataContainer().get(keyTrackId, PersistentDataType.STRING);
        if (trackId == null || trackId.isBlank()) {
            p.sendMessage(ChatColor.RED + "This tool isn't bound to a track.");
            return;
        }

        MarbleTrack t = tracks.getTrack(trackId);
        if (t == null) {
            p.sendMessage(ChatColor.RED + "Track no longer exists: " + trackId);
            return;
        }

        if (!t.getWorld().equals(p.getWorld())) {
            p.sendMessage(ChatColor.RED + "You're in the wrong world for track '" + trackId + "'.");
            return;
        }

        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        Location loc = clicked.getLocation().add(0.5, 1.0, 0.5);
        loc.setYaw(p.getLocation().getYaw());
        loc.setPitch(p.getLocation().getPitch());

        boolean ok = tracks.addPoint(trackId, loc);
        if (!ok) {
            p.sendMessage(ChatColor.RED + "Failed to add point.");
            return;
        }

        int count = tracks.getTrack(trackId).size();
        p.sendMessage(ChatColor.GREEN + "Added point #" + count + " to '" + trackId + "'.");

        // Prevent normal interaction (e.g., opening containers) while using the tool
        e.setCancelled(true);
    }
}
