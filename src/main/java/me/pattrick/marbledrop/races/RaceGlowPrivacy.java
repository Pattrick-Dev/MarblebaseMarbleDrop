package me.pattrick.marbledrop.races;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes a marble's glowing-outline toggle (see RaceGlowListener) private to
 * the player who turned it on, instead of Entity#setGlowing()'s normal
 * shared-to-everyone behavior - Bukkit has no per-viewer option for that
 * flag, so this rewrites the actual entity-metadata packets.
 * <p>
 * The real entity DOES have the glowing flag set (MarbleRunner still just
 * calls Entity#setGlowing() - see setGlowing()/isGlowing() there); this
 * only intercepts the outgoing packets telling OTHER players' clients
 * about it and strips the glowing bit back out before it reaches anyone
 * but the owner. That means it correctly covers every way a client learns
 * about the flag - the initial toggle, and a new viewer entering render
 * distance of an already-glowing marble - since both go through the same
 * ENTITY_METADATA packet type, not just a single point-in-time send.
 * <p>
 * Requires ProtocolLib. Main only constructs this when ProtocolLib is
 * actually installed (see softdepend in plugin.yml) - without it, glow
 * still works, just visible to everyone, same as before this existed.
 */
public final class RaceGlowPrivacy {

    // Bit 0x40 in the shared entity-flags byte (metadata index 0) is
    // GLOWING - every entity type inherits this index from the base
    // Entity class, and it's the same bit the vanilla Glowing potion
    // effect sets. Stable across modern Minecraft versions.
    private static final int FLAGS_INDEX = 0;
    private static final byte GLOWING_BIT = 0x40;

    private final Set<Integer> trackedEntityIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, UUID> ownerByEntityId = new ConcurrentHashMap<>();

    public RaceGlowPrivacy(Plugin plugin) {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                int entityId = packet.getIntegers().read(0);
                if (!trackedEntityIds.contains(entityId)) return;

                UUID owner = ownerByEntityId.get(entityId);
                if (owner != null && owner.equals(event.getPlayer().getUniqueId())) return; // the owner sees the real thing

                stripGlow(packet);
            }
        });
    }

    private void stripGlow(PacketContainer packet) {
        StructureModifier<List<WrappedDataValue>> mod = packet.getDataValueCollectionModifier();
        List<WrappedDataValue> values = mod.read(0);
        if (values == null) return;

        for (WrappedDataValue value : values) {
            if (value.getIndex() != FLAGS_INDEX) continue;

            Object raw = value.getValue();
            if (!(raw instanceof Byte b)) continue;

            byte cleared = (byte) (b & ~GLOWING_BIT);
            if (cleared != b) value.setValue(cleared);
        }
    }

    /** Starts hiding this entity's glow from everyone except {@code owner}. Call right after turning glow on. */
    public void track(Entity entity, UUID owner) {
        trackedEntityIds.add(entity.getEntityId());
        ownerByEntityId.put(entity.getEntityId(), owner);
    }

    /** Stops filtering for this entity - call when glow is turned back off (or the marble despawns). */
    public void untrack(Entity entity) {
        int id = entity.getEntityId();
        trackedEntityIds.remove(id);
        ownerByEntityId.remove(id);
    }
}
