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
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * All of RaceGlowListener's ProtocolLib-touching code, isolated into its
 * own class for the same reason TrackBuildToolsPacketBridge is - see that
 * class's javadoc. Only ever constructed from an already-confirmed
 * "ProtocolLib is present" branch.
 * <p>
 * pendingThisTick guards against the same double-fire TrackBuildToolsPacketBridge
 * documents in full: a single physical click against a block sends both
 * USE_ITEM_ON and USE_ITEM, and without this guard toggle ran twice per
 * click - harmless here since two toggles cancel back out, but worth
 * closing so a delayed/rescheduled toggle can't ever land on the wrong tick.
 */
final class RaceGlowPacketBridge {

    private final Set<UUID> pendingThisTick = ConcurrentHashMap.newKeySet();

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

                UUID id = player.getUniqueId();
                if (!pendingThisTick.add(id)) return; // already queued from this same click's other packet
                // Packet listeners run off the main thread in some cases -
                // hop back to it before touching any Bukkit state.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pendingThisTick.remove(id);
                    toggle.accept(player);
                });
            }
        });
    }
}
