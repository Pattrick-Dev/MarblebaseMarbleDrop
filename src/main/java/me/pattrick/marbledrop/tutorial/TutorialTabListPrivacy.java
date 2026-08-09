package me.pattrick.marbledrop.tutorial;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a tutorial-hidden player's name in the tab list even though
 * {@link TutorialVisibility} hides their actual in-world entity.
 * Bukkit's {@code Player#hideEntity}, when the target is another Player,
 * also removes that player's tab-list entry -- the right default outside
 * the tutorial (no "ghost" name with nothing visible behind it), but it
 * reads exactly like a disconnect here, which is worse for onboarding
 * than just not seeing them in the world.
 * <p>
 * This only strips the tab-list side effect: the underlying entity-hide
 * (and whatever interaction/attack blocking comes with it) is untouched,
 * so two tutorial-separated players still can't see or hit each other --
 * only their tab entries survive. It works by intercepting the outgoing
 * PLAYER_INFO_REMOVE packet and dropping just the UUIDs we've been told
 * to protect for that specific viewer, the same "filter one real packet"
 * approach RaceGlowPrivacy uses for glow visibility.
 * <p>
 * Requires ProtocolLib -- construct via {@link #createIfAvailable(Plugin)},
 * which returns null if it isn't installed; every caller treats null the
 * same as "just let hideEntity's default tab-removal happen."
 */
public final class TutorialTabListPrivacy {

    // viewerId -> set of player UUIDs whose PLAYER_INFO_REMOVE should be
    // suppressed for that specific viewer right now.
    private final Map<UUID, Set<UUID>> keepListedFor = new ConcurrentHashMap<>();

    public static TutorialTabListPrivacy createIfAvailable(Plugin plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) return null;
        return new TutorialTabListPrivacy(plugin);
    }

    private TutorialTabListPrivacy(Plugin plugin) {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.PLAYER_INFO_REMOVE) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Set<UUID> keep = keepListedFor.get(event.getPlayer().getUniqueId());
                if (keep == null || keep.isEmpty()) return;

                StructureModifier<List<UUID>> mod = event.getPacket().getUUIDLists();
                List<UUID> removed = mod.read(0);
                if (removed == null || removed.isEmpty()) return;

                List<UUID> filtered = new ArrayList<>(removed);
                if (filtered.removeAll(keep)) {
                    mod.write(0, filtered);
                }
            }
        });
    }

    /** Suppresses {@code hidden}'s tab-list removal for {@code viewer} -- call right before {@code viewer.hideEntity(plugin, hidden)}. */
    public void keepListed(Player viewer, Player hidden) {
        keepListedFor.computeIfAbsent(viewer.getUniqueId(), id -> ConcurrentHashMap.newKeySet())
                .add(hidden.getUniqueId());
    }

    /** Stops protecting {@code hidden}'s tab entry for {@code viewer} -- call alongside {@code viewer.showEntity(plugin, hidden)}. */
    public void stopKeepingListed(Player viewer, Player hidden) {
        Set<UUID> keep = keepListedFor.get(viewer.getUniqueId());
        if (keep != null) keep.remove(hidden.getUniqueId());
    }

    /**
     * Full cleanup for one player -- call on quit, both as a viewer (they're
     * gone, nothing left to protect for them) and as a previously-hidden
     * target (so a real disconnect's own PLAYER_INFO_REMOVE isn't
     * accidentally suppressed for whoever still had them protected).
     */
    public void clear(Player player) {
        keepListedFor.remove(player.getUniqueId());
        for (Set<UUID> keep : keepListedFor.values()) {
            keep.remove(player.getUniqueId());
        }
    }
}
