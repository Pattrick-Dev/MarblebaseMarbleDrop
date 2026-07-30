package me.pattrick.marbledrop.races;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Ticks active MarbleRunner instances.
 * <p>
 * Runners are grouped by the TrackPhysics they share (one per race
 * session) so each session's dyn4j World is stepped exactly once per
 * tick no matter how many marbles are in it: apply every runner's drive
 * force, step that group's World once, then let every runner in that
 * group read its new position back.
 */
public final class MarbleRaceEngine {

    private static final double DT = 1.0 / 20.0; // seconds per Minecraft tick

    private final Plugin plugin;
    private final List<MarbleRunner> runners = new LinkedList<>();

    private BukkitTask task;

    public MarbleRaceEngine(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearAll();
    }

    public void addRunner(MarbleRunner runner) {
        if (runner == null) return;
        runners.add(runner);
    }

    /** Force-despawns every active runner right now (armor stand + physics body), without firing finish listeners. */
    public void clearAll() {
        for (MarbleRunner runner : runners) {
            try {
                runner.forceRemove();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "MarbleRaceEngine: failed to force-remove a runner during purge.", t);
            }
        }
        runners.clear();
    }

    /** Returns the average position of all active runners, or null if none. */
    public Location getCentroid() {
        if (runners.isEmpty()) return null;
        double x = 0, y = 0, z = 0;
        World world = null;
        int count = 0;
        for (MarbleRunner r : runners) {
            Location loc = r.getLocation();
            if (loc == null || loc.getWorld() == null) continue;
            world = loc.getWorld();
            x += loc.getX();
            y += loc.getY();
            z += loc.getZ();
            count++;
        }
        if (count == 0 || world == null) return null;
        return new Location(world, x / count, y / count, z / count);
    }

    private void tick() {
        if (runners.isEmpty()) return;

        try {
            Map<TrackPhysics, List<MarbleRunner>> groups = new LinkedHashMap<>();
            for (MarbleRunner runner : runners) {
                groups.computeIfAbsent(runner.getPhysics(), k -> new ArrayList<>()).add(runner);
            }

            List<MarbleRunner> finished = new ArrayList<>();
            for (Map.Entry<TrackPhysics, List<MarbleRunner>> group : groups.entrySet()) {
                List<MarbleRunner> members = group.getValue();

                try {
                    for (MarbleRunner runner : members) {
                        runner.applyDrive();
                    }

                    group.getKey().step(DT);

                    for (MarbleRunner runner : members) {
                        if (!runner.syncAfterStep()) {
                            finished.add(runner);
                        }
                    }
                } catch (Throwable t) {
                    // One race session's physics going unstable (e.g. an
                    // extreme pileup at high speed) must not freeze every
                    // other race -- or this whole repeating task, forever,
                    // since an uncaught exception here would otherwise
                    // silently cancel it -- so isolate the failure to just
                    // this session's runners and keep ticking the rest.
                    plugin.getLogger().log(Level.SEVERE,
                            "MarbleRaceEngine: a race session crashed mid-tick, force-removing its runners.", t);
                    for (MarbleRunner runner : members) {
                        try {
                            runner.forceRemove();
                        } catch (Throwable ignored) {
                        }
                    }
                    finished.addAll(members);
                }
            }

            runners.removeAll(finished);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "MarbleRaceEngine: tick failed unexpectedly.", t);
        }
    }
}
