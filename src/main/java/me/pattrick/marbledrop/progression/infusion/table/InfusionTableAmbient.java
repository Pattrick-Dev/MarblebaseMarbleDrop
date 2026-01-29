package me.pattrick.marbledrop.progression.infusion.table;

import me.pattrick.marbledrop.progression.DustManager;
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

public final class InfusionTableAmbient {

    private static final String HOLO_NAME =
            ChatColor.DARK_PURPLE + "✦ " + ChatColor.LIGHT_PURPLE + "Infusion Cauldron" + ChatColor.DARK_PURPLE + " ✦";

    private final Plugin plugin;
    private final InfusionTableManager tables;
    private final DustManager dust;

    private BukkitTask task;

    // Track marker stands by table key
    private final Map<String, UUID> markerIds = new HashMap<>();

    public InfusionTableAmbient(Plugin plugin, InfusionTableManager tables, DustManager dust) {
        this.plugin = plugin;
        this.tables = tables;
        this.dust = dust;
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

        // Remove ALL markers we can currently see
        for (UUID id : markerIds.values()) {
            ArmorStand as = getArmorStand(id);
            if (as != null && as.isValid()) {
                as.remove();
            }
        }
        markerIds.clear();
    }

    /**
     * Called when a table is removed to clean up visuals immediately.
     */
    public void removeTable(Location loc) {
        String key = tables.keyOf(loc);
        UUID id = markerIds.remove(key);
        if (id == null) return;

        ArmorStand as = getArmorStand(id);
        if (as != null && as.isValid()) {
            as.remove();
        }

        // Also remove any duplicates near the spot (if chunk loaded)
        Location standLoc = loc.clone().add(0.5, 1.25, 0.5);
        if (isChunkLoaded(standLoc)) {
            removeDuplicateHolograms(standLoc.getWorld(), standLoc);
        }
    }

    private void tick() {
        for (Location loc : tables.getTables()) {
            World w = loc.getWorld();
            if (w == null) continue;

            Location base = loc.clone().add(0.5, 1.0, 0.5);
            Location standLoc = loc.clone().add(0.5, 1.25, 0.5);

            // ✅ If chunk is not loaded, DO NOT spawn/replace holograms.
            // This is the #1 cause of duplicate armor stands.
            if (!isChunkLoaded(standLoc)) {
                continue;
            }

            List<Player> nearby = getPlayersNear(base, 8.0);
            boolean active = !nearby.isEmpty();

            boolean energized = false;
            if (active) {
                for (Player p : nearby) {
                    if (dust.getDust(p) > 0) {
                        energized = true;
                        break;
                    }
                }
            }

            ensureMarker(loc, standLoc, active);

            int enchantCount = active ? (energized ? 10 : 6) : 2;
            int portalCount  = active ? (energized ? 6 : 3) : 1;

            w.spawnParticle(Particle.ENCHANT, base, enchantCount, 0.22, 0.22, 0.22, 0.02);
            w.spawnParticle(Particle.PORTAL, base, portalCount, 0.18, 0.18, 0.18, 0.02);

            if (energized) {
                w.spawnParticle(Particle.WITCH, base, 2, 0.18, 0.18, 0.18, 0.01);
            }

            if (active && (Bukkit.getCurrentTick() % 120 == 0)) {
                float pitch = energized ? 1.35f : 1.15f;
                for (Player p : nearby) {
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, pitch);
                }
            }
        }
    }

    private void ensureMarker(Location tableLoc, Location standLoc, boolean showName) {
        String key = tables.keyOf(tableLoc);

        // chunk safety
        if (!isChunkLoaded(standLoc)) return;

        ArmorStand stand = null;
        UUID id = markerIds.get(key);

        if (id != null) {
            stand = getArmorStand(id);

            // IMPORTANT: if stand == null here, it might just be unloaded in some edge cases.
            // Since we already checked chunk loaded, null should mean "actually gone".
            if (stand == null || !stand.isValid()) {
                markerIds.remove(key);
                stand = null;
            }
        }

        if (stand == null) {
            // ✅ Before spawning, dedupe any old duplicates (from past bug)
            ArmorStand existing = findAnyHologramStand(standLoc.getWorld(), standLoc);
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
                    as.setCustomName(HOLO_NAME);
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

        // keep name state updated
        stand.setCustomName(HOLO_NAME);
        stand.setCustomNameVisible(showName);

        // HARD guarantee: no helmet ever
        if (stand.getEquipment() != null) {
            stand.getEquipment().setHelmet(null);
        }

        // ✅ Kill any duplicates that might still exist
        removeDuplicateHolograms(standLoc.getWorld(), standLoc, stand.getUniqueId());
    }

    private boolean isChunkLoaded(Location loc) {
        World w = loc.getWorld();
        if (w == null) return false;
        Chunk c = loc.getChunk();
        return c != null && c.isLoaded();
    }

    private ArmorStand findAnyHologramStand(World w, Location standLoc) {
        if (w == null) return null;

        // Remove duplicates, keep first found
        ArmorStand keep = null;
        for (Entity e : w.getNearbyEntities(standLoc, 0.6, 0.6, 0.6)) {
            if (!(e instanceof ArmorStand as)) continue;
            if (!as.isValid()) continue;

            if (HOLO_NAME.equals(as.getCustomName()) && as.isMarker() && as.isInvisible()) {
                if (keep == null) {
                    keep = as;
                } else {
                    as.remove();
                }
            }
        }
        return keep;
    }

    private void removeDuplicateHolograms(World w, Location standLoc) {
        removeDuplicateHolograms(w, standLoc, null);
    }

    private void removeDuplicateHolograms(World w, Location standLoc, UUID keepId) {
        if (w == null) return;

        for (Entity e : w.getNearbyEntities(standLoc, 0.6, 0.6, 0.6)) {
            if (!(e instanceof ArmorStand as)) continue;
            if (!as.isValid()) continue;

            if (!HOLO_NAME.equals(as.getCustomName())) continue;
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
        if (loc.getWorld() == null) return Collections.emptyList();

        double r2 = radius * radius;
        List<Player> out = new ArrayList<>();
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= r2) {
                out.add(p);
            }
        }
        return out;
    }
}
