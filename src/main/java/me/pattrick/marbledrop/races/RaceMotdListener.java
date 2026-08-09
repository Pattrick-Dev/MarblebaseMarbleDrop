package me.pattrick.marbledrop.races;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

/**
 * Rewrites the server list MOTD on every single ping (server.properties'
 * motd is only ever a static fallback -- this fires fresh each time a
 * client's multiplayer screen pings/refreshes, which is what makes the
 * race countdown "live" with no polling/scheduling of our own needed).
 */
public final class RaceMotdListener implements Listener {

    private final ScheduledRaceManager scheduledRaces;

    public RaceMotdListener(ScheduledRaceManager scheduledRaces) {
        this.scheduledRaces = scheduledRaces;
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        event.motd(Component.text("Marblebase", NamedTextColor.AQUA)
                .append(Component.newline())
                .append(statusLine()));
    }

    private Component statusLine() {
        // Entries closed but the race itself hasn't finished yet.
        if (scheduledRaces.activeCycleTrackId() != null && scheduledRaces.openTrackId() == null) {
            return Component.text("A race is running right now!", NamedTextColor.GOLD);
        }

        if (scheduledRaces.openTrackId() != null) {
            return Component.text("Race open -- starts in " + ScheduledRaceManager.formatDuration(scheduledRaces.secondsUntilNextCycle())
                    + " -- join with /md join!", NamedTextColor.GREEN);
        }

        long seconds = scheduledRaces.secondsUntilNextCycle();
        String label = seconds <= 60 ? "Next race starting any moment!" : ("Next race in " + ScheduledRaceManager.formatDuration(seconds));
        return Component.text(label, NamedTextColor.YELLOW);
    }
}
