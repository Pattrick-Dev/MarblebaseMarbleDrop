package me.pattrick.marbledrop.progression;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class RecyclerAmbient {

    private static final String HOLO_NAME =
            ChatColor.DARK_GRAY + "✦ " + ChatColor.GOLD + "Marble Recycler" + ChatColor.DARK_GRAY + " ✦";

    private final Plugin plugin;
    private final MarbleRecyclerManager recyclers;

    private BukkitTask task;

    // Track hologram stands by recycler key
    private final Map<String, UUID> markerIds = new HashMap<>();

    public RecyclerAmbient(Plugin plugin, MarbleRecyclerManager recyclers) {
        this.plugin = plugin;
        this.recyclers = recyclers;
    }

    public void start() {
        stop();
        // 10 ticks = 0.5s (keep it light)
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        // Clean up all markers
        for (UUID id : markerIds.values()) {
            ArmorStand as = getArmorStand(id);
            if (as != null && as.isValid()) as.remove();
        }
        markerIds.clear();
    }

    /**
     * Call this when a recycler is removed so the hologram disappears immediately.
     * (Still safe even if called when no marker exists.)
     */
    public void removeRecycler(Location recyclerLoc) {
        if (recyclerLoc == null) return;

        String key = recyclers.keyOf(recyclerLoc);
        UUID id = markerIds.remove(key);
        if (id == null) return;

        ArmorStand as = getArmorStand(id);
        if (as != null && as.isValid()) as.remove();
    }

    private void tick() {
        // Current set of recycler keys (used to delete stale holograms)
        Set<String> liveKeys = new HashSet<>();

        for (Location loc : recyclers.getRecyclers()) {
            World w = loc.getWorld();
            if (w == null) continue;

            String key = recyclers.keyOf(loc);
            liveKeys.add(key);

            // Effects / hologram positions
            Location base = loc.clone().add(0.5, 1.0, 0.5);
            Location standLoc = loc.clone().add(0.5, 1.15, 0.5);

            // Only show name / play sound if players are nearby
            List<Player> nearby = getPlayersNear(base, 8.0);
            boolean active = !nearby.isEmpty();

            ensureMarker(key, standLoc, active);

            // Particles (mechanical / dusty, subtle)
            int ash = active ? 5 : 2;
            int crit = active ? 2 : 1;

            w.spawnParticle(Particle.ASH, base, ash, 0.20, 0.10, 0.20, 0.01);
            w.spawnParticle(Particle.CRIT, base, crit, 0.20, 0.10, 0.20, 0.08);

            // Rare crumble (very light, only when active)
            if (active && (Bukkit.getCurrentTick() % 80 == 0)) {
                // You said your server has BLOCK_CRUMBLE (not BLOCK_CRACK)
                w.spawnParticle(Particle.BLOCK_CRUMBLE, base, 6, 0.18, 0.08, 0.18, 0.04, loc.getBlock().getBlockData());
            }

            // Occasional ambient grind sound (only if active)
            if (active && (Bukkit.getCurrentTick() % 160 == 0)) {
                for (Player p : nearby) {
                    p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.18f, 0.95f);
                }
            }
        }

        // Remove markers for recyclers that no longer exist (file changed / removed, etc.)
        if (!markerIds.isEmpty()) {
            Iterator<Map.Entry<String, UUID>> it = markerIds.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, UUID> entry = it.next();
                if (liveKeys.contains(entry.getKey())) continue;

                ArmorStand as = getArmorStand(entry.getValue());
                if (as != null && as.isValid()) as.remove();
                it.remove();
            }
        }
    }

    private void ensureMarker(String key, Location standLoc, boolean showName) {
        ArmorStand stand = null;
        UUID existingId = markerIds.get(key);

        if (existingId != null) {
            stand = getArmorStand(existingId);
            if (stand == null || !stand.isValid()) {
                markerIds.remove(key);
                stand = null;
            }
        }

        if (stand == null) {
            World w = standLoc.getWorld();
            if (w == null) return;

            stand = w.spawn(standLoc, ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setMarker(true);
                as.setGravity(false);
                as.setSmall(true);
                as.setInvulnerable(true);
                as.setSilent(true);

                as.setCustomName(HOLO_NAME);
                as.setCustomNameVisible(showName);

                // IMPORTANT: no helmet / no displayed item
            });

            markerIds.put(key, stand.getUniqueId());
            return;
        }

        // Keep it in place
        if (!stand.getLocation().getWorld().equals(standLoc.getWorld())
                || stand.getLocation().distanceSquared(standLoc) > 0.15) {
            stand.teleport(standLoc);
        }

        stand.setCustomName(HOLO_NAME);
        stand.setCustomNameVisible(showName);
    }

    private ArmorStand getArmorStand(UUID id) {
        if (id == null) return null;
        for (World w : Bukkit.getWorlds()) {
            var e = w.getEntity(id);
            if (e instanceof ArmorStand as) return as;
        }
        return null;
    }

    private List<Player> getPlayersNear(Location loc, double radius) {
        World w = loc.getWorld();
        if (w == null) return Collections.emptyList();

        double r2 = radius * radius;
        List<Player> out = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= r2) {
                out.add(p);
            }
        }
        return out;
    }
}
