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
 */
final class TrackBuildToolsPacketBridge {

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
                Bukkit.getScheduler().runTask(plugin, () -> onUse.accept(player, tool));
            }
        });
    }
}
