package me.pattrick.marbledrop.races;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Ticks active MarbleRunner instances.
 *
 * Backwards compatible:
 * - If MarbleRunner has boolean tick(List<MarbleRunner> allRunners), we call that.
 * - Otherwise we call legacy boolean tick().
 *
 * This lets us add repulsion/wall-collision in MarbleRunner without breaking older runners.
 */
public final class MarbleRaceEngine {

    private final Plugin plugin;
    private final List<MarbleRunner> runners = new LinkedList<>();

    private BukkitTask task;

    // Cache reflection lookups per runner class to avoid repeated method scanning
    private final Map<Class<?>, Method> neighborTickCache = new HashMap<>();
    private final Set<Class<?>> noNeighborTick = new HashSet<>();

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
        runners.clear();
        neighborTickCache.clear();
        noNeighborTick.clear();
    }

    public void addRunner(MarbleRunner runner) {
        if (runner == null) return;
        runners.add(runner);
    }

    private void tick() {
        if (runners.isEmpty()) return;

        // Snapshot so each runner sees a consistent view of all runners for this tick
        final List<MarbleRunner> snapshot = new ArrayList<>(runners);

        Iterator<MarbleRunner> it = runners.iterator();
        while (it.hasNext()) {
            MarbleRunner runner = it.next();

            boolean alive = tickRunner(runner, snapshot);
            if (!alive) {
                it.remove();
            }
        }
    }

    private boolean tickRunner(MarbleRunner runner, List<MarbleRunner> snapshot) {
        // Prefer tick(List<MarbleRunner>) if runner provides it
        Class<?> cls = runner.getClass();

        Method m = neighborTickCache.get(cls);
        if (m == null && !noNeighborTick.contains(cls)) {
            m = findNeighborTick(cls);
            if (m != null) {
                neighborTickCache.put(cls, m);
            } else {
                noNeighborTick.add(cls);
            }
        }

        if (m != null) {
            try {
                Object out = m.invoke(runner, snapshot);
                if (out instanceof Boolean b) return b;
                return true; // if something weird, don't kill the runner
            } catch (Throwable t) {
                // If a runner's advanced tick fails, fall back to legacy tick() this tick
                try {
                    return runner.tick();
                } catch (Throwable ignored) {
                    return false;
                }
            }
        }

        // Legacy behavior
        return runner.tick();
    }

    private Method findNeighborTick(Class<?> cls) {
        try {
            // Must be exactly: boolean tick(List<MarbleRunner> runners)
            Method m = cls.getMethod("tick", List.class);
            if (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class) {
                m.setAccessible(true);
                return m;
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) {
        }
        return null;
    }
}
