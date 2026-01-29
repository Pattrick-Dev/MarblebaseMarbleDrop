package me.pattrick.marbledrop.progression;

import me.pattrick.marbledrop.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class RecyclerAmbient {

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
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        for (UUID id : markerIds.values()) {
            ArmorStand as = getArmorStand(id);
            if (as != null && as.isValid()) as.remove();
        }
        markerIds.clear();
    }

    public void removeRecycler(Location recyclerLoc) {
        if (recyclerLoc == null) return;

        String key = recyclers.keyOf(recyclerLoc);
        UUID id = markerIds.remove(key);
        if (id != null) {
            ArmorStand as = getArmorStand(id);
            if (as != null && as.isValid()) as.remove();
        }

        Location standLoc = recyclerLoc.clone().add(0.5, getHologramYOffset(), 0.5);
        if (isChunkLoaded(standLoc)) {
            removeDuplicateHolograms(
                    standLoc.getWorld(),
                    standLoc,
                    getHologramName(),
                    null
            );
            ;
        }
    }

    private void tick() {
        Set<String> liveKeys = new HashSet<>();

        for (Location loc : recyclers.getRecyclers()) {
            World w = loc.getWorld();
            if (w == null) continue;

            String key = recyclers.keyOf(loc);
            liveKeys.add(key);

            Location base = loc.clone().add(0.5, 1.0, 0.5);
            Location standLoc = loc.clone().add(0.5, getHologramYOffset(), 0.5);

            // ✅ Chunk safety: do not spawn/replace holograms when the chunk is unloaded
            if (!isChunkLoaded(standLoc)) {
                continue;
            }

            double visibleRadius = getNameVisibleRadius();
            List<Player> nearby = getPlayersNear(base, visibleRadius);
            boolean active = !nearby.isEmpty();

            ensureMarker(key, standLoc, active);

            int ash = active ? 5 : 2;
            int crit = active ? 2 : 1;

            w.spawnParticle(Particle.ASH, base, ash, 0.20, 0.10, 0.20, 0.01);
            w.spawnParticle(Particle.CRIT, base, crit, 0.20, 0.10, 0.20, 0.08);

            if (active && (Bukkit.getCurrentTick() % 80 == 0)) {
                w.spawnParticle(Particle.BLOCK_CRUMBLE, base, 6, 0.18, 0.08, 0.18, 0.04, loc.getBlock().getBlockData());
            }

            if (active && (Bukkit.getCurrentTick() % 160 == 0)) {
                for (Player p : nearby) {
                    p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.18f, 0.95f);
                }
            }
        }

        // Remove markers for recyclers that no longer exist (only if we can see the entity)
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
        if (!isChunkLoaded(standLoc)) return;

        ArmorStand stand = null;
        UUID existingId = markerIds.get(key);

        if (existingId != null) {
            stand = getArmorStand(existingId);
            if (stand == null || !stand.isValid()) {
                markerIds.remove(key);
                stand = null;
            }
        }

        String holoName = getHologramName();

        if (stand == null) {
            // Prefer reusing an existing matching hologram to avoid duplicates.
            ArmorStand existing = findAnyHologramStand(standLoc.getWorld(), standLoc, holoName);
            if (existing != null) {
                stand = existing;
                markerIds.put(key, stand.getUniqueId());
            } else {
                stand = standLoc.getWorld().spawn(standLoc, ArmorStand.class, as -> {
                    as.setInvisible(true);
                    as.setMarker(true);
                    as.setGravity(false);
                    as.setSmall(true);
                    as.setInvulnerable(true);
                    as.setSilent(true);
                    as.setCustomName(holoName);
                    as.setCustomNameVisible(showName);
                });
                markerIds.put(key, stand.getUniqueId());
            }
        } else {
            if (!stand.getLocation().getWorld().equals(standLoc.getWorld())
                    || stand.getLocation().distanceSquared(standLoc) > 0.15) {
                stand.teleport(standLoc);
            }
        }

        stand.setCustomName(holoName);
        stand.setCustomNameVisible(showName);

        // ✅ Kill duplicates nearby (cleanup old bug)
        removeDuplicateHolograms(standLoc.getWorld(), standLoc, holoName, stand.getUniqueId());
    }

    private String getHologramName() {
        // default matches old behavior (but now supports & via MdConfig)
        if (plugin instanceof Main main) {
            String s = main.cfg().recyclerHologramName();
            if (s != null && !s.isBlank()) return s;
        }
        return ChatColor.DARK_GRAY + "✦ " + ChatColor.GOLD + "Marble Recycler" + ChatColor.DARK_GRAY + " ✦";
    }

    private double getHologramYOffset() {
        if (plugin instanceof Main main) {
            return main.cfg().recyclerHologramYOffset();
        }
        return 1.15;
    }

    private double getNameVisibleRadius() {
        if (plugin instanceof Main main) {
            return main.cfg().hologramNameRadius();
        }
        return 8.0;
    }

    private boolean isChunkLoaded(Location loc) {
        World w = loc.getWorld();
        if (w == null) return false;
        Chunk c = loc.getChunk();
        return c != null && c.isLoaded();
    }

    private ArmorStand findAnyHologramStand(World w, Location standLoc, String holoName) {
        if (w == null) return null;

        ArmorStand keep = null;
        for (Entity e : w.getNearbyEntities(standLoc, 0.6, 0.6, 0.6)) {
            if (!(e instanceof ArmorStand as)) continue;
            if (!as.isValid()) continue;

            if (holoName.equals(as.getCustomName()) && as.isMarker() && as.isInvisible()) {
                if (keep == null) {
                    keep = as;
                } else {
                    as.remove();
                }
            }
        }
        return keep;
    }

    private void removeDuplicateHolograms(World w, Location standLoc, String holoName) {
        removeDuplicateHolograms(w, standLoc, holoName, null);
    }

    private void removeDuplicateHolograms(World w, Location standLoc, String holoName, UUID keepId) {
        if (w == null) return;

        for (Entity e : w.getNearbyEntities(standLoc, 0.6, 0.6, 0.6)) {
            if (!(e instanceof ArmorStand as)) continue;
            if (!as.isValid()) continue;

            if (!holoName.equals(as.getCustomName())) continue;
            if (!as.isMarker() || !as.isInvisible()) continue;

            if (keepId != null && keepId.equals(as.getUniqueId())) continue;
            as.remove();
        }
    }

    private ArmorStand getArmorStand(UUID id) {
        if (id == null) return null;
        for (World w : Bukkit.getWorlds()) {
            Entity e = w.getEntity(id);
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
