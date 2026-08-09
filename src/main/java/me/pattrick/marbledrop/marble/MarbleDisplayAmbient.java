package me.pattrick.marbledrop.marble;

import me.pattrick.marbledrop.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-marble hologram + particle ticker for placed marble displays --
 * mirrors InfusionTableAmbient's marker/chunk-safety/duplicate-cleanup
 * shape, but keyed per placed marble (not one shared name) since each
 * marble's hologram text and on/off toggles come from its own block PDC
 * (see MarbleDisplayMenu).
 */
public final class MarbleDisplayAmbient {

    private static final double STAND_Y_OFFSET = 0.75;

    private final Plugin plugin;
    private final PlacedMarbleManager marbles;

    // Tags every armor stand we spawn so it can be positively identified
    // as "ours" (by findAnyHologramStand/removeDuplicateHolograms/
    // sweepOrphans) regardless of what its display name currently says --
    // the name alone isn't a safe fingerprint since SHOW_RARITY can change
    // it at any time (and InfusionTable/Recycler/UpgradeStation spawn
    // their own isMarker+isInvisible stands too).
    private final NamespacedKey markerTag;

    private BukkitTask task;

    private final Map<String, UUID> markerIds = new HashMap<>();

    public MarbleDisplayAmbient(Plugin plugin, PlacedMarbleManager marbles) {
        this.plugin = plugin;
        this.marbles = marbles;
        this.markerTag = new NamespacedKey(plugin, "marble_display_hologram");
    }

    public void start() {
        stop();
        sweepOrphans();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    /**
     * One-time startup pass that removes any hologram matching our own
     * name format but sitting at a location no longer in the registry --
     * e.g. a marble placed/broken under an older build (before this
     * ambient ticker, or before removeMarker()'s physical cleanup below,
     * existed) can leave a stray armor stand nothing will ever adopt or
     * clean up on its own, since tick() only ever looks at currently
     * registered locations.
     */
    private void sweepOrphans() {
        Set<String> validKeys = new HashSet<>();
        for (Location loc : marbles.getMarbles()) {
            Location standLoc = loc.clone().add(0.5, STAND_Y_OFFSET, 0.5);
            validKeys.add(blockKey(standLoc));
        }

        for (World w : Bukkit.getWorlds()) {
            for (Entity e : new ArrayList<>(w.getEntities())) {
                if (!(e instanceof ArmorStand as)) continue;
                if (!as.isValid() || !isOurs(as)) continue;

                if (!validKeys.contains(blockKey(as.getLocation()))) {
                    as.remove();
                }
            }
        }
    }

    private boolean isOurs(ArmorStand as) {
        return as.isMarker() && as.isInvisible()
                && as.getPersistentDataContainer().has(markerTag, PersistentDataType.BYTE);
    }

    private String blockKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        for (UUID id : markerIds.values()) {
            ArmorStand as = getArmorStand(id);
            if (as != null && as.isValid()) {
                as.remove();
            }
        }
        markerIds.clear();
    }

    /** Called on break to clean up that marble's hologram immediately. */
    public void removeMarker(Location loc) {
        String key = marbles.keyOf(loc);
        UUID id = markerIds.remove(key);
        if (id != null) {
            ArmorStand as = getArmorStand(id);
            if (as != null && as.isValid()) {
                as.remove();
            }
        }

        // Also sweep the spot directly instead of trusting markerIds alone --
        // if this marble was broken before tick() ever got a chance to adopt
        // it into markerIds (e.g. right after a restart), the tracked lookup
        // above finds nothing and the hologram would otherwise be orphaned
        // forever, since removed-from-the-registry locations are never
        // revisited by tick() again.
        Location standLoc = loc.clone().add(0.5, STAND_Y_OFFSET, 0.5);
        if (isChunkLoaded(standLoc)) {
            removeDuplicateHolograms(standLoc.getWorld(), standLoc, null);
        }
    }

    private void tick() {
        final Main main = (plugin instanceof Main m) ? m : null;
        final double nameRadius = (main != null) ? main.cfg().hologramNameRadius() : 8.0;

        Set<String> seenKeys = new HashSet<>();

        for (Location loc : marbles.getMarbles()) {
            World w = loc.getWorld();
            if (w == null) continue;

            String key = marbles.keyOf(loc);
            seenKeys.add(key);

            Location standLoc = loc.clone().add(0.5, STAND_Y_OFFSET, 0.5);
            if (!isChunkLoaded(standLoc)) continue;

            BlockState state = loc.getBlock().getState();
            if (!(state instanceof Skull skull)) {
                removeMarker(loc);
                continue;
            }

            PersistentDataContainer pdc = skull.getPersistentDataContainer();
            MarbleData data = MarbleItem.readFromContainer(pdc);
            if (data == null) {
                // Manager is stale (the block was replaced without going
                // through MarblePlacementListener's onBreak).
                removeMarker(loc);
                marbles.removeMarble(loc);
                continue;
            }

            boolean showHologram = MarbleDisplayMenu.isHologramEnabled(pdc);
            boolean showParticles = MarbleDisplayMenu.isParticlesEnabled(pdc);

            if (showHologram) {
                boolean nearby = !w.getNearbyEntities(loc.clone().add(0.5, 1, 0.5), nameRadius, nameRadius, nameRadius).isEmpty();
                ensureMarker(loc, standLoc, key, holoName(data, pdc), nearby);
            } else {
                UUID id = markerIds.remove(key);
                if (id != null) {
                    ArmorStand as = getArmorStand(id);
                    if (as != null && as.isValid()) as.remove();
                }
            }

            if (showParticles) {
                Location center = loc.clone().add(0.5, 0.4, 0.5);
                MarbleDisplayMenu.styleOf(pdc).spawn(w, center, data.getRarity());
            }
        }

        // Clean up markers for marbles that no longer exist in the registry.
        markerIds.entrySet().removeIf(entry -> {
            if (seenKeys.contains(entry.getKey())) return false;
            ArmorStand as = getArmorStand(entry.getValue());
            if (as != null && as.isValid()) as.remove();
            return true;
        });
    }

    private String holoName(MarbleData data, PersistentDataContainer pdc) {
        String placedName = pdc.get(MarbleKeys.PLACED_NAME, PersistentDataType.STRING);

        MarbleRarity rarity = data.getRarity() == null ? MarbleRarity.COMMON : data.getRarity();
        String color = MarbleItem.rarityColor(rarity);

        String label = (placedName != null && !placedName.isBlank()) ? ChatColor.stripColor(placedName) : "Marble";
        if (!MarbleDisplayMenu.isRarityEnabled(pdc)) {
            return color + label;
        }
        return color + label + ChatColor.GRAY + " (" + rarity.name() + ")";
    }

    private void ensureMarker(Location markerKeyLoc, Location standLoc, String key, String name, boolean showName) {
        if (!isChunkLoaded(standLoc)) return;

        ArmorStand stand = null;
        UUID id = markerIds.get(key);

        if (id != null) {
            stand = getArmorStand(id);
            if (stand == null || !stand.isValid()) {
                markerIds.remove(key);
                stand = null;
            }
        }

        if (stand == null) {
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
                    as.setCustomName(name);
                    as.setCustomNameVisible(showName);
                    as.getPersistentDataContainer().set(markerTag, PersistentDataType.BYTE, (byte) 1);
                });
                markerIds.put(key, stand.getUniqueId());
            }
        } else if (!stand.getLocation().getWorld().equals(standLoc.getWorld())
                || stand.getLocation().distanceSquared(standLoc) > 0.15) {
            stand.teleport(standLoc);
        }

        stand.setCustomName(name);
        stand.setCustomNameVisible(showName);

        // Unconditionally (re-)tag on every pass, not just at spawn time --
        // this self-heals stands that were adopted via findAnyHologramStand
        // from before this tag existed, so sweepOrphans() recognizes them too.
        stand.getPersistentDataContainer().set(markerTag, PersistentDataType.BYTE, (byte) 1);

        if (stand.getEquipment() != null) {
            stand.getEquipment().setHelmet(null);
        }

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

        ArmorStand keep = null;
        for (Entity e : w.getNearbyEntities(standLoc, 0.6, 0.6, 0.6)) {
            if (!(e instanceof ArmorStand as)) continue;
            if (!as.isValid()) continue;
            if (as.isMarker() && as.isInvisible() && as.getCustomName() != null) {
                if (keep == null) {
                    keep = as;
                } else {
                    as.remove();
                }
            }
        }
        return keep;
    }

    private void removeDuplicateHolograms(World w, Location standLoc, UUID keepId) {
        if (w == null) return;

        for (Entity e : w.getNearbyEntities(standLoc, 0.6, 0.6, 0.6)) {
            if (!(e instanceof ArmorStand as)) continue;
            if (!as.isValid()) continue;
            if (!as.isMarker() || !as.isInvisible() || as.getCustomName() == null) continue;
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
}
