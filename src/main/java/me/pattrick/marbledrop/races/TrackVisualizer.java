package me.pattrick.marbledrop.races;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TrackVisualizer {

    private final Plugin plugin;
    private final TrackManager tracks;

    // per-player active visualizations
    private final Map<UUID, BukkitTask> active = new HashMap<>();

    public TrackVisualizer(Plugin plugin, TrackManager tracks) {
        this.plugin = plugin;
        this.tracks = tracks;
    }

    public void show(Player player, String trackId) {
        hide(player);

        MarbleTrack track = tracks.getTrack(trackId);
        if (track == null) {
            player.sendMessage("§cTrack not found.");
            return;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            drawTrack(player, track);
        }, 0L, 5L); // every 5 ticks

        active.put(player.getUniqueId(), task);

        player.sendMessage("§aShowing track §e" + trackId);
    }

    /** True if this player currently has a track preview running (any track). */
    public boolean isShowing(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    public void hide(Player player) {
        BukkitTask task = active.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            player.sendMessage("§7Track visualization hidden.");
        }
    }

    private void drawTrack(Player player, MarbleTrack track) {
        List<Location> pts = track.getPoints();
        if (pts.size() < 2) return;

        for (int i = 0; i < pts.size() - 1; i++) {
            drawSegment(player, pts.get(i), pts.get(i + 1));
        }
    }

    private void drawSegment(Player player, Location a, Location b) {
        Vector dir = b.toVector().subtract(a.toVector());
        double length = dir.length();
        dir.normalize();

        double step = 0.3; // 👈 matches your marble spacing goal
        int points = (int) (length / step);

        for (int i = 0; i <= points; i++) {
            Vector offset = dir.clone().multiply(i * step);
            Location loc = a.clone().add(offset);

            player.spawnParticle(
                    Particle.END_ROD,
                    loc,
                    1,
                    0, 0, 0,
                    0
            );
        }
    }

    public void shutdown() {
        active.values().forEach(BukkitTask::cancel);
        active.clear();
    }
}
