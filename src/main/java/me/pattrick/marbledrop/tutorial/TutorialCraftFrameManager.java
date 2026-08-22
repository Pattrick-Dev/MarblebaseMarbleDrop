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
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the physical 3x3 item frame "what to craft" display for the
 * tutorial's CRAFT step, by faking the ITEM field of the admin's real,
 * permanently-empty item frames - via a per-player ENTITY_METADATA
 * packet - so two players on different recipes at the same physical spot
 * each see their own recipe, with no stand-in entity involved at all.
 * <p>
 * An earlier version of this spawned a private {@code ItemDisplay} copy in
 * front of each real frame and hand-computed a rotation transform to match
 * the wall's facing. That math was wrong for east/west-facing walls --
 * items rendered rotated (see git history) - and being a whole extra
 * entity, it could also double up against a real frame that still had
 * leftover content. Faking the real frame's own ITEM field instead means
 * the client renders it with vanilla's own item-frame code: rotation is
 * exactly right by construction, for every wall direction, because it's a
 * genuine item frame doing the rendering, not a copy we're trying to
 * imitate.
 * <p>
 * The real frames' actual server-side content stays empty forever (see
 * setupFromSelection) and invisible (vanilla's own "invisible item frame"
 * flag, hiding the wooden border for everyone); only the fake ITEM packet,
 * sent to one specific player, ever makes a frame appear to hold
 * something, and only for that viewer - nobody else's view of the same
 * entity is touched.
 * <p>
 * A repeating tick re-sends every active viewer's 9 fake packets, because
 * a client that leaves and re-enters render distance of a real entity
 * re-syncs to its true (empty) state on its own, silently dropping our
 * fake overrides until something re-asserts them.
 * <p>
 * Requires ProtocolLib - {@link #isConfigured()} (and so
 * {@code TutorialManager}'s fallback to {@code TutorialCraftGui}) treats a
 * server without it the same as craft frames simply not being registered.
 * <p>
 * Setup is one-shot: stand with a WorldEdit/FAWE selection around the 9
 * built (empty) frames and run /tutorial setcraftframes.
 * <p>
 * The actual ProtocolLib calls live in TutorialCraftFramePackets, a
 * separate class held here as a nullable field and only ever constructed
 * once ProtocolLib is already confirmed present - see that class's
 * javadoc for why this class itself must never contain ProtocolLib-
 * referencing bytecode (it's constructed unconditionally in Main).
 */
public final class TutorialCraftFrameManager {

    private final Plugin plugin;
    private final TutorialCraftFrameStore store;

    // Null if ProtocolLib isn't installed - see isConfigured().
    private final TutorialCraftFramePackets packets;

    // player UUID -> the recipe currently shown, so the heal tick knows what to re-send
    private final Map<UUID, StationType> displayedType = new HashMap<>();

    private BukkitTask healTask;

    public TutorialCraftFrameManager(Plugin plugin, TutorialCraftFrameStore store) {
        this.plugin = plugin;
        this.store = store;
        this.packets = Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")
                ? new TutorialCraftFramePackets(plugin)
                : null;

        if (packets == null) {
            plugin.getLogger().info("[Tutorial] ProtocolLib not found - the craft-frame item preview is disabled; "
                    + "the tutorial's CRAFT step will fall back to TutorialCraftGui instead.");
        }
    }

    public void start() {
        stop();
        if (packets == null) return;
        healTask = Bukkit.getScheduler().runTaskTimer(plugin, this::healAll, 20L, 20L);
    }

    public void stop() {
        if (healTask != null) {
            healTask.cancel();
            healTask = null;
        }
        displayedType.clear();
    }

    /** False (TutorialManager falls back to TutorialCraftGui) if the frames aren't registered, or ProtocolLib isn't installed to drive them. */
    public boolean isConfigured() {
        return packets != null && store.isComplete();
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
            admin.sendMessage(ChatColor.RED + "Found " + frames.size() + " item frames in the selection - need exactly 9.");
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
            // Vanilla still renders a real frame's contained item (correctly
            // rotated, but shared by every viewer) even while the frame
            // itself is invisible - if this ever had an item left in it
            // (e.g. a placeholder used while building), every player would
            // see it layered behind their own fake, recipe-specific item.
            // Clearing it here guarantees that can't happen regardless of
            // what the admin built with.
            frame.setItem(null);
            // Invisible (vanilla's own "invisible item frame" flag, not a
            // per-player hideEntity trick) so the wooden border never shows
            // to anyone - only the fake ITEM packet is ever visible, and
            // only to the player currently on this recipe.
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

    /** Shows this player's own fake copy of the 9 frames' contents, set to the given recipe's layout. */
    public void displayForPlayer(Player player, StationType type) {
        if (!isConfigured()) return;

        displayedType.put(player.getUniqueId(), type);
        sendAll(player, type);
    }

    /** Re-sends every active viewer's fake contents, so a client that resynced from real (empty) frames picks the fake ones back up. */
    private void healAll() {
        if (displayedType.isEmpty()) return;

        for (Map.Entry<UUID, StationType> entry : displayedType.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            sendAll(player, entry.getValue());
        }
    }

    private void sendAll(Player player, StationType type) {
        ItemStack[] icons = StationRecipes.shapeIcons(plugin, type);
        for (int i = 0; i < TutorialCraftFrameStore.SLOT_COUNT; i++) {
            ItemFrame frame = realFrameAt(i);
            if (frame != null) packets.sendFakeItem(plugin, player, frame, icons[i]);
        }
    }

    /** Reverts all 9 frames back to their real (empty) content for this player. Call on step-advance, tutorial finish/reset/skip, and quit. */
    public void clearForPlayer(Player player) {
        StationType type = displayedType.remove(player.getUniqueId());
        if (type == null) return; // never shown anything to this player - nothing to revert

        for (int i = 0; i < TutorialCraftFrameStore.SLOT_COUNT; i++) {
            ItemFrame frame = realFrameAt(i);
            if (frame != null) packets.sendFakeItem(plugin, player, frame, null);
        }
    }

    /**
     * The real frame entity for this slot, or null if it's ever missing
     * (shouldn't happen once registered). Also self-heals the frame's
     * invisibility: if it ever came back visible (re-placed by hand, a
     * plugin reload, etc.) this quietly re-hides it rather than requiring
     * setcraftframes to be re-run.
     */
    private ItemFrame realFrameAt(int slot) {
        Location loc = store.get(slot);
        if (loc == null) return null;

        ItemFrame frame = findRealFrameAt(loc);
        if (frame == null) return null;

        if (frame.isVisible()) frame.setVisible(false);
        return frame;
    }

    private ItemFrame findRealFrameAt(Location loc) {
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 0.5, 0.5, 0.5)) {
            if (e instanceof ItemFrame f) return f;
        }
        return null;
    }

}
