package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.MdConfig;
import me.pattrick.marbledrop.marble.MarbleData;
import me.pattrick.marbledrop.marble.MarbleItem;
import me.pattrick.marbledrop.marble.MarbleRarity;
import me.pattrick.marbledrop.marble.MarbleStat;
import me.pattrick.marbledrop.marble.MarbleStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.*;

public final class RaceManager {

    public static final int MAX_ENTRIES_PER_TRACK = 16;

    // Starting-grid spacing (both laterally within a row and row-to-row) --
    // roughly a marble's diameter plus a small buffer, same scale as
    // MarbleRunner's own marble-vs-marble separation.
    private static final double GRID_SPACING = 0.45;

    // "On your marks" + 3-2-1 before a held grid is released -- see
    // runStartCountdown(). Public so ScheduledRaceManager can time its own
    // synced broadcasts (e.g. announcing the reward) against the same GO
    // moment without hardcoding a duplicate tick count.
    public static final int COUNTDOWN_SECONDS = 3;
    public static final long COUNTDOWN_TOTAL_TICKS = (COUNTDOWN_SECONDS + 1) * 20L; // +1 for the initial "on your marks" beat

    private final TrackManager tracks;
    private final MarbleRaceEngine engine;
    private final MdConfig config;

    // ✅ Watch manager (optional)
    private RaceWatchManager watch;

    // Nullable -- only set when ProtocolLib is installed (see Main). When
    // present, giveBoostItem()/giveGlowItem() show ability items via fake
    // packets instead of real inventory writes (see RaceInventoryOverlay).
    private RaceInventoryOverlay inventoryOverlay;

    // trackId -> active session
    private final Map<String, RaceSession> active = new HashMap<>();

    // trackId -> lobby entries
    private final Map<String, List<RaceEntry>> lobby = new HashMap<>();

    // ✅ NEW: trackId -> OPEN state
    private final Set<String> openTracks = new HashSet<>();

    // playerId -> their own runner, for the race they're currently in (see
    // start()/RaceBoostListener). Populated when a real race starts,
    // removed as each runner finishes (or the field is force-cleared by
    // purgeAllRunners()) -- never left pointing at a finished/despawned
    // runner, so a stale Boost item just reports "race already finished"
    // instead of silently doing nothing to a dead object.
    private final Map<UUID, MarbleRunner> activeRunnerByOwner = new HashMap<>();

    // playerId -> last-picked RaceLoadout, across all tracks. Doubles as
    // the fallback getLoadout() reads when there's no real lobby entry to
    // check -- which is what lets /md race test respect a loadout picked
    // through the same GUI a real lobby entry uses, without needing a
    // lobby entry of its own.
    private final Map<UUID, RaceLoadout> loadoutPreference = new HashMap<>();

    // playerId -> remaining Boost charges for their current race. This is
    // now the authoritative count -- with the inventory overlay active
    // there's no real ItemStack whose stack size can double as the
    // "charges remaining" display the way RaceBoostItem's own javadoc
    // originally intended, so RaceBoostListener reads/writes this instead
    // and just re-renders a fresh fake item reflecting it each time.
    private final Map<UUID, Integer> boostCharges = new HashMap<>();

    /**
     * Fired whenever a race started via {@link #start} finishes, for any
     * external code (currently just ScheduledRaceManager) that wants to react
     * to a race's outcome -- e.g. awarding Dust, running a server-wide
     * announcement -- without RaceManager itself knowing anything about
     * dust/scheduling.
     */
    public interface OutcomeListener {
        void onRaceFinished(String trackId, List<RaceEntry> finishOrder);
    }

    private OutcomeListener outcomeListener;

    public RaceManager(TrackManager tracks, MarbleRaceEngine engine, MdConfig config) {
        this.tracks = tracks;
        this.engine = engine;
        this.config = config;
    }

    public void setOutcomeListener(OutcomeListener listener) {
        this.outcomeListener = listener;
    }

    // ✅ allow Main to wire watch manager without redesigning flow
    public void setWatchManager(RaceWatchManager watch) {
        this.watch = watch;
    }

    /** Wired in from Main.java only when ProtocolLib is installed -- see the field javadoc. */
    public void setInventoryOverlay(RaceInventoryOverlay inventoryOverlay) {
        this.inventoryOverlay = inventoryOverlay;
    }

    /** Defensive copy of a track's current lobby -- read-only peek, doesn't affect join validation. */
    public List<RaceEntry> lobbySnapshot(String trackId) {
        if (trackId == null) return List.of();
        List<RaceEntry> list = lobby.get(trackId.toLowerCase());
        return list == null ? List.of() : List.copyOf(list);
    }

    /** This player's own runner in whatever real race they're currently in, or null if they're not racing (or already finished). See RaceBoostListener. */
    public MarbleRunner getMyRunner(UUID playerId) {
        if (playerId == null) return null;
        return activeRunnerByOwner.get(playerId);
    }

    /** Remaining Boost charges for this player's current race, or 0 if they have none (or aren't racing). */
    public int getBoostCharges(UUID playerId) {
        if (playerId == null) return 0;
        return boostCharges.getOrDefault(playerId, 0);
    }

    /** Called by RaceBoostListener right after consuming a charge, to persist the new count. */
    public void setBoostCharges(UUID playerId, int charges) {
        if (playerId == null) return;
        boostCharges.put(playerId, Math.max(0, charges));
    }

    /** The track this player currently has an entry in (first match), or null if none -- so /md leave doesn't need a trackId argument. */
    public String findTrackIdForPlayer(UUID playerId) {
        if (playerId == null) return null;
        for (Map.Entry<String, List<RaceEntry>> e : lobby.entrySet()) {
            for (RaceEntry entry : e.getValue()) {
                if (playerId.equals(entry.owner)) return e.getKey();
            }
        }
        return null;
    }

    /** Force-removes every currently active marble runner without firing finish callbacks -- see /md race purge. */
    public void purgeAllRunners() {
        engine.purgeAll();
        activeRunnerByOwner.clear();
        boostCharges.clear();
    }

    // ----------------------------
    // ✅ OPEN / CLOSE (Lobby state)
    // ----------------------------

    public boolean open(Player opener, String trackId) {
        if (trackId == null || trackId.isBlank()) return false;
        trackId = trackId.toLowerCase();

        if (active.containsKey(trackId)) {
            if (opener != null) opener.sendMessage(ChatColor.RED + "A race is already running on '" + trackId + "'.");
            return false;
        }

        MarbleTrack track = tracks.getTrack(trackId);
        if (track == null || track.size() < 2) {
            if (opener != null) opener.sendMessage(ChatColor.RED + "Track not found or not enough points.");
            return false;
        }

        openTracks.add(trackId);
        lobby.computeIfAbsent(trackId, k -> new ArrayList<>());

        if (opener != null) {
            opener.sendMessage(ChatColor.GREEN + "Track '" + trackId + "' is now OPEN for entries.");
            opener.sendMessage(ChatColor.GRAY + "Players can join via " + ChatColor.AQUA + "/md race");
        }
        return true;
    }

    public boolean close(Player closer, String trackId) {
        if (trackId == null || trackId.isBlank()) return false;
        trackId = trackId.toLowerCase();

        openTracks.remove(trackId);

        if (closer != null) {
            closer.sendMessage(ChatColor.YELLOW + "Track '" + trackId + "' is now CLOSED for entries.");
        }
        return true;
    }

    public boolean isOpen(String trackId) {
        if (trackId == null) return false;
        return openTracks.contains(trackId.toLowerCase());
    }

    public boolean isRunning(String trackId) {
        if (trackId == null) return false;
        return active.containsKey(trackId.toLowerCase());
    }

    public int lobbyCount(String trackId) {
        if (trackId == null) return 0;
        List<RaceEntry> list = lobby.get(trackId.toLowerCase());
        return list == null ? 0 : list.size();
    }

    public boolean hasEntry(String trackId, UUID playerId) {
        if (trackId == null || playerId == null) return false;
        List<RaceEntry> list = lobby.get(trackId.toLowerCase());
        if (list == null) return false;
        for (RaceEntry e : list) {
            if (playerId.equals(e.owner)) return true;
        }
        return false;
    }

    /** This player's own lobby entry for a track, or null if they haven't joined it (or the race already started). See RaceLoadoutGui. */
    public RaceEntry findEntry(String trackId, UUID playerId) {
        if (trackId == null || playerId == null) return null;
        List<RaceEntry> list = lobby.get(trackId.toLowerCase());
        if (list == null) return null;
        for (RaceEntry e : list) {
            if (playerId.equals(e.owner)) return e;
        }
        return null;
    }

    /**
     * This player's effective loadout for a track: their actual lobby
     * entry's choice if they have one there, otherwise their last-picked
     * preference (see setLoadout()) -- which is also how /md race test
     * picks up a loadout chosen through the same GUI without needing a
     * real lobby entry to hang it on. Defaults to BALANCED if neither
     * exists yet.
     */
    public RaceLoadout getLoadout(String trackId, UUID playerId) {
        RaceEntry entry = findEntry(trackId, playerId);
        if (entry != null) return entry.loadout;
        if (playerId == null) return RaceLoadout.BALANCED;
        return loadoutPreference.getOrDefault(playerId, RaceLoadout.BALANCED);
    }

    /**
     * Sets this player's chosen loadout -- both their preference (used as
     * the getLoadout() fallback, and by /md race test) and their real
     * lobby entry for this track, if they have one.
     */
    public void setLoadout(String trackId, UUID playerId, RaceLoadout loadout) {
        if (playerId == null || loadout == null) return;
        loadoutPreference.put(playerId, loadout);

        RaceEntry entry = findEntry(trackId, playerId);
        if (entry != null) entry.loadout = loadout;
    }

    public List<String> openTrackIds() {
        List<String> out = new ArrayList<>(openTracks);
        Collections.sort(out);
        return out;
    }

    // ----------------------------
    // Entries
    // ----------------------------

    public void enter(Player player, String trackId, ItemStack marbleItem) {
        if (player == null) return;
        if (trackId == null || trackId.isBlank()) return;

        trackId = trackId.toLowerCase();

        // ✅ NEW: must be open to join (this is the “tracks open so players can join” rule)
        if (!isOpen(trackId)) {
            player.sendMessage(ChatColor.RED + "That track is not open for entries.");
            player.sendMessage(ChatColor.GRAY + "Wait for an admin to open it, then use " + ChatColor.AQUA + "/md race");
            return;
        }

        if (active.containsKey(trackId)) {
            player.sendMessage(ChatColor.RED + "A race is already running on '" + trackId + "'.");
            return;
        }

        MarbleTrack track = tracks.getTrack(trackId);
        if (track == null || track.size() < 2) {
            player.sendMessage(ChatColor.RED + "Track not found or not enough points.");
            return;
        }

        if (!MarbleItem.isMarble(marbleItem)) {
            player.sendMessage(ChatColor.RED + "Hold a marble in your main hand first.");
            return;
        }

        MarbleData data = MarbleItem.read(marbleItem);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "Could not read marble data.");
            return;
        }

        UUID marbleId = data.getId();

        List<RaceEntry> list = lobby.computeIfAbsent(trackId, k -> new ArrayList<>());

        if (list.size() >= MAX_ENTRIES_PER_TRACK) {
            player.sendMessage(ChatColor.RED + "That race is full (" + MAX_ENTRIES_PER_TRACK + ").");
            return;
        }

        // prevent duplicate marble IDs in same lobby
        for (RaceEntry e : list) {
            if (e.marbleId.equals(marbleId)) {
                player.sendMessage(ChatColor.RED + "That marble is already entered on this track.");
                return;
            }
        }

        // one entry per player per track
        for (RaceEntry e : list) {
            if (e.owner.equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You are already entered on this track. Right-click it in /md race to leave.");
                return;
            }
        }

        ItemStack helmet = marbleItem.clone();
        helmet.setAmount(1);

        String marbleDisplayName = getMarbleDisplayName(helmet);

        list.add(new RaceEntry(player.getUniqueId(), marbleId, helmet, data, marbleDisplayName));

        player.sendMessage(ChatColor.GREEN + "Entered your marble into track '" + trackId + "'.");
        player.sendMessage(ChatColor.GRAY + "Entries: " + list.size() + "/" + MAX_ENTRIES_PER_TRACK);
    }

    public void leave(Player player, String trackId) {
        if (player == null) return;
        if (trackId == null || trackId.isBlank()) return;

        trackId = trackId.toLowerCase();

        if (active.containsKey(trackId)) {
            player.sendMessage(ChatColor.RED + "That race already started. You can't leave now.");
            return;
        }

        List<RaceEntry> list = lobby.get(trackId);

        if (list == null || list.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No entries exist for that track.");
            return;
        }

        boolean removed = list.removeIf(e -> e.owner.equals(player.getUniqueId()));
        if (removed) {
            player.sendMessage(ChatColor.YELLOW + "Removed your entry from '" + trackId + "'.");
            if (list.isEmpty()) lobby.remove(trackId);
        } else {
            player.sendMessage(ChatColor.RED + "You are not entered on that track.");
        }
    }

    public void clear(String trackId) {
        if (trackId == null) return;
        trackId = trackId.toLowerCase();

        if (active.containsKey(trackId)) {
            return;
        }

        lobby.remove(trackId);
    }

    public void listToPlayer(Player viewer, String trackId) {
        if (viewer == null) return;
        if (trackId == null || trackId.isBlank()) return;

        trackId = trackId.toLowerCase();

        List<RaceEntry> list = lobby.get(trackId);
        int count = (list == null) ? 0 : list.size();

        viewer.sendMessage(ChatColor.YELLOW + "Race entries for '" + trackId + "': " + ChatColor.WHITE + count);

        if (list == null || list.isEmpty()) return;

        for (RaceEntry e : list) {
            String name = viewer.getServer().getOfflinePlayer(e.owner).getName();
            if (name == null) name = e.owner.toString();

            viewer.sendMessage(ChatColor.GRAY + "- " + name
                    + ChatColor.DARK_GRAY + " | "
                    + ChatColor.AQUA + (e.marbleDisplayName != null ? e.marbleDisplayName : e.marbleId.toString()));
        }
    }

    /**
     * A real starting grid: staggered rows fanned across the track's actual
     * usable width at the start line, staggered further back row by row
     * along the track's own reverse direction -- not a circle, not a line
     * -- shared by start(), runTestRace(), and the tutorial's own races.
     */
    public List<Location> startingGrid(MarbleTrack track, int count) {
        TrackSpline spline = track.getRaceSpline();
        double maxSearch = (config != null) ? config.raceWallSearchRadius() : 2.5;
        double usableWidth = Math.max(GRID_SPACING, spline.measureWidthAt(0, maxSearch));
        return spline.startingGrid(Math.max(1, count), usableWidth, GRID_SPACING);
    }

    public void start(Player starter, String trackId) {
        if (starter == null) return;
        if (trackId == null || trackId.isBlank()) return;

        trackId = trackId.toLowerCase();
        final String finalTrackId = trackId;

        if (active.containsKey(trackId)) {
            starter.sendMessage(ChatColor.RED + "A race is already running on '" + trackId + "'.");
            return;
        }

        MarbleTrack track = tracks.getTrack(trackId);
        if (track == null || track.size() < 2) {
            starter.sendMessage(ChatColor.RED + "Track not found or not enough points.");
            return;
        }

        List<RaceEntry> list = lobby.get(trackId);
        if (list == null || list.isEmpty()) {
            starter.sendMessage(ChatColor.RED + "No entries for that track.");
            return;
        }

        RaceSession session = new RaceSession(trackId, starter.getUniqueId(), list);
        active.put(trackId, session);

        // Clear lobby + close track immediately -- no sneaking in another
        // entry while the grid/countdown below is still playing out.
        lobby.remove(trackId);
        openTracks.remove(trackId);

        starter.sendMessage(ChatColor.GREEN + "Starting race on '" + trackId + "' with " + list.size() + " marbles...");
        if (track.getLaps() > 1) {
            broadcastToSession(session, Component.text("This race is " + track.getLaps() + " laps.", NamedTextColor.GRAY));
        }

        // ✅ AUTO-WATCH: put all entered players into watch mode when race starts
        if (watch != null) {
            for (RaceEntry entry : list) {
                Player owner = Bukkit.getPlayer(entry.owner);
                if (owner != null && owner.isOnline()) {
                    watch.start(owner, trackId);
                }
            }
        }

        // Spawn every marble on its real starting-grid spot right away,
        // held motionless, so players actually see the grid lined up
        // instead of marbles popping into motion the instant /md race
        // start is typed. They're released together once the countdown
        // below finishes.
        int n = list.size();
        List<Location> grid = startingGrid(track, n);
        List<MarbleRunner> runners = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            RaceEntry entry = list.get(i);
            Location spawn = grid.get(i);
            MarbleStats effectiveStats = entry.loadout.applyTo(entry.data.getStats());

            MarbleRunner runner = new MarbleRunner(
                    track,
                    spawn,
                    entry.helmet,
                    effectiveStats,
                    track.getLaps(),
                    () -> {
                        activeRunnerByOwner.remove(entry.owner);
                        boostCharges.remove(entry.owner);
                        onFinish(finalTrackId, entry);
                    }
            );
            runner.hold();
            engine.addRunner(runner);
            runners.add(runner);

            activeRunnerByOwner.put(entry.owner, runner);

            // Deferred a tick: start() is commonly reached synchronously
            // from within a right-click handler (a "start" race sign --
            // see RaceSignListener), and vanilla resends a player's real
            // held-slot content as the trailing step of processing that
            // same click packet, regardless of anything a Bukkit event
            // handler did with it. That trailing resend happens right
            // after this loop returns control to it -- if the ability
            // items were shown synchronously here, on the same tick, it
            // would immediately overwrite them with whatever's really
            // there (the entrant's marble). Running one tick later
            // guarantees we're past that resend, so our packet is the one
            // that actually sticks. Harmless when start() was reached from
            // a plain command instead (no click, no trailing resend) --
            // just an imperceptible extra 1-tick delay there.
            UUID ownerId = entry.owner;
            int boostStat = effectiveStats.get(MarbleStat.BOOST);
            engine.scheduleDelayed(() -> {
                giveBoostItem(ownerId, boostStat);
                giveGlowItem(ownerId);
            }, 1L);
        }

        runStartCountdown(session, runners);
    }

    /** Hands the entrant a Glow item (see RaceGlowListener) -- purely cosmetic, no stat scaling. No-op if they're offline. */
    private void giveGlowItem(UUID ownerId) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) return;

        // Hotbar slot 9 (the one keyed to "9") is Bukkit inventory index 8
        // -- the hotbar is index 0-8, matching keys 1-9. Fixed on purpose
        // so it's always in the same place race to race.
        if (inventoryOverlay != null) {
            inventoryOverlay.show(owner, 8, RaceInventoryOverlay.TAG_GLOW, RaceGlowItem.create(engine.getPlugin()));
        } else {
            owner.getInventory().setItem(8, RaceGlowItem.create(engine.getPlugin()));
        }
    }

    /** Hands the entrant a Boost item charged for their marble's BOOST stat -- see RaceBoostListener. No-op if they're offline. */
    private void giveBoostItem(UUID ownerId, int boostStat) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) return;

        int charges = boostChargesForStat(boostStat);
        boostCharges.put(ownerId, charges);

        // Whatever hotbar slot the player already has selected -- not a
        // fixed one, so "right-click to boost" works the instant the race
        // goes with no fumbling to find it. setItemInMainHand (not
        // addItem) does the same for the real-item fallback below.
        int slot = owner.getInventory().getHeldItemSlot();

        if (inventoryOverlay != null) {
            inventoryOverlay.show(owner, slot, RaceInventoryOverlay.TAG_BOOST, RaceBoostItem.create(engine.getPlugin(), charges));
        } else {
            owner.getInventory().setItemInMainHand(RaceBoostItem.create(engine.getPlugin(), charges));
        }
    }

    /** 2 charges at BOOST 0 up to 6 at BOOST 100 -- even a low-BOOST marble gets a real, if small, budget to spend well. */
    private int boostChargesForStat(int boostStat) {
        double frac = Math.max(0.0, Math.min(1.0, boostStat / 100.0));
        return (int) Math.round(2 + frac * 4);
    }

    /** "On your marks... 3... 2... 1... GO!" -- then releases every held runner together. See start(). */
    private void runStartCountdown(RaceSession session, List<MarbleRunner> runners) {
        broadcastToSession(session, Component.text("On your marks...", NamedTextColor.YELLOW));
        for (int sec = COUNTDOWN_SECONDS; sec >= 1; sec--) {
            long delayTicks = (long) (COUNTDOWN_SECONDS - sec + 1) * 20L;
            String label = sec + "...";
            engine.scheduleDelayed(() -> broadcastToSession(session, Component.text(label, NamedTextColor.YELLOW)), delayTicks);
        }
        engine.scheduleDelayed(() -> {
            session.startMs = System.currentTimeMillis();
            for (MarbleRunner runner : runners) runner.release();
            broadcastToSession(session, Component.text("GO!", NamedTextColor.GREEN, TextDecoration.BOLD));
        }, COUNTDOWN_TOTAL_TICKS);
    }

    private void onFinish(String trackId, RaceEntry entry) {
        RaceSession session = active.get(trackId);
        if (session == null) return;

        if (session.finishedIds.contains(entry.marbleId)) return;

        long elapsed = System.currentTimeMillis() - session.startMs;
        session.finishedIds.add(entry.marbleId);
        session.finished.add(entry);
        session.finishTimes.put(entry.marbleId, elapsed);

        int place = session.finished.size();

        String ownerName = Bukkit.getOfflinePlayer(entry.owner).getName();
        if (ownerName == null) ownerName = entry.owner.toString();

        Component line = Component.text("#" + place + " finished: ", NamedTextColor.GRAY)
                .append(Component.text(ownerName, NamedTextColor.YELLOW))
                .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                .append(buildMarbleNameComponent(entry))
                .append(Component.text(")", NamedTextColor.DARK_GRAY))
                .append(Component.text(" — " + formatTime(elapsed), NamedTextColor.GREEN));

        broadcastToSession(session, line);

        if (session.finished.size() >= session.total) {
            broadcastResults(session);
            active.remove(trackId);
            releaseWatchers(session);
            if (outcomeListener != null) outcomeListener.onRaceFinished(trackId, List.copyOf(session.finished));
        }
    }

    /**
     * start() auto-enters every entrant into watch mode (see the AUTO-WATCH
     * block above) but never released them again -- players were getting
     * stuck in spectator mode (wrong gamemode, cleared inventory, never
     * teleported back) after every real race. Restores everyone a few
     * seconds after the results are posted, so they see the standings
     * first instead of being yanked out mid-read.
     */
    private void releaseWatchers(RaceSession session) {
        if (watch == null) return;
        engine.scheduleDelayed(() -> {
            for (UUID id : session.recipients) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline() && watch.isWatching(p)) {
                    watch.stop(p, true);
                }
            }
        }, 100L);
    }

    private void broadcastResults(RaceSession session) {
        Component header = Component.text("=== Race Results (" + session.trackId + ") ===", NamedTextColor.GOLD);
        broadcastToSession(session, header);

        for (int i = 0; i < session.finished.size(); i++) {
            RaceEntry e = session.finished.get(i);

            String ownerName = Bukkit.getOfflinePlayer(e.owner).getName();
            if (ownerName == null) ownerName = e.owner.toString();

            NamedTextColor medal = switch (i) {
                case 0 -> NamedTextColor.GOLD;
                case 1 -> NamedTextColor.GRAY;
                case 2 -> NamedTextColor.DARK_RED;
                default -> NamedTextColor.WHITE;
            };

            Long finishMs = session.finishTimes.get(e.marbleId);
            String timeStr = finishMs != null ? formatTime(finishMs) : "—";

            Component line = Component.text((i + 1) + ". ", medal)
                    .append(Component.text(ownerName, NamedTextColor.YELLOW))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(buildMarbleNameComponent(e))
                    .append(Component.text(" " + timeStr, NamedTextColor.GREEN));

            broadcastToSession(session, line);
        }
    }

    private void broadcastToSession(RaceSession session, Component msg) {
        if (session == null || msg == null) return;

        for (UUID u : session.recipients) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }

    private Component buildMarbleNameComponent(RaceEntry entry) {
        String name = (entry.marbleDisplayName != null && !entry.marbleDisplayName.isBlank())
                ? entry.marbleDisplayName
                : entry.marbleId.toString();
        return buildMarbleNameComponent(name, entry.data);
    }

    /** Public overload for callers that only have a display name + MarbleData, not a full RaceEntry (e.g. ScheduledRaceManager's AI runners). */
    public Component buildMarbleNameComponent(String displayName, MarbleData data) {
        String name = (displayName != null && !displayName.isBlank()) ? displayName : "Marble";
        Component hover = buildMarbleHover(displayName, data);

        return Component.text(name, NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(hover));
    }

    private Component buildMarbleHover(String displayName, MarbleData data) {
        String team = (data.getTeamKey() == null || data.getTeamKey().isBlank()) ? "Neutral" : data.getTeamKey();
        String rarity = (data.getRarity() == null) ? "COMMON" : data.getRarity().name();

        int speed = data.getStats().get(MarbleStat.SPEED);
        int accel = data.getStats().get(MarbleStat.ACCEL);
        int handling = data.getStats().get(MarbleStat.HANDLING);
        int stability = data.getStats().get(MarbleStat.STABILITY);
        int boost = data.getStats().get(MarbleStat.BOOST);

        Component c = Component.empty();

        String title = (displayName != null && !displayName.isBlank()) ? displayName : "Marble";

        c = c.append(Component.text(title, NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
                .append(Component.newline());

        c = c.append(Component.text("Team: ", NamedTextColor.GRAY))
                .append(Component.text(team, NamedTextColor.WHITE))
                .append(Component.newline());

        c = c.append(Component.text("Rarity: ", NamedTextColor.GRAY))
                .append(Component.text(rarity, NamedTextColor.WHITE))
                .append(Component.newline());

        c = c.append(Component.text("Level: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(data.getLevel()), NamedTextColor.WHITE))
                .append(Component.newline());

        c = c.append(Component.text("XP: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(data.getXp()), NamedTextColor.WHITE))
                .append(Component.newline());

        c = c.append(Component.text("----------------", NamedTextColor.DARK_GRAY))
                .append(Component.newline());

        c = c.append(Component.text("Speed: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(speed), NamedTextColor.WHITE)).append(Component.newline());
        c = c.append(Component.text("Accel: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(accel), NamedTextColor.WHITE)).append(Component.newline());
        c = c.append(Component.text("Handling: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(handling), NamedTextColor.WHITE)).append(Component.newline());
        c = c.append(Component.text("Stability: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(stability), NamedTextColor.WHITE)).append(Component.newline());
        c = c.append(Component.text("Boost: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(boost), NamedTextColor.WHITE));

        return c;
    }

    private String getMarbleDisplayName(ItemStack item) {
        if (item == null) return "Marble";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "Marble";
        String dn = meta.getDisplayName();
        if (dn == null || dn.isBlank()) return "Marble";
        return ChatColor.stripColor(dn);
    }

    // ------------------------------------------------------------
    // Public helper: build a single stats-driven runner without going
    // through the shared lobby/session system. Used by the tutorial's
    // isolated practice race (and safe for any other future one-off use).
    // ------------------------------------------------------------

    public MarbleRunner buildStatsRunner(MarbleTrack track, Location spawn, ItemStack helmet,
                                          MarbleData data, MarbleRunner.FinishListener listener) {
        // aiBoost=true -- nothing here is under a live player's real-time
        // click control (test-race AI racers, the tutorial's AI and player
        // marbles alike, ScheduledRaceManager's automated entries), so the
        // runner rolls for its own boosts off its BOOST stat instead of
        // waiting on a right-click that will never come. See MarbleRunner's
        // aiBoost constructor javadoc.
        return new MarbleRunner(track, spawn, helmet, data.getStats(), track.getLaps(), listener, true);
    }

    // ------------------------------------------------------------
    // TEST RACE -- debug-only physics testing, bypasses the lobby/open/enter
    // flow entirely and drops runners straight onto the engine. Gated on
    // config.debugEnabled() so it's inert on public builds unless an admin
    // has explicitly flipped debug mode on with /md debug. This version has
    // no per-viewer visibility (that was a TrackSpline-era feature that got
    // reverted), so test marbles are visible to everyone nearby, same as a
    // real race.
    // ------------------------------------------------------------

    public void runTestRace(Player admin, String trackId, int aiCount, boolean includeSelf) {
        if (config == null || !config.debugEnabled()) {
            admin.sendMessage(ChatColor.RED + "Test races require debug mode -- run /md debug to enable it first.");
            return;
        }
        if (trackId == null || trackId.isBlank()) {
            admin.sendMessage(ChatColor.RED + "Usage: /md race test <trackId> [aiCount] [noself]");
            return;
        }
        trackId = trackId.toLowerCase();

        // Loadout comes from the same GUI a real lobby entry uses (see
        // getLoadout()/RaceLoadoutGui) -- open it with /md race test
        // loadout <trackId> to set one before running the test.
        RaceLoadout loadout = getLoadout(trackId, admin.getUniqueId());

        MarbleTrack track = tracks.getTrack(trackId);
        if (track == null || track.size() < 2) {
            admin.sendMessage(ChatColor.RED + "Track '" + trackId + "' doesn't exist or needs at least 2 points.");
            return;
        }

        aiCount = Math.max(0, Math.min(aiCount, MAX_ENTRIES_PER_TRACK - 1));

        ItemStack selfMarble = null;
        MarbleData selfData = null;
        if (includeSelf) {
            ItemStack held = admin.getInventory().getItemInMainHand();
            if (MarbleItem.isMarble(held)) {
                selfData = MarbleItem.read(held);
                if (selfData != null) {
                    selfMarble = held.clone();
                    selfMarble.setAmount(1);
                }
            }
            if (selfMarble == null) {
                admin.sendMessage(ChatColor.YELLOW + "You're not holding a marble -- running AI-only.");
                includeSelf = false;
            }
        }

        int totalCount = aiCount + (includeSelf ? 1 : 0);
        if (totalCount == 0) {
            admin.sendMessage(ChatColor.RED + "Nothing to race -- pass an AI count or hold a marble.");
            return;
        }

        Location start = track.getPoint(0).clone();

        // Same auto-watch real races use (see start() above) so the admin is
        // actually standing at the track to see it; fall back to a plain
        // teleport if no watch anchor is configured for this track yet.
        if (watch != null) watch.start(admin, trackId);
        if (watch == null || !watch.isWatching(admin)) {
            admin.teleport(start.clone().add(0, 2.0, 0));
            admin.sendMessage(ChatColor.GRAY + "(Tip: run /md track setwatch " + trackId + " for a proper spectator camera.)");
        }

        admin.sendMessage(ChatColor.YELLOW + "[TEST] Launching " + totalCount + " marble(s) on '" + trackId + "' (debug only, not saved).");

        // runTestRace() puts the admin into watch mode above (same as a
        // real race) but, unlike a real race, had nothing tracking when
        // every runner was actually done -- the admin was never released:
        // stuck in spectator mode (wrong gamemode, cleared inventory) with
        // no teleport back, forever. This mirrors the same fix start()
        // already got: release a few seconds after the last finish, once
        // every runner (self + AI) is accounted for.
        final int finalTotalCount = totalCount;
        java.util.concurrent.atomic.AtomicInteger finishedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        Runnable onAnyFinish = () -> {
            if (finishedCount.incrementAndGet() >= finalTotalCount) {
                engine.scheduleDelayed(() -> {
                    if (watch != null && watch.isWatching(admin)) {
                        watch.stop(admin, true);
                    }
                }, 100L);
            }
        };

        List<Location> grid = startingGrid(track, totalCount);
        int slot = 0;

        if (includeSelf) {
            UUID adminId = admin.getUniqueId();
            MarbleStats effectiveStats = loadout.applyTo(selfData.getStats());

            MarbleRunner selfRunner = new MarbleRunner(track, grid.get(slot), selfMarble, effectiveStats, track.getLaps(), () -> {
                activeRunnerByOwner.remove(adminId);
                boostCharges.remove(adminId);
                admin.sendMessage(ChatColor.GREEN + "[TEST] Your marble finished!");
                onAnyFinish.run();
            });
            engine.addRunner(selfRunner);
            slot++;

            // Test races skipped the starting-grid hold/countdown real races
            // get, so there's no natural "everything's given out, go" beat
            // to hang this on -- give the item the instant the runner
            // exists instead, same as a real race does once it releases.
            activeRunnerByOwner.put(adminId, selfRunner);
            // Deferred a tick -- see the matching comment in start(). Test
            // races are usually launched from a typed command (no trailing
            // resend risk), but this is a cheap, always-safe guard against
            // the same click-triggered clobbering if one ever launches one
            // synchronously from an interact handler instead.
            int testBoostStat = effectiveStats.get(MarbleStat.BOOST);
            engine.scheduleDelayed(() -> {
                giveBoostItem(adminId, testBoostStat);
                giveGlowItem(adminId);
            }, 1L);
            if (loadout != RaceLoadout.BALANCED) {
                admin.sendMessage(ChatColor.GRAY + "[TEST] Loadout: " + ChatColor.YELLOW + loadout.label());
            }
        }

        Random rng = new Random();
        for (int i = 0; i < aiCount; i++) {
            int mid = 45 + rng.nextInt(20); // 45-64 per stat, mid-tier
            MarbleStats aiStats = new MarbleStats(mid, mid, mid, mid, mid);
            MarbleData aiData = new MarbleData(
                    UUID.randomUUID(), "test_ai", "ai", MarbleRarity.COMMON,
                    aiStats, null, System.currentTimeMillis(), 0, 0
            );

            int racerNumber = i + 1;
            // A marker armor stand with no helmet renders as literally
            // nothing, so give AI runners a plain default head to equip.
            ItemStack aiHelmet = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta aiHelmetMeta = aiHelmet.getItemMeta();
            if (aiHelmetMeta != null) {
                aiHelmetMeta.setDisplayName(ChatColor.GRAY + "AI Racer " + racerNumber);
                aiHelmet.setItemMeta(aiHelmetMeta);
            }

            MarbleRunner aiRunner = buildStatsRunner(track, grid.get(slot), aiHelmet, aiData, () -> {
                admin.sendMessage(ChatColor.GRAY + "[TEST] AI Racer " + racerNumber + " finished.");
                onAnyFinish.run();
            });
            engine.addRunner(aiRunner);
            slot++;
        }
    }

    public static String formatTime(long ms) {
        long totalTenths = ms / 100;
        long tenths = totalTenths % 10;
        long totalSeconds = ms / 1000;
        long seconds = totalSeconds % 60;
        long minutes = totalSeconds / 60;
        if (minutes > 0) {
            return minutes + ":" + String.format("%02d", seconds) + "." + tenths;
        }
        return seconds + "." + tenths + "s";
    }

    // ------------------------------------------------------------
    // Data
    // ------------------------------------------------------------

    public static final class RaceEntry {
        public final UUID owner;
        public final UUID marbleId;
        public final ItemStack helmet;

        public final MarbleData data;
        public final String marbleDisplayName;

        // Mutable, unlike everything else here -- set (and changed) via
        // RaceLoadoutGui any time before the race actually starts. Defaults
        // to BALANCED so an entrant who never opens the loadout menu races
        // on their marble's raw stats, same as before this existed.
        public RaceLoadout loadout = RaceLoadout.BALANCED;

        public RaceEntry(UUID owner, UUID marbleId, ItemStack helmet,
                         MarbleData data, String marbleDisplayName) {
            this.owner = owner;
            this.marbleId = marbleId;
            this.helmet = helmet;
            this.data = data;
            this.marbleDisplayName = marbleDisplayName;
        }
    }

    private static final class RaceSession {
        final String trackId;
        final int total;
        // Set to the actual "go" moment once the starting-grid countdown
        // finishes (see start()) -- not construction time, so finish times
        // don't include however long marbles sat waiting on the grid.
        long startMs = System.currentTimeMillis();

        final List<RaceEntry> finished = new ArrayList<>();
        final Set<UUID> finishedIds = new HashSet<>();
        final Map<UUID, Long> finishTimes = new LinkedHashMap<>();

        final Set<UUID> recipients = new HashSet<>();

        RaceSession(String trackId, UUID starter, List<RaceEntry> entries) {
            this.trackId = trackId;
            this.total = entries.size();

            for (RaceEntry e : entries) recipients.add(e.owner);
            if (starter != null) recipients.add(starter);
        }
    }
}
