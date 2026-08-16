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
 * All of RaceBoostListener's ProtocolLib-touching code, isolated into its
 * own class for the same reason TrackBuildToolsPacketBridge is - see that
 * class's javadoc. Only ever constructed from an already-confirmed
 * "ProtocolLib is present" branch.
 */
final class RaceBoostPacketBridge {

    RaceBoostPacketBridge(Plugin plugin, Predicate<Player> isHoldingBoostItem, Consumer<Player> handleBoost) {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.LOW,
                PacketType.Play.Client.USE_ITEM, PacketType.Play.Client.USE_ITEM_ON) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                EnumWrappers.Hand hand = event.getPacket().getHands().readSafely(0);
                if (hand != null && hand != EnumWrappers.Hand.MAIN_HAND) return;

                Player player = event.getPlayer();
                if (!isHoldingBoostItem.test(player)) return;

                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> handleBoost.accept(player));
            }
        });
    }
}
