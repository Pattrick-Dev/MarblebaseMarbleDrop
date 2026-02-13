package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.marble.MarbleItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public final class RaceSignListener implements Listener {

    private final TrackManager tracks;
    private final RaceManager races;
    private final RaceSignManager signManager;
    private final RaceWatchManager watch;

    public RaceSignListener(TrackManager tracks, RaceManager races, RaceSignManager signManager, RaceWatchManager watch) {
        this.tracks = tracks;
        this.races = races;
        this.signManager = signManager;
        this.watch = watch;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        String l0 = strip(e.getLine(0));
        if (!l0.equalsIgnoreCase("[race]")) return;

        if (!p.hasPermission("marbledrop.admin")) {
            p.sendMessage(ChatColor.RED + "You don't have permission to create race signs.");
            return;
        }

        String trackId = strip(e.getLine(1));
        String action = strip(e.getLine(2));

        if (trackId.isBlank()) trackId = "menu";
        if (action.isBlank()) action = "menu";

        trackId = trackId.toLowerCase();
        action = action.toLowerCase();

        if (!isValidAction(action)) {
            p.sendMessage(ChatColor.RED + "Invalid action. Use: join, leave, watch, open, close, start, menu");
            action = "menu";
        }

        if (!trackId.equals("menu")) {
            MarbleTrack t = tracks.getTrack(trackId);
            if (t == null || t.size() < 2) {
                p.sendMessage(ChatColor.RED + "Unknown trackId '" + trackId + "'. Sign will be set to menu.");
                trackId = "menu";
                action = "menu";
            }
        } else {
            action = "menu";
        }

        e.setLine(0, ChatColor.DARK_PURPLE + "[Race]");
        e.setLine(1, ChatColor.YELLOW + (trackId.equals("menu") ? "Menu" : trackId));
        e.setLine(2, ChatColor.AQUA + action);

        signManager.set(e.getBlock().getLocation(), trackId, action);

        p.sendMessage(ChatColor.GREEN + "Race sign created: " + ChatColor.YELLOW + trackId + ChatColor.GRAY + " / " + ChatColor.AQUA + action);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        if (!isSignBlock(b.getType())) return;

        var rs = signManager.get(b.getLocation());
        if (rs == null) return;

        e.setCancelled(true);

        Player p = e.getPlayer();
        if (p == null) return;

        String trackId = rs.trackId();
        String action = rs.action();

        if (action.equals("menu") || trackId.equals("menu")) {
            RaceGui.open(p, tracks, races);
            return;
        }

        if (action.equals("open") || action.equals("close") || action.equals("start")) {
            if (!p.hasPermission("marbledrop.admin")) {
                p.sendMessage(ChatColor.RED + "You don't have permission.");
                return;
            }

            switch (action) {
                case "open" -> races.open(p, trackId);
                case "close" -> races.close(p, trackId);
                case "start" -> races.start(p, trackId);
            }
            return;
        }

        switch (action) {
            case "join" -> {
                ItemStack held = p.getInventory().getItemInMainHand();
                if (held == null || held.getType().isAir() || !MarbleItem.isMarble(held)) {
                    p.sendMessage(ChatColor.RED + "Hold a marble in your main hand first.");
                    return;
                }
                races.enter(p, trackId, held);
            }
            case "leave" -> races.leave(p, trackId);
            case "watch" -> watch.start(p, trackId); // toggle feels best on signs
            default -> RaceGui.open(p, tracks, races);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b == null) return;

        if (!isSignBlock(b.getType())) return;
        if (!signManager.isRaceSign(b.getLocation())) return;

        Player p = e.getPlayer();
        if (p != null && !p.hasPermission("marbledrop.admin")) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You can't break race signs.");
            return;
        }

        signManager.remove(b.getLocation());
    }

    private boolean isValidAction(String a) {
        return a.equals("join") || a.equals("leave") || a.equals("watch")
                || a.equals("open") || a.equals("close") || a.equals("start") || a.equals("menu");
    }

    private boolean isSignBlock(Material m) {
        if (m == null) return false;
        String n = m.name();
        return n.endsWith("_SIGN") || n.endsWith("_WALL_SIGN");
    }

    private String strip(String s) {
        if (s == null) return "";
        return ChatColor.stripColor(s).trim();
    }
}
