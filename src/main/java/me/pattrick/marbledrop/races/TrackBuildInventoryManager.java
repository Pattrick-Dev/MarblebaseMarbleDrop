package me.pattrick.marbledrop.races;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks who's currently in track "build mode" and which track they're
 * building, and hides their real inventory behind a packet-only overlay
 * for the duration - see RaceInventoryOverlay, the same mechanism
 * already used to fake the race Boost/Glow items.
 * <p>
 * The real inventory is never touched: not cleared, not read, not
 * written anywhere. There is nothing to save, nothing to persist to
 * disk, and nothing that can ever be lost - logging out, crashing, or
 * the server restarting mid-build all just mean the overlay quietly
 * stops applying to that connection; the player's actual inventory was
 * sitting there untouched the entire time and is exactly what they see
 * the moment they're back.
 * <p>
 * Requires ProtocolLib. Without it (overlay == null), build mode falls
 * back to handing out real kit items alongside whatever's already in the
 * player's inventory - no hiding, but also nothing to lose, the same
 * softly-optional pattern as RaceBoostListener/RaceGlowListener.
 */
public final class TrackBuildInventoryManager implements Listener {

    private final RaceInventoryOverlay overlay;

    // playerId -> the track they're currently building, if any
    private final Map<UUID, String> building = new HashMap<>();

    public TrackBuildInventoryManager(RaceInventoryOverlay overlay) {
        this.overlay = overlay;
    }

    /** Nullable - see the class javadoc. Null means ProtocolLib isn't installed and kit tools fall back to real items. */
    public RaceInventoryOverlay overlay() {
        return overlay;
    }

    public boolean isBuilding(Player p) {
        return p != null && building.containsKey(p.getUniqueId());
    }

    /** The track this player is currently building, or null if they aren't. */
    public String activeTrackId(Player p) {
        return p == null ? null : building.get(p.getUniqueId());
    }

    /**
     * Marks {@code p} as building {@code trackId} and, if ProtocolLib is
     * available, hides their real inventory behind the overlay - a
     * no-op on the hide step if they were already building something
     * (switching tracks mid-session, or topping up a lost tool, doesn't
     * need a second hide).
     */
    public void enter(Player p, String trackId) {
        boolean wasBuilding = isBuilding(p);
        building.put(p.getUniqueId(), trackId);

        if (!wasBuilding && overlay != null) {
            overlay.hideRealInventory(p);
        }
    }

    /** Ends the build session and restores the real (never-touched) inventory view. No-op if they weren't building. */
    public void exit(Player p) {
        if (building.remove(p.getUniqueId()) == null) return;
        if (overlay != null) {
            overlay.clearAll(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent e) {
        building.remove(e.getPlayer().getUniqueId());
    }
}
