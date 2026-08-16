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

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * All of RaceGlowListener's ProtocolLib-touching code, isolated into its
 * own class for the same reason TrackBuildToolsPacketBridge is - see that
 * class's javadoc. Only ever constructed from an already-confirmed
 * "ProtocolLib is present" branch.
 */
final class RaceGlowPacketBridge {

    RaceGlowPacketBridge(Plugin plugin, Predicate<Player> isHoldingGlowItem, Consumer<Player> toggle) {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.LOW,
                PacketType.Play.Client.USE_ITEM, PacketType.Play.Client.USE_ITEM_ON) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                // Our ability items only ever live in a hotbar slot - never
                // the off-hand - so an off-hand interaction isn't ours,
                // whatever else might be going on with it.
                EnumWrappers.Hand hand = event.getPacket().getHands().readSafely(0);
                if (hand != null && hand != EnumWrappers.Hand.MAIN_HAND) return;

                Player player = event.getPlayer();
                if (!isHoldingGlowItem.test(player)) return;

                event.setCancelled(true);
                // Packet listeners run off the main thread in some cases -
                // hop back to it before touching any Bukkit state.
                Bukkit.getScheduler().runTask(plugin, () -> toggle.accept(player));
            }
        });
    }
}
