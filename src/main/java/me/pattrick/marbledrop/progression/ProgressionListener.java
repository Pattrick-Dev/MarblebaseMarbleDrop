package me.pattrick.marbledrop.progression;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProgressionListener implements Listener {

    private final TaskManager taskManager;

    // movement tracking to avoid per-tick heavy math
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final Map<UUID, Long> lastMoveSampleMs = new HashMap<>();

    // sample every 1500ms, ignore tiny wiggles, only count if moved >= ~1.5 blocks
    private static final long MOVE_SAMPLE_MS = 1500L;
    private static final double MIN_DIST_SQUARED = 2.25; // 1.5^2

    public ProgressionListener(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Ensure reset keys are set up when they join
        taskManager.ensureResets(e.getPlayer());

        // Initialize movement baseline
        lastLocation.put(e.getPlayer().getUniqueId(), e.getPlayer().getLocation());
        lastMoveSampleMs.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return; // optional: no progress in creative
        taskManager.increment(p, TaskTrigger.BREAK_BLOCKS, 1);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        if (killer.getGameMode() == GameMode.CREATIVE) return; // optional
        taskManager.increment(killer, TaskTrigger.KILL_MOBS, 1);
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE) return; // optional
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Entity caught = e.getCaught();
        if (caught == null) return;

        taskManager.increment(e.getPlayer(), TaskTrigger.FISH_CAUGHT, 1);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.SPECTATOR) return;

        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();

        Long lastMs = lastMoveSampleMs.get(id);
        if (lastMs != null && (now - lastMs) < MOVE_SAMPLE_MS) return;

        Location prev = lastLocation.get(id);
        Location cur = p.getLocation();
        if (prev == null) {
            lastLocation.put(id, cur);
            lastMoveSampleMs.put(id, now);
            return;
        }

        // must be same world to compare
        if (prev.getWorld() == null || cur.getWorld() == null || !prev.getWorld().equals(cur.getWorld())) {
            lastLocation.put(id, cur);
            lastMoveSampleMs.put(id, now);
            return;
        }

        double dx = cur.getX() - prev.getX();
        double dy = cur.getY() - prev.getY();
        double dz = cur.getZ() - prev.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq < MIN_DIST_SQUARED) {
            // update sample time but keep lastLocation so they must actually move further next time
            lastMoveSampleMs.put(id, now);
            return;
        }

        // Convert to approx blocks walked (rounded)
        int blocks = (int) Math.round(Math.sqrt(distSq));
        if (blocks > 0) {
            taskManager.increment(p, TaskTrigger.WALK_DISTANCE_BLOCKS, blocks);
        }

        lastLocation.put(id, cur);
        lastMoveSampleMs.put(id, now);
    }
}
