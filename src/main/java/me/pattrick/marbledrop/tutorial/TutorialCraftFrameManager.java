package me.pattrick.marbledrop.tutorial;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import me.pattrick.marbledrop.progression.StationRecipes;
import me.pattrick.marbledrop.progression.StationType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the physical 3x3 item frame "what to craft" display for the
 * tutorial's CRAFT step, using a private, per-player copy of the 9 frames
 * (hidden from everyone else) so two players on different recipes at the
 * same physical spot each see the correct one -- the same
 * setVisibleByDefault + showEntity/hideEntity trick already used elsewhere
 * in the tutorial (the sheep in TutorialTasksHandler, TutorialVisibility).
 * <p>
 * The admin's real, permanently-placed frames are pure anchors: they exist
 * only to mark the facing/location this class reads, and are expected to
 * stay EMPTY forever. Setup (setupFromSelection) also sets them invisible
 * via vanilla's own item-frame "invisible" flag (ItemFrame#setVisible,
 * global -- not a per-player hideEntity trick), so no wooden border ever
 * shows and only our private ItemDisplay icon is ever visible. Nothing
 * here ever reads or writes their contents, and -- unlike an earlier
 * version of this class -- nothing here ever hides or shows them via
 * per-player hideEntity/showEntity either. That bookkeeping was the source
 * of a real bug: Bukkit's per-player hidden-entity state lives on the
 * client connection, not in any flag we can keep in sync, so a relog or
 * plugin reload could silently desync our "already hidden" assumption from
 * what the client actually had rendered. The global invisible flag has no
 * such per-client state to desync.
 * <p>
 * The private copies are {@link ItemDisplay} entities, not real
 * ItemFrames: vanilla refuses to place a second hanging entity on a block
 * face that already has one (the same rule that stops a player placing two
 * item frames on the same wall spot in survival), so spawning an actual
 * ItemFrame copy directly on top of the still-present real one gets
 * rejected -- the copy is immediately discarded and its item pops out as a
 * dropped item. An ItemDisplay isn't a Hanging entity, so it has no such
 * placement/support check and coexists fine at the exact same spot; it's
 * positioned and rotated (see {@link #transformationFor(BlockFace)}) to sit
 * flush against the wall like the real frame, using the vanilla "fixed"
 * item-display context so it renders the way an item-frame's contents
 * normally would.
 * <p>
 * A repeating tick (mirroring the ensure-it's-there pattern the ambient
 * holograms already use elsewhere in this codebase) verifies each active
 * player's 9 displays are still present and re-spawns any that went
 * missing, so a display removed by something else (a plugin reload, an
 * admin clearing entities, etc.) never leaves a lasting gap.
 * <p>
 * Setup is one-shot: stand with a WorldEdit/FAWE selection around the 9
 * built (empty) frames and run /md tutorial setcraftframes. If no frames
 * are registered, TutorialManager falls back to TutorialCraftGui instead.
 */
public final class TutorialCraftFrameManager {

    private final Plugin plugin;
    private final TutorialCraftFrameStore store;

    // player UUID -> their 9 private, hidden-by-default ItemDisplay copies (slot-indexed, may contain nulls)
    private final Map<UUID, List<UUID>> playerFrames = new HashMap<>();

    // player UUID -> the recipe currently shown, so a self-heal respawn knows what item to put back
    private final Map<UUID, StationType> displayedType = new HashMap<>();

    private BukkitTask healTask;

    public TutorialCraftFrameManager(Plugin plugin, TutorialCraftFrameStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void start() {
        stop();
        healTask = Bukkit.getScheduler().runTaskTimer(plugin, this::healAll, 20L, 20L);
    }

    public void stop() {
        if (healTask != null) {
            healTask.cancel();
            healTask = null;
        }
    }

    public boolean isConfigured() {
        return store.isComplete();
    }

    /**
     * Reads the admin's current WorldEdit/FAWE selection, finds exactly 9
     * ItemFrame entities inside it, sorts them into the standard 3x3 grid
     * order (top-left to bottom-right, based on the frames' shared facing),
     * and persists their locations.
     */
    public void setupFromSelection(Player admin) {
        Region region;
        try {
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(admin));
            region = session.getSelection(BukkitAdapter.adapt(admin.getWorld()));
        } catch (IncompleteRegionException e) {
            admin.sendMessage(ChatColor.RED + "Make a WorldEdit selection around the 9 item frames first.");
            return;
        } catch (Throwable t) {
            admin.sendMessage(ChatColor.RED + "WorldEdit/FAWE isn't available on this server.");
            plugin.getLogger().warning("setcraftframes failed: " + t);
            return;
        }

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        World world = admin.getWorld();

        BoundingBox box = BoundingBox.of(
                new Location(world, min.getX(), min.getY(), min.getZ()),
                new Location(world, max.getX() + 1, max.getY() + 1, max.getZ() + 1)
        );

        List<ItemFrame> frames = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(box)) {
            if (!(e instanceof ItemFrame frame)) continue;
            Location loc = frame.getLocation();
            if (region.contains(BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()))) {
                frames.add(frame);
            }
        }

        if (frames.size() != TutorialCraftFrameStore.SLOT_COUNT) {
            admin.sendMessage(ChatColor.RED + "Found " + frames.size() + " item frames in the selection -- need exactly 9.");
            return;
        }

        BlockFace frameFacing = frames.get(0).getFacing();
        for (ItemFrame frame : frames) {
            if (frame.getFacing() != frameFacing) {
                admin.sendMessage(ChatColor.RED + "All 9 item frames must face the same direction.");
                return;
            }
        }

        // Use the admin's current cardinal look direction so slot 0..8 maps
        // to top-left..bottom-right exactly as the admin sees it while
        // running setcraftframes.
        BlockFace viewDirection = switch (admin.getFacing()) {
            case NORTH, EAST, SOUTH, WEST -> admin.getFacing();
            default -> frameFacing.getOppositeFace();
        };
        BlockFace right = rightOf(viewDirection);

        frames.sort(Comparator
                .comparingDouble((ItemFrame f) -> -f.getLocation().getY())
                .thenComparingDouble(f -> f.getLocation().getX() * right.getModX() + f.getLocation().getZ() * right.getModZ()));

        for (int i = 0; i < TutorialCraftFrameStore.SLOT_COUNT; i++) {
            ItemFrame frame = frames.get(i);
            store.set(i, frame.getLocation());
            // Invisible (vanilla's own "invisible item frame" flag, not a
            // per-player hideEntity trick) so the wooden border never shows
            // to anyone -- only our private ItemDisplay icon is visible,
            // and only to the player currently on this recipe.
            frame.setVisible(false);
        }
        store.save();

        admin.sendMessage(ChatColor.GREEN + "Registered all 9 craft-preview frames from your selection.");
    }

    /** Standard compass "turn right" from the direction a viewer looks to face the wall. */
    private static BlockFace rightOf(BlockFace viewDirection) {
        return switch (viewDirection) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST; // floor/ceiling-mounted frames aren't supported
        };
    }

    /** Shows this player's own private copy of the frames, set to the given recipe's layout. */
    public void displayForPlayer(Player player, StationType type) {
        if (!isConfigured()) return;

        displayedType.put(player.getUniqueId(), type);

        List<UUID> ids = playerFrames.get(player.getUniqueId());
        if (ids == null) {
            ids = new ArrayList<>();
            for (int i = 0; i < TutorialCraftFrameStore.SLOT_COUNT; i++) ids.add(null);
            playerFrames.put(player.getUniqueId(), ids);
        }

        ItemStack[] icons = StationRecipes.shapeIcons(plugin, type);
        for (int i = 0; i < TutorialCraftFrameStore.SLOT_COUNT; i++) {
            ItemDisplay display = resolve(ids.get(i));
            if (display == null) {
                display = spawnSingleFrame(i, player);
                if (display != null) ids.set(i, display.getUniqueId());
            } else {
                // Re-apply every time, not just at spawn -- otherwise an
                // entity that survived a code change (a /reload, or simply
                // not having left/re-entered this recipe since) keeps
                // whatever rotation it was originally spawned with forever,
                // since nothing else here ever touches an existing
                // display's transform.
                display.setTransformation(transformationFor(resolveFacing(i)));
            }
            if (display != null) display.setItemStack(icons[i]);
        }
    }

    /**
     * Re-verifies every active player's displays still exist and re-spawns
     * any that went missing, putting back whatever item that slot should
     * currently show. A no-op for any slot whose display is still fine.
     */
    private void healAll() {
        if (playerFrames.isEmpty()) return;

        for (Map.Entry<UUID, List<UUID>> entry : playerFrames.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            StationType type = displayedType.get(entry.getKey());
            if (type == null) continue;

            ItemStack[] icons = StationRecipes.shapeIcons(plugin, type);
            List<UUID> ids = entry.getValue();

            for (int i = 0; i < TutorialCraftFrameStore.SLOT_COUNT; i++) {
                ItemDisplay display = resolve(ids.get(i));
                if (display == null) {
                    display = spawnSingleFrame(i, player);
                    if (display != null) {
                        display.setItemStack(icons[i]);
                        ids.set(i, display.getUniqueId());
                    }
                } else {
                    display.setTransformation(transformationFor(resolveFacing(i)));
                }
            }
        }
    }

    private ItemDisplay resolve(UUID id) {
        if (id == null) return null;
        Entity e = Bukkit.getEntity(id);
        return (e instanceof ItemDisplay display && display.isValid()) ? display : null;
    }

    private ItemDisplay spawnSingleFrame(int slot, Player player) {
        Location loc = store.get(slot);
        if (loc == null) return null;

        BlockFace facing = resolveFacing(slot);

        ItemDisplay copy = loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(transformationFor(facing));
            d.setVisibleByDefault(false);
            d.setInvulnerable(true);
            d.setPersistent(false);
        });
        player.showEntity(plugin, copy);
        return copy;
    }

    /**
     * The real frame's current facing for this slot (NORTH if it's ever
     * missing -- shouldn't happen once registered, but keeps this from
     * throwing). Also self-heals the frame's invisibility: if it ever came
     * back visible (re-placed by hand, a plugin reload, etc.) this quietly
     * re-hides it rather than requiring setcraftframes to be re-run.
     */
    private BlockFace resolveFacing(int slot) {
        Location loc = store.get(slot);
        if (loc == null) return BlockFace.NORTH;

        ItemFrame real = findRealFrameAt(loc);
        if (real == null) return BlockFace.NORTH;

        if (real.isVisible()) real.setVisible(false);
        return real.getFacing();
    }

    /**
     * Rotation-only transform (no translation) that turns the display's
     * item to face outward the same way a real item frame mounted on this
     * block face would. Scaled down slightly so it doesn't clip into the
     * wall behind it; tune SCALE here if it still looks off in-game.
     * <p>
     * yawDegrees below is the standard Minecraft entity-yaw-for-BlockFace
     * mapping (0=south, 90=west, 180=north, 270=east) -- an item frame's own
     * body is rotated by exactly this much to face the way it does. An
     * ItemDisplay's identity rotation, though, renders an item already
     * rotated -90 degrees relative to that convention (confirmed by testing:
     * a SOUTH frame with no offset showed the item facing WEST, and a WEST
     * frame showed it facing SOUTH -- both exactly explained by "visual
     * facing = input yaw - 90"), so a further +90 is needed to cancel that
     * out and land on the frame's actual facing.
     */
    private static Transformation transformationFor(BlockFace facing) {
        float yawDegrees = switch (facing) {
            case NORTH -> 180f;
            case SOUTH -> 0f;
            case EAST -> 270f;
            case WEST -> 90f;
            default -> 0f; // floor/ceiling-mounted frames aren't supported
        };
        final float YAW_OFFSET_DEGREES = 90f;
        final float SCALE = 0.72f;

        return new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf()
                        .rotateY((float) Math.toRadians(yawDegrees + YAW_OFFSET_DEGREES)),
                new Vector3f(SCALE, SCALE, SCALE),
                new Quaternionf()
        );
    }

    private ItemFrame findRealFrameAt(Location loc) {
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 0.5, 0.5, 0.5)) {
            if (e instanceof ItemFrame f) return f;
        }
        return null;
    }

    /** Removes this player's private frame copies. Call on step-advance, tutorial finish/reset/skip, and quit. */
    public void clearForPlayer(Player player) {
        List<UUID> ids = playerFrames.remove(player.getUniqueId());
        displayedType.remove(player.getUniqueId());
        if (ids != null) {
            for (UUID id : ids) {
                ItemDisplay display = resolve(id);
                if (display != null) display.remove();
            }
        }
    }
}
