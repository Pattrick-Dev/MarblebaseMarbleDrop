package me.pattrick.marbledrop.races;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a race ability item (Boost, Glow) in a player's hotbar via a raw
 * SET_SLOT packet instead of actually placing it in their real inventory.
 * The real slot is never touched - RaceWatchManager no longer clears a
 * racer's inventory at all when this is available, so there's nothing to
 * lose if the server crashes mid-race and nothing to restore afterward
 * either. Only the client's *display* of that one hotbar slot is
 * overridden; clear()/clearAll() resync it back to whatever's actually
 * there via Player#updateInventory().
 * <p>
 * Each overlaid slot is tagged (see TAG_BOOST/TAG_GLOW) so a listener can
 * ask "is the player's currently-held slot showing MY ability" rather than
 * just "is it showing some fake item" - Boost and Glow live in different
 * slots per player (see RaceManager), so a plain boolean wouldn't be
 * enough to tell them apart.
 * <p>
 * SET_SLOT carries a "state id" the client uses to detect desync between
 * its predicted inventory state and the server's - get it wrong and the
 * client can silently ignore or revert the fake item. Rather than
 * hardcoding a guess, this tracks the real state id the server last sent
 * that player (by watching the server's own outgoing SET_SLOT/
 * WINDOW_ITEMS packets) and echoes it back, so the fake packet always
 * looks consistent with whatever the client currently expects.
 * <p>
 * Requires ProtocolLib. Main only constructs this when ProtocolLib is
 * actually installed (see softdepend in plugin.yml); everything that uses
 * this falls back to real inventory items when it's null.
 */
public final class RaceInventoryOverlay {

    public static final String TAG_BOOST = "boost";
    public static final String TAG_GLOW = "glow";

    private final ProtocolManager manager;

    // playerId -> last real state id the server sent them, from watching
    // its own outgoing packets. Falls back to 0 (the state id vanilla
    // itself uses before any real inventory change has happened yet) if
    // we haven't seen one for a player yet.
    private final Map<UUID, Integer> lastKnownStateId = new ConcurrentHashMap<>();

    // playerId -> (hotbar slot 0-8 -> tag) for every slot currently
    // showing a fake item for that player.
    private final Map<UUID, Map<Integer, String>> fakeSlotsByPlayer = new ConcurrentHashMap<>();

    // playerId -> (hotbar slot 0-8 -> the ItemStack last shown there) --
    // lets reassertAll() re-send exactly what was showing, without the
    // caller needing to remember it. See reassertAll()'s javadoc for why
    // this exists.
    private final Map<UUID, Map<Integer, ItemStack>> fakeItemByPlayer = new ConcurrentHashMap<>();

    public RaceInventoryOverlay(Plugin plugin) {
        this.manager = ProtocolLibrary.getProtocolManager();

        manager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.MONITOR,
                PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                // MONITOR priority + read-only: this only ever observes the
                // server's own real packets to learn the current state id,
                // never modifies or cancels them. Wrapped defensively --
                // this must never disrupt the real packet it's observing.
                try {
                    Integer stateId = event.getPacket().getIntegers().readSafely(1);
                    if (stateId != null) {
                        lastKnownStateId.put(event.getPlayer().getUniqueId(), stateId);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    /** Shows {@code fakeItem} in the player's hotbar slot {@code hotbarSlot} (0-8), tagged so isFakeSlot() can identify it later. */
    public void show(Player player, int hotbarSlot, String tag, ItemStack fakeItem) {
        fakeSlotsByPlayer.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>()).put(hotbarSlot, tag);
        fakeItemByPlayer.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>()).put(hotbarSlot, fakeItem);
        sendFakeSlot(player, hotbarSlot, fakeItem);
    }

    /**
     * Re-sends every currently-tracked fake slot's last-shown item for this
     * player. Needed because cancelling a Bukkit inventory event (drop,
     * shift-click, drag, ...) only stops the real-world effect - it
     * doesn't stop the vanilla trailing packet the server sends right
     * after to correct the client's own optimistic prediction (the client
     * assumes the drop/move succeeded and updates its display before the
     * server even responds). That correction reflects the real item
     * underneath (the player's marble), which would silently overwrite our
     * fake overlay the same way the click-triggered held-slot resync did
     * (see RaceBoostListener's javadoc for the first place this pattern
     * showed up). Call this (see RaceWatchManager) a tick after cancelling
     * one of those events, so it lands after that correction instead of
     * racing against it.
     */
    public void reassertAll(Player player) {
        Map<Integer, ItemStack> items = fakeItemByPlayer.get(player.getUniqueId());
        if (items == null) return;
        for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
            sendFakeSlot(player, e.getKey(), e.getValue());
        }
    }

    /**
     * Overlays the player's entire real inventory - armor, main inventory,
     * hotbar, offhand - as empty, so nothing real (their marble included)
     * shows through for the duration of a race. Call this once, before
     * show() lays the actual ability items on top of their designated
     * hotbar slots, so those end up as the last word for their slots.
     * Undone by clear()/clearAll()'s resync back to reality.
     */
    public void hideRealInventory(Player player) {
        // An actual AIR stack, not Java null - ProtocolLib's item
        // converter (IgnoreNullConverter) passes a null straight through
        // unconverted instead of turning it into an empty NMS ItemStack,
        // and the packet's item field isn't nullable, so a raw null here
        // silently breaks encoding for the packet (past where our
        // try/catch below can see it - it fails on the network thread
        // during the async write, not the synchronous send call).
        ItemStack empty = new ItemStack(Material.AIR);
        for (int protocolSlot = 5; protocolSlot <= 45; protocolSlot++) {
            sendRawSlot(player, protocolSlot, empty);
        }
    }

    /** True if this player's hotbar slot is currently overlaid with an item tagged {@code tag}. */
    public boolean isFakeSlot(Player player, int hotbarSlot, String tag) {
        Map<Integer, String> slots = fakeSlotsByPlayer.get(player.getUniqueId());
        return slots != null && tag.equals(slots.get(hotbarSlot));
    }

    /** Stops overlaying one specific slot and resyncs the player's whole inventory back to reality. */
    public void clear(Player player, int hotbarSlot) {
        Map<Integer, String> slots = fakeSlotsByPlayer.get(player.getUniqueId());
        if (slots != null) slots.remove(hotbarSlot);
        Map<Integer, ItemStack> items = fakeItemByPlayer.get(player.getUniqueId());
        if (items != null) items.remove(hotbarSlot);
        player.updateInventory();
    }

    /** Stops overlaying every fake slot for this player and resyncs their client back to their real inventory. */
    public void clearAll(Player player) {
        fakeSlotsByPlayer.remove(player.getUniqueId());
        fakeItemByPlayer.remove(player.getUniqueId());
        lastKnownStateId.remove(player.getUniqueId());
        player.updateInventory();
    }

    private void sendFakeSlot(Player player, int hotbarSlot, ItemStack item) {
        // Container 0 (the player's own inventory) numbers the hotbar 36-44,
        // not 0-8 - Bukkit's PlayerInventory indices don't match the wire
        // protocol's slot indices for this container.
        sendRawSlot(player, 36 + hotbarSlot, item);
    }

    /** Same as sendFakeSlot(), but takes the raw container-0 protocol slot index directly (see hideRealInventory()). */
    private void sendRawSlot(Player player, int protocolSlot, ItemStack item) {
        int stateId = lastKnownStateId.getOrDefault(player.getUniqueId(), 0);

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.SET_SLOT);
        packet.getIntegers()
                .write(0, 0) // window id 0 == the player's own inventory
                .write(1, stateId)
                .write(2, protocolSlot);
        packet.getItemModifier().write(0, item);

        try {
            manager.sendServerPacket(player, packet, false);
        } catch (Exception ex) {
            player.sendMessage("Couldn't show a race item overlay.");
        }
    }
}
