package me.pattrick.marbledrop.races;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * All of TrackBuildToolsListener's ProtocolLib-touching code, isolated into
 * its own class specifically so TrackBuildToolsListener itself - a real
 * Bukkit Listener, unconditionally constructed and registered in Main -
 * never contains any ProtocolLib-referencing bytecode. This class is only
 * ever constructed from inside an already-confirmed "ProtocolLib is
 * present" branch, so it's the only place a NoClassDefFoundError for a
 * missing ProtocolLib class could originate from here, and only when
 * actually reachable.
 * <p>
 * Reads the raw USE_ITEM/USE_ITEM_ON client packet and cancels it before
 * vanilla's own handler ever runs, which avoids the forced held-slot
 * resync that would otherwise immediately overwrite the fake tool icon
 * with whatever's really in that slot (the player's real, untouched
 * item). The Point Tool needs to know which block was targeted; onUse
 * (bound to TrackBuildToolsListener#handle) re-raycasts server-side
 * rather than parsing that out of the raw packet.
 * <p>
 * A single physical right-click at a block sends BOTH of the packet types
 * listened for here - the vanilla client always sends USE_ITEM_ON first,
 * and (for a non-block item like these tools, since nothing consumed the
 * block interaction) immediately follows it with USE_ITEM for the same
 * click. Bukkit's own PlayerInteractEvent construction collapses that
 * pair into a single event; reading the raw packets bypasses that
 * collapsing, so without the pendingThisTick guard below onUse fired
 * twice per click - see git history, this was exactly the "track tools
 * fire twice" bug.
 */
final class TrackBuildToolsPacketBridge {

    // Player UUIDs with an onUse call already scheduled for later this tick
    // - the second packet of the USE_ITEM_ON+USE_ITEM pair for the same
    // click hits this and is dropped instead of scheduling a duplicate.
    // ConcurrentHashMap-backed since packet listeners can run off the main
    // thread (see RaceGlowPacketBridge).
    private final Set<UUID> pendingThisTick = ConcurrentHashMap.newKeySet();

    TrackBuildToolsPacketBridge(Plugin plugin, RaceInventoryOverlay overlay,
                                 BiConsumer<Player, TrackCreationKit.Tool> onUse) {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.LOW,
                PacketType.Play.Client.USE_ITEM, PacketType.Play.Client.USE_ITEM_ON) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                EnumWrappers.Hand hand = event.getPacket().getHands().readSafely(0);
                if (hand != null && hand != EnumWrappers.Hand.MAIN_HAND) return;

                Player player = event.getPlayer();
                int heldSlot = player.getInventory().getHeldItemSlot();
                TrackCreationKit.Tool tool = TrackCreationKit.Tool.bySlot(heldSlot);
                if (tool == null || !overlay.isFakeSlot(player, heldSlot, tool.tag)) return;

                event.setCancelled(true);

                UUID id = player.getUniqueId();
                if (!pendingThisTick.add(id)) return; // already queued from this same click's other packet
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pendingThisTick.remove(id);
                    onUse.accept(player, tool);
                });
            }
        });
    }
}
