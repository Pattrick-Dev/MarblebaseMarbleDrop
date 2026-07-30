package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.MdConfig;
import me.pattrick.marbledrop.tutorial.TutorialManager;
import me.pattrick.marbledrop.marble.MarbleData;
import me.pattrick.marbledrop.marble.MarbleItem;
import me.pattrick.marbledrop.marble.MarbleStat;
import me.pattrick.marbledrop.marble.MarbleStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.HoverEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class RaceManager implements Listener {

    public static final int MAX_ENTRIES_PER_TRACK = 16;

    private final TrackManager tracks;
    private final MarbleRaceEngine engine;
    private final MdConfig config;

    // ✅ Watch manager (optional)
    private RaceWatchManager watch;

    // Optional: wired in from Main.java after TutorialManager exists (mirrors
    // setWatchManager below). Gates real race entry on tutorial completion --
    // fails open (no gate) if never wired, same as watch being null.
    private TutorialManager tutorialManager;

    // trackId -> active session
    private final Map<String, RaceSession> active = new HashMap<>();

    // trackId -> lobby entries
    private final Map<String, List<RaceEntry>> lobby = new HashMap<>();

    // ✅ NEW: trackId -> OPEN state
    private final Set<String> openTracks = new HashSet<>();

    /**
     * Fired whenever a race started via {@link #start} finishes, for any
     * external code (currently just ScheduledRaceManager) that wants to react
     * to a race's outcome -- e.g. awarding Dust, running a server-wide
     * announcement -- without RaceManager itself knowing anything about
     * dust/scheduling. A single global listener (not per-race): the listener
     * is responsible for checking the trackId against whatever it cares about
     * and ignoring the rest (e.g. an admin's manually-started race on some
     * other track).
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

    /** Defensive copy of a track's current lobby -- read-only peek, doesn't affect join validation. */
    public List<RaceEntry> lobbySnapshot(String trackId) {
        if (trackId == null) return List.of();
        List<RaceEntry> list = lobby.get(trackId.toLowerCase());
        return list == null ? List.of() : List.copyOf(list);
    }

    // ✅ allow Main to wire watch manager without redesigning flow
    public void setWatchManager(RaceWatchManager watch) {
        this.watch = watch;
    }

    /** Wired in from Main.java once TutorialManager exists (constructed after RaceManager today). */
    public void setTutorialManager(TutorialManager tutorialManager) {
        this.tutorialManager = tutorialManager;
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

        // Real races (this is the one funnel every join path -- /md join, the
        // /md race GUI, race signs -- already goes through) are gated behind
        // finishing the tutorial. The tutorial's own practice race bypasses
        // this method entirely (it builds runners directly), so this only
        // ever blocks *real* races, never the tutorial itself.
        if (tutorialManager != null && !tutorialManager.hasCompleted(player)) {
            player.sendMessage(ChatColor.RED + "Finish the tutorial before racing for real.");
            player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.AQUA + "/md tutorial start" + ChatColor.GRAY + " to begin.");
            return;
        }

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
                player.sendMessage(ChatColor.RED + "You are already entered on this track. Type /md leave to leave.");
                return;
            }
        }

        ItemStack helmet = marbleItem.clone();
        helmet.setAmount(1);

        String marbleDisplayName = getMarbleDisplayName(helmet);

        // Stats-driven speed
        double speedPerTick = computeSpeedPerTick(data);

        // Option C personality (you already wired chaos/aggression in MarbleRunner)
        double chaos = computeChaos(data);
        double aggression = computeAggression(data);

        // Take the real marble out of their hand now that every check has
        // passed -- this is what makes "one marble per race" actually
        // enforced (they physically can't offer the same marble to a
        // second race while it's held here) rather than just checked at
        // entry time. Returned via refundEntry() the moment they leave,
        // the lobby is cleared, or they finish the race.
        takeMarbleFromHand(player);

        list.add(new RaceEntry(player.getUniqueId(), marbleId, helmet, data, marbleDisplayName, speedPerTick, chaos, aggression));

        player.sendMessage(ChatColor.GREEN + "Entered your marble into track '" + trackId + "'.");
        player.sendMessage(ChatColor.GRAY + "It's held until the race ends -- entries: " + list.size() + "/" + MAX_ENTRIES_PER_TRACK);
        player.sendMessage(ChatColor.GRAY + "Changed your mind? Type " + ChatColor.AQUA + "/md leave" + ChatColor.GRAY + " to get your marble back.");
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

        RaceEntry toRemove = null;
        for (RaceEntry e : list) {
            if (e.owner.equals(player.getUniqueId())) {
                toRemove = e;
                break;
            }
        }

        if (toRemove != null) {
            list.remove(toRemove);
            refundEntry(toRemove);
            player.sendMessage(ChatColor.YELLOW + "Removed your entry from '" + trackId + "' and returned your marble.");
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

        List<RaceEntry> removed = lobby.remove(trackId);
        if (removed != null) {
            for (RaceEntry e : removed) refundEntry(e);
        }
    }

    /**
     * Pulls a single entry out of a track's lobby WITHOUT refunding its
     * marble -- for ScheduledRaceManager's AI-fill path, which bypasses
     * start()'s own session/lobby handling to build its race directly but
     * still needs the caller's already-captured helmet/data. The caller
     * takes over responsibility for eventually returning the marble (see
     * refundMarble) once its own race concludes. Returns null if no such
     * entry exists.
     */
    public RaceEntry takeEntryForExternalRace(String trackId, UUID owner) {
        if (trackId == null || owner == null) return null;
        trackId = trackId.toLowerCase();

        List<RaceEntry> list = lobby.get(trackId);
        if (list == null) return null;

        RaceEntry found = null;
        for (RaceEntry e : list) {
            if (e.owner.equals(owner)) {
                found = e;
                break;
            }
        }

        if (found != null) {
            list.remove(found);
            if (list.isEmpty()) lobby.remove(trackId);
        }
        return found;
    }

    /** Gives an entry's marble back -- public so ScheduledRaceManager can return marbles for races it runs outside RaceManager's own lobby/session flow. */
    public void refundMarble(RaceEntry entry) {
        refundEntry(entry);
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

    /** starter may be null for a system-triggered start (see ScheduledRaceManager) -- messages are just skipped, same as open()/close(). */
    public void start(Player starter, String trackId) {
        if (trackId == null || trackId.isBlank()) return;

        trackId = trackId.toLowerCase();
        final String finalTrackId = trackId;

        if (active.containsKey(trackId)) {
            if (starter != null) starter.sendMessage(ChatColor.RED + "A race is already running on '" + trackId + "'.");
            return;
        }

        MarbleTrack track = tracks.getTrack(trackId);
        if (track == null || track.size() < 2) {
            if (starter != null) starter.sendMessage(ChatColor.RED + "Track not found or not enough points.");
            return;
        }

        List<RaceEntry> list = lobby.get(trackId);
        if (list == null || list.isEmpty()) {
            if (starter != null) starter.sendMessage(ChatColor.RED + "No entries for that track.");
            return;
        }

        RaceSession session = new RaceSession(trackId, starter == null ? null : starter.getUniqueId(), list);
        active.put(trackId, session);

        if (starter != null) {
            starter.sendMessage(ChatColor.GREEN + "Starting race on '" + trackId + "' with " + list.size() + " marbles...");
        }

        // ✅ AUTO-WATCH: put all entered players into watch mode when race starts,
        // so they're in position for the countdown below (not still standing
        // wherever they were when they entered the lobby).
        if (watch != null) {
            for (RaceEntry entry : list) {
                Player owner = Bukkit.getPlayer(entry.owner);
                if (owner != null && owner.isOnline()) {
                    watch.start(owner, trackId);
                }
            }
        }

        // once started: clear lobby + close track immediately, so no one can
        // join mid-countdown.
        lobby.remove(trackId);
        openTracks.remove(trackId);

        runCountdown(session, track, list, finalTrackId);
    }

    private static final int COUNTDOWN_SECONDS = 3;

    /** "Get ready..." -> 3... 2... 1... -> GO!, then actually launches the marbles. */
    private void runCountdown(RaceSession session, MarbleTrack track, List<RaceEntry> list, String trackId) {
        Plugin plugin = JavaPlugin.getProvidingPlugin(getClass());

        broadcastToSession(session, Component.text("Get ready...", NamedTextColor.YELLOW));
        playSoundToSession(session, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);

        for (int i = COUNTDOWN_SECONDS; i >= 1; i--) {
            int secondsLeft = i;
            long delayTicks = (long) (COUNTDOWN_SECONDS - i + 1) * 20L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                broadcastToSession(session, Component.text(secondsLeft + "...", NamedTextColor.YELLOW, TextDecoration.BOLD));
                playSoundToSession(session, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }, delayTicks);
        }

        long goDelay = (long) (COUNTDOWN_SECONDS + 1) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            broadcastToSession(session, Component.text("GO!", NamedTextColor.GREEN, TextDecoration.BOLD));
            playSoundToSession(session, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            launchRunners(session, track, list, trackId);
        }, goDelay);
    }

    private void launchRunners(RaceSession session, MarbleTrack track, List<RaceEntry> list, String trackId) {
        // The elapsed time shown in results is measured from here (actual
        // launch), not from when /md race start was run -- otherwise every
        // finish time would be inflated by the countdown length.
        session.startMs = System.currentTimeMillis();

        int n = list.size();

        TrackPhysics physics = createPhysics(track);
        List<Location> grid = startingGrid(track, n);

        for (int i = 0; i < n; i++) {
            RaceEntry entry = list.get(i);
            Location spawn = grid.get(i);

            MarbleRunner runner = new MarbleRunner(
                    physics,
                    spawn,
                    entry.helmet,
                    entry.speedPerTick,
                    entry.chaos,
                    entry.aggression,
                    () -> onFinish(trackId, entry)
            );

            engine.addRunner(runner);
        }
    }

    private void onFinish(String trackId, RaceEntry entry) {
        RaceSession session = active.get(trackId);
        if (session == null) return;

        if (session.finishedIds.contains(entry.marbleId)) return;

        long elapsed = System.currentTimeMillis() - session.startMs;
        session.finishedIds.add(entry.marbleId);
        session.finished.add(entry);
        session.finishTimes.put(entry.marbleId, elapsed);

        // NOT refunded here -- watch.start() (see start()) snapshotted their
        // inventory AFTER the marble was already taken in enter(), so giving
        // it back this early just gets silently wiped out when
        // scheduleReleaseWatchers() restores that snapshot later. Refunded
        // there instead, right after the restore, not before.

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
            scheduleReleaseWatchers(session);
            if (outcomeListener != null) outcomeListener.onRaceFinished(trackId, List.copyOf(session.finished));
        }
    }

    // ~5s so everyone has a moment to read the final standings before being
    // returned to normal -- this is the fix for players getting stuck in
    // adventure mode/cleared inventory forever after a race: nothing used to
    // call watch.stop() for them once the race ended.
    private static final long RESULTS_DISPLAY_TICKS = 100L;

    private void scheduleReleaseWatchers(RaceSession session) {
        Plugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (watch != null) {
                for (UUID id : session.recipients) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline() && watch.isWatching(p)) {
                        watch.stop(p, true);
                    }
                }
            }

            // Refund AFTER the watch restore above, not before -- see the
            // comment in onFinish() for why doing this earlier silently
            // loses the marble to that restore.
            for (RaceEntry entry : session.allEntries) {
                refundEntry(entry);
            }
        }, RESULTS_DISPLAY_TICKS);
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

    private void playSoundToSession(RaceSession session, Sound sound, float volume, float pitch) {
        if (session == null) return;

        for (UUID u : session.recipients) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                p.playSound(p.getLocation(), sound, volume, pitch);
            }
        }
    }

    // ------------------------------------------------------------
    // Marble take/return -- entering takes the real marble out of the
    // player's hand (see enter()) so "one marble per race" is actually
    // enforced, not just checked once at entry time. Every path that
    // removes an entry without it finishing a race (leave/clear/purge)
    // refunds it here; if the owner is offline at that exact moment, it's
    // queued to race-pending-marbles.yml and handed back on their next
    // join instead of being lost -- the same disk-safety-net idea
    // RaceWatchManager already uses for inventory-across-logout safety.
    // ------------------------------------------------------------

    private void takeMarbleFromHand(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType().isAir()) return;

        if (mainHand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            mainHand.setAmount(mainHand.getAmount() - 1);
        }
    }

    private void refundEntry(RaceEntry entry) {
        if (entry == null || entry.helmet == null) return;
        ItemStack marble = entry.helmet.clone();

        Player owner = Bukkit.getPlayer(entry.owner);
        if (owner != null && owner.isOnline()) {
            giveOrDrop(owner, marble);
        } else {
            queuePendingReturn(entry.owner, marble);
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack left : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    private File pendingReturnsFile() {
        Plugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        return new File(plugin.getDataFolder(), "race-pending-marbles.yml");
    }

    private void queuePendingReturn(UUID owner, ItemStack marble) {
        Plugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        File file = pendingReturnsFile();

        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            if (!file.exists()) file.createNewFile();

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String key = owner.toString();

            List<ItemStack> existing = new ArrayList<>();
            List<?> raw = cfg.getList(key);
            if (raw != null) {
                for (Object o : raw) {
                    if (o instanceof ItemStack is) existing.add(is);
                }
            }
            existing.add(marble);
            cfg.set(key, existing);
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[MarbleDrop] Failed to queue pending race marble return: " + e.getMessage());
        }
    }

    /** Hands back any marbles queued while this player was offline (see queuePendingReturn). */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        File file = pendingReturnsFile();
        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String key = player.getUniqueId().toString();
        List<?> raw = cfg.getList(key);
        if (raw == null || raw.isEmpty()) return;

        for (Object o : raw) {
            if (o instanceof ItemStack is) giveOrDrop(player, is);
        }

        cfg.set(key, null);
        try {
            cfg.save(file);
        } catch (IOException ex) {
            JavaPlugin.getProvidingPlugin(getClass()).getLogger()
                    .severe("[MarbleDrop] Failed to clear pending race marble returns: " + ex.getMessage());
        }

        player.sendMessage(ChatColor.YELLOW + "A marble you had entered into a race has been returned to you.");
    }

    private Component buildMarbleNameComponent(RaceEntry entry) {
        return buildMarbleNameComponent(entry.marbleDisplayName, entry.data);
    }

    /**
     * A hoverable marble name for a results/leaderboard line -- shows team,
     * rarity, level, XP, and all 5 stats on hover. Shared by real races
     * (see broadcastResults) and the tutorial's practice-race results, so
     * both display marble stats the same way.
     */
    public Component buildMarbleNameComponent(String displayName, MarbleData data) {
        String name = (displayName != null && !displayName.isBlank())
                ? displayName
                : "Marble";

        Component hover = buildMarbleHover(displayName, data);

        return Component.text(name, NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(hover));
    }

    private Component buildMarbleHover(RaceEntry entry) {
        return buildMarbleHover(entry.marbleDisplayName, entry.data);
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

        String title = (displayName != null && !displayName.isBlank())
                ? displayName
                : "Marble";

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
    // Public helpers: build a physics world + stats-driven runners
    // without going through the shared lobby/session system. Used by
    // the tutorial's isolated practice race (and safe for any other
    // future one-off use). Callers that spawn several runners for the
    // same one-off race (e.g. a player + AI opponents) should build
    // ONE TrackPhysics via createPhysics() and pass it to every
    // buildStatsRunner() call for that race, so those marbles actually
    // collide with each other.
    // ------------------------------------------------------------

    /**
     * Force-despawns every currently active marble runner (any track/session)
     * and releases anyone still parked in watch mode from those sessions --
     * this is meant to be the admin's "get everyone unstuck" escape hatch, so
     * it has to tear down the same state onFinish() normally would, not just
     * the physics runners.
     */
    public void purgeAllRunners() {
        engine.clearAll();

        for (RaceSession session : active.values()) {
            // A purged session never reaches scheduleReleaseWatchers (that
            // only runs once the race finishes naturally), so nobody in it
            // has been refunded yet regardless of whether they'd already
            // crossed the finish line -- refund everyone. Watch is stopped
            // first, same ordering reason as scheduleReleaseWatchers: their
            // inventory snapshot predates the marble being taken, so
            // restoring it before refunding would just wipe the refund out.
            if (watch != null) {
                for (UUID id : session.recipients) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline() && watch.isWatching(p)) {
                        watch.stop(p, true);
                    }
                }
            }

            for (RaceEntry entry : session.allEntries) {
                refundEntry(entry);
            }
        }

        active.clear();
    }

    public TrackPhysics createPhysics(MarbleTrack track) {
        return new TrackPhysics(track.getSpline(), config.raceWallSearchRadius());
    }

    /**
     * Evenly spaced starting positions across the track's width at its start
     * line, staggering into further-back rows once one is full. Marble bodies
     * have real size now, so spawning them isn't just cosmetic anymore --
     * cramming them into a tight circle (as the old point-to-point movement
     * system safely could) leaves them overlapping, which the physics then
     * spends the first several ticks violently resolving instead of racing.
     * Width is measured against the track's real blocks (see
     * TrackPhysics.measureWidthAt), same as the walls themselves, rather than
     * a configured number that has to be kept in sync with what's built.
     */
    public List<Location> startingGrid(MarbleTrack track, int count) {
        double maxSearch = config.raceWallSearchRadius();
        double realWidth = TrackPhysics.measureWidthAt(track.getSpline(), 0, maxSearch);
        double spacing = TrackPhysics.MARBLE_RADIUS * 2 + 0.15;
        double usableWidth = Math.max(spacing, realWidth - TrackPhysics.MARBLE_RADIUS * 2);
        return track.getSpline().startingGrid(count, usableWidth, spacing);
    }

    public MarbleRunner buildStatsRunner(TrackPhysics physics, Location spawn, ItemStack helmet,
                                          MarbleData data, MarbleRunner.FinishListener listener) {
        return buildStatsRunner(physics, spawn, helmet, data, listener, null);
    }

    /**
     * @param viewer When non-null, this marble is visible only to them
     *               (see MarbleRunner) instead of everyone nearby -- used
     *               by the tutorial's practice race so concurrent racers
     *               never see each other's marbles.
     */
    public MarbleRunner buildStatsRunner(TrackPhysics physics, Location spawn, ItemStack helmet,
                                          MarbleData data, MarbleRunner.FinishListener listener, Player viewer) {
        double speedPerTick = computeSpeedPerTick(data);
        double chaos = computeChaos(data);
        double aggression = computeAggression(data);
        return new MarbleRunner(physics, spawn, helmet, speedPerTick, chaos, aggression, listener, viewer);
    }

    // ------------------------------------------------------------
    // Speed logic + Option C personality
    // ------------------------------------------------------------

    private double computeSpeedPerTick(MarbleData data) {
        MarbleStats stats = data.getStats();

        int speed = stats.get(MarbleStat.SPEED);
        int accel = stats.get(MarbleStat.ACCEL);
        int handling = stats.get(MarbleStat.HANDLING);
        int stability = stats.get(MarbleStat.STABILITY);
        int boost = stats.get(MarbleStat.BOOST);

        double base = 0.014;

        // Stats run 1-100 (see StatRoller). These coefficients are scaled so
        // the full range spans the clamp below smoothly -- the previous
        // coefficients (0.00038/0.00030/0.00012) put a mid-40s speed/accel/
        // handling marble (well within even a COMMON roll) already past the
        // old 0.030 ceiling, so almost every marble saturated to the same
        // speed regardless of stats and the race outcome barely reflected
        // them at all.
        double statBoost =
                (speed * 0.0001) +
                        (accel * 0.00008) +
                        (handling * 0.00003);

        double variance = 0.0012;
        double stab = clamp01(stability / 100.0);
        double randomness = (Math.random() - 0.5) * (variance * (1.05 - 0.85 * stab));

        double boostChance = clamp01(boost * 0.008);
        double boostBonus = (Math.random() < boostChance) ? 0.00055 : 0;

        double finalSpeed = base + statBoost + randomness + boostBonus;

        if (finalSpeed < 0.010) finalSpeed = 0.010;
        if (finalSpeed > 0.036) finalSpeed = 0.036;

        return finalSpeed;
    }

    private double computeChaos(MarbleData data) {
        MarbleStats s = data.getStats();
        double stab = clamp01(s.get(MarbleStat.STABILITY) / 100.0);
        double hand = clamp01(s.get(MarbleStat.HANDLING) / 100.0);

        double chaos = 0.55 - (stab * 0.28) - (hand * 0.18);
        return clamp(chaos, 0.12, 0.65);
    }

    private double computeAggression(MarbleData data) {
        MarbleStats s = data.getStats();
        double b = clamp01(s.get(MarbleStat.BOOST) / 100.0);
        double a = clamp01(s.get(MarbleStat.ACCEL) / 100.0);
        double sp = clamp01(s.get(MarbleStat.SPEED) / 100.0);

        double aggro = 0.35 + (b * 0.30) + (a * 0.20) + (sp * 0.10);
        return clamp(aggro, 0.20, 0.95);
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
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

        public final double speedPerTick;
        public final double chaos;
        public final double aggression;

        public RaceEntry(UUID owner, UUID marbleId, ItemStack helmet,
                         MarbleData data, String marbleDisplayName,
                         double speedPerTick, double chaos, double aggression) {
            this.owner = owner;
            this.marbleId = marbleId;
            this.helmet = helmet;
            this.data = data;
            this.marbleDisplayName = marbleDisplayName;
            this.speedPerTick = speedPerTick;
            this.chaos = chaos;
            this.aggression = aggression;
        }
    }

    private static final class RaceSession {
        final String trackId;
        final int total;
        long startMs; // set in launchRunners(), once the countdown finishes and marbles actually start moving

        // Every entry that started this race, kept around (not just those
        // that already finished) so a force-purge (see purgeAllRunners)
        // can still refund marbles for anyone who hadn't crossed the line yet.
        final List<RaceEntry> allEntries;

        final List<RaceEntry> finished = new ArrayList<>();
        final Set<UUID> finishedIds = new HashSet<>();
        final Map<UUID, Long> finishTimes = new LinkedHashMap<>();

        final Set<UUID> recipients = new HashSet<>();

        RaceSession(String trackId, UUID starter, List<RaceEntry> entries) {
            this.trackId = trackId;
            this.total = entries.size();
            this.allEntries = List.copyOf(entries);

            for (RaceEntry e : entries) recipients.add(e.owner);
            if (starter != null) recipients.add(starter);
        }
    }
}
