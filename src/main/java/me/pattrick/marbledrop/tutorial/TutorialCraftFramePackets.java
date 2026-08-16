package me.pattrick.marbledrop.tutorial;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * All of TutorialCraftFrameManager's ProtocolLib-touching code, isolated
 * into its own class so that class itself never contains any
 * ProtocolLib-referencing bytecode - unlike the race listeners (see
 * TrackBuildToolsPacketBridge's javadoc), TutorialCraftFrameManager isn't a
 * Bukkit Listener, so it isn't at risk from registerEvents()'s reflection
 * scan specifically, but it's still constructed unconditionally in Main
 * (craftFrameManager is a non-nullable field TutorialManager depends on),
 * so the same "never let the optional dependency's types appear in a class
 * that's unconditionally touched" discipline applies - only this wrapper,
 * held as a nullable field, is ever constructed, and only once ProtocolLib
 * is already confirmed present.
 */
final class TutorialCraftFramePackets {

    private final ProtocolManager protocol;

    TutorialCraftFramePackets(Plugin plugin) {
        this.protocol = ProtocolLibrary.getProtocolManager();
    }

    /**
     * Sends a fake ENTITY_METADATA packet overriding just this one real
     * frame's ITEM field for one player - {@code fakeItem} null reverts
     * it back to empty/AIR. Everyone else's view, and the frame's real
     * server-side state, are untouched.
     * <p>
     * The field to overwrite is found by scanning the frame's own live
     * data watcher for its single ItemStack-typed entry, rather than a
     * hardcoded index - an ItemFrame only ever has exactly one (the
     * "Item" field; "Rotation" is a plain int we never touch, so these
     * frames never need in-frame rotation), so this is unambiguous and
     * doesn't depend on which numeric index happens to hold it.
     * <p>
     * The packet itself is built via getDataValueCollectionModifier()
     * (List&lt;WrappedDataValue&gt;), NOT the older
     * getWatchableCollectionModifier() (List&lt;WrappedWatchableObject&gt;)
     * - that legacy compatibility path mis-packs on this server version:
     * it hands the packet's pack() method a live SynchedEntityData$DataItem
     * where a DataValue snapshot is required, which throws a
     * ClassCastException deep in the encoder and force-disconnects
     * whoever it was being sent to. getDataValueCollectionModifier() is
     * the modern format and packs correctly (RaceGlowPrivacy already
     * relies on it for the same packet type).
     */
    void sendFakeItem(Plugin plugin, Player player, ItemFrame frame, ItemStack fakeItem) {
        WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(frame);

        WrappedWatchableObject itemEntry = null;
        for (WrappedWatchableObject w : watcher.getWatchableObjects()) {
            if (w.getValue() instanceof ItemStack) {
                itemEntry = w;
                break;
            }
        }
        if (itemEntry == null) return; // shouldn't happen - every ItemFrame has exactly one ItemStack-typed field

        ItemStack value = (fakeItem != null) ? fakeItem : new ItemStack(Material.AIR);
        WrappedDataValue fakeEntry = WrappedDataValue.fromWrappedValue(
                itemEntry.getIndex(), itemEntry.getWatcherObject().getSerializer(), value);

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, frame.getEntityId());
        packet.getDataValueCollectionModifier().write(0, List.of(fakeEntry));

        try {
            protocol.sendServerPacket(player, packet, false);
        } catch (Exception ex) {
            plugin.getLogger().warning("[Tutorial] Couldn't show a craft-frame preview item: " + ex.getMessage());
        }
    }
}
