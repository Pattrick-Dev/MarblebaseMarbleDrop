package me.pattrick.marbledrop.races;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.pattrick.marbledrop.MdConfig;
import me.pattrick.marbledrop.marble.MarbleData;
import me.pattrick.marbledrop.marble.MarbleRarity;
import me.pattrick.marbledrop.marble.MarbleStats;
import me.pattrick.marbledrop.progression.DustManager;
import me.pattrick.marbledrop.progression.infusion.heads.HeadEntry;
import me.pattrick.marbledrop.progression.infusion.heads.HeadPool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Runs a server race at each of {@link MdConfig#scheduledRaceDailyTimes()}
 * (fixed clock times, server-local) - races happen ONLY at those specific
 * times, never on any kind of recurring interval. The entry window for a
 * race is always open: the moment one race concludes (or the plugin starts),
 * the next one opens immediately and stays open right up until the next
 * configured daily-time, at which point entries close and it runs - so
 * there's no idle "nothing joinable" gap, but races still only ever
 * physically happen at the 4 (or however many) configured times. A random
 * eligible track (see MarbleTrack#isAutoRaceEligible, set via
 * {@code /md track autorace}) opens for entries, a boss bar + chat reminders
 * (at each of {@link MdConfig#scheduledRaceAnnounceMinutesBefore()}) count
 * down to that close time, and at zero the race runs.
 * <p>
 * Three outcomes depending on how many real players joined via {@code /md join}:
 * <ul>
 *   <li><b>2+</b>: handed straight to {@link RaceManager#start} exactly like an
 *   admin-triggered race - {@link RaceManager.OutcomeListener} picks up the
 *   finish here to award Dust and announce server-wide.</li>
 *   <li><b>1</b>: RaceManager's lobby is bypassed (it has no concept of AI
 *   entries) in favor of building the race directly via
 *   {@link RaceManager#startingGrid}/{@link RaceManager#buildStatsRunner} --
 *   the same low-level pattern
 *   TutorialRaceService uses for its solo-vs-AI practice race - with AI
 *   filling the rest of the field, behind the same 3-2-1-GO countdown and
 *   live per-place finish tower a normal race gets. Reduced Dust, and only
 *   if the human actually won.</li>
 *   <li><b>0</b>: same direct-build path, but AI-only - a pure showcase with
 *   no Dust and no forced teleports (there's no human to return anywhere).</li>
 * </ul>
 */
public final class ScheduledRaceManager implements Listener {

    private final Plugin plugin;
    private final MdConfig config;
    private final TrackManager tracks;
    private final RaceManager races;
    private final RaceWatchManager watch;
    private final MarbleRaceEngine engine;
    private final DustManager dustManager;
    private final HeadPool headPool;

    private final Random rng = new Random();

    // ~5s after enable, so the very first open isn't racing plugin startup.
    private static final long INITIAL_DELAY_TICKS = 100L;
    // Buffer between one race concluding and the next one opening - just
    // long enough for that race's own result messages to land before the
    // next "opens for entries" announcement starts.
    private static final long REOPEN_DELAY_TICKS = 200L;
    // Floor on a freshly-opened entry window, in case runCycle() ever fires
    // right at the exact instant a daily-time hits (millisUntilNextDailyTime()
    // would otherwise return ~0) - keeps the boss bar/countdown sane instead
    // of appearing to open and instantly close.
    private static final long MIN_WINDOW_MILLIS = 30_000L;

    private volatile boolean running;
    private BukkitTask nextTask;

    // Non-null only during the open-for-entry window; used by /md join.
    private volatile String openTrackId;

    // Non-null from the moment a track opens for entry until its race (of
    // whichever of the 3 kinds) fully concludes - the master "a cycle is in
    // flight" gate. Unlike openTrackId (which only covers the entry window),
    // this stays set through the actual race too, so runCycle()/forceCycleNow()
    // never start a second race before the first one has actually finished.
    private volatile String activeCycleTrackId;

    // Non-null only between calling races.start() and the outcome listener
    // firing, so the listener can tell "my scheduled race" apart from any
    // unrelated admin-triggered race finishing at the same time.
    private volatile String awaitingOutcomeTrackId;

    private volatile long nextCycleAtMillis;

    // Shared boss bar for the current entry window, visible to everyone
    // online (not just entrants) - created in announceOpen, torn down the
    // moment the window closes in resolveCycle (or on stop()).
    private BossBar countdownBar;
    private BukkitTask bossBarTickTask;
    private volatile long entryWindowEndsAtMillis;

    // No-op everywhere unless discord.webhook-url is configured - see DiscordRaceStatus.
    private final DiscordRaceStatus discord;

    public ScheduledRaceManager(Plugin plugin, MdConfig config, TrackManager tracks, RaceManager races,
                                 RaceWatchManager watch, MarbleRaceEngine engine, DustManager dustManager,
                                 HeadPool headPool) {
        this.plugin = plugin;
        this.config = config;
        this.tracks = tracks;
        this.races = races;
        this.watch = watch;
        this.engine = engine;
        this.dustManager = dustManager;
        this.headPool = headPool;
        this.discord = new DiscordRaceStatus(plugin, config, this, races);

        races.setOutcomeListener(this::onRaceFinished);
    }

    public void start() {
        stop();
        running = true;
        nextTask = Bukkit.getScheduler().runTaskLater(plugin, this::runCycle, INITIAL_DELAY_TICKS);
    }

    public void stop() {
        running = false;
        if (nextTask != null) {
            nextTask.cancel();
            nextTask = null;
        }
        stopBossBar();
        discord.shutdown();

        // Don't leave a track stuck "open forever" with no scheduler left to
        // ever resolve it - close it out and refund anyone already joined.
        if (openTrackId != null) {
            String trackId = openTrackId;
            openTrackId = null;
            races.close(null, trackId);
            races.clear(trackId);
        }

        // Release the in-flight gate too - a race that's still actually
        // running (started via races.start()) is left alone to finish
        // naturally, but its own conclusion will find running=false and
        // simply skip scheduling a next cycle, so this is safe either way.
        activeCycleTrackId = null;
    }

    /** The track currently open for /md join, or null if no scheduled race is in its entry window right now. */
    public String openTrackId() {
        return openTrackId;
    }

    /**
     * The track a scheduled cycle currently has in flight (open OR actually
     * racing), or null if nothing's happening right now. Distinct from
     * {@link #openTrackId()}: this stays set once entries close and the race
     * itself is running, which is what lets /md race next tell "a race is
     * running, check back after" apart from "nothing's open, next in Xm"
     * instead of just reporting a stale/meaningless minutesUntilNextCycle().
     */
    public String activeCycleTrackId() {
        return activeCycleTrackId;
    }

    /**
     * nextCycleAtMillis is when the currently-open race's entry window
     * closes (the next configured daily-time), set fresh every time
     * runCycle() successfully opens one - since a race is meant to always
     * be open (see class doc), this is normally always meaningful; it only
     * goes stale during the rare gap where nothing could open at all
     * (disabled, or no eligible track free).
     */
    public int minutesUntilNextCycle() {
        long remainingMs = nextCycleAtMillis - System.currentTimeMillis();
        return (int) Math.max(0, Math.ceil(remainingMs / 60000.0));
    }

    /** Same underlying countdown as {@link #minutesUntilNextCycle()}, just second-precision for a real mm:ss readout (e.g. RaceMotdListener) instead of a ceiling-rounded minute count. */
    public long secondsUntilNextCycle() {
        long remainingMs = nextCycleAtMillis - System.currentTimeMillis();
        return Math.max(0, remainingMs / 1000);
    }

    /**
     * Called alongside RaceManager#purgeAllRunners (see /md race purge) --
     * a purge force-removes runners without ever calling their normal
     * finish callbacks, so without this, purging a stuck scheduled race
     * would leave activeCycleTrackId set forever and jam the scheduler
     * permanently. No-op if nothing was actually in flight.
     * <p>
     * If the cycle was still in its open-for-entry window (not yet an
     * actual running race) when this was called, that track's own open
     * state has to be torn down here too - otherwise races.isOpen(trackId)
     * is left permanently true with nothing left driving it forward.
     */
    public void releaseStuckCycle() {
        if (activeCycleTrackId == null) return;
        String trackId = activeCycleTrackId;
        activeCycleTrackId = null;
        awaitingOutcomeTrackId = null;

        if (trackId.equals(openTrackId)) {
            openTrackId = null;
            stopBossBar();
            discord.shutdown();
            races.close(null, trackId);
            races.clear(trackId);
        }

        scheduleReopenSoon();
    }

    /** Admin escape hatch (see /md race forcecycle) - opens a race right now instead of waiting for the next reopen. No-op if a cycle is already open/running. */
    public void forceCycleNow() {
        if (activeCycleTrackId != null) return;
        if (nextTask != null) {
            nextTask.cancel();
            nextTask = null;
        }
        runCycle();
    }

    /**
     * Called after /md reload (see CommandKit) re-reads config.yml - if an
     * admin just added a daily-time that's sooner than the one the
     * currently open entry window is already counting down to, pull that
     * window's close in to match instead of leaving it stuck counting down
     * to the now-stale time. Only ever pulls the close time IN, never
     * pushes it back out - a newly-added later time (or a removed sooner
     * one) doesn't un-shrink a window that's already correctly counting
     * down; it'll just be picked up naturally by the next cycle. No-op if
     * nothing's actually open for entries right now (idle between cycles,
     * or a race already actually running - see openTrackId's own javadoc)
     * since both of those read the config fresh on their own next
     * runCycle()/resolveCycle() anyway.
     */
    public void onConfigReloaded() {
        if (!running || openTrackId == null) return;

        long msUntilNext = millisUntilNextDailyTime();
        // No daily-times configured at all right now (e.g. the last one was
        // just removed via the GUI) - Long.MAX_VALUE, which would overflow
        // the arithmetic below into garbage. Nothing to pull in either way;
        // the already-open window just runs out its original course.
        if (msUntilNext == Long.MAX_VALUE) return;

        long newWindowMs = Math.max(MIN_WINDOW_MILLIS, msUntilNext);
        long newCloseAtMillis = System.currentTimeMillis() + newWindowMs;

        if (newCloseAtMillis >= nextCycleAtMillis - 1000L) return; // not actually sooner - nothing to do

        String trackId = openTrackId;

        if (nextTask != null) {
            nextTask.cancel();
        }
        nextCycleAtMillis = newCloseAtMillis;
        nextTask = Bukkit.getScheduler().runTaskLater(plugin, () -> resolveCycle(trackId), newWindowMs / 50L);

        // Re-arms the boss bar (and its own tick task) against the new
        // close time - its running tick task otherwise keeps computing
        // progress off the stale windowMs captured in its closure back
        // when the window first opened (see startBossBar()).
        startBossBar(trackId, newWindowMs);

        broadcastToServer(Component.text("The race on ", NamedTextColor.GOLD)
                .append(Component.text(trackId, NamedTextColor.YELLOW))
                .append(Component.text(" was moved up - now closes in " + formatDuration(newWindowMs / 1000) + "!", NamedTextColor.GOLD)));
    }

    private void scheduleReopenSoon() {
        if (!running) return;
        nextTask = Bukkit.getScheduler().runTaskLater(plugin, this::runCycle, REOPEN_DELAY_TICKS);
    }

    /** Milliseconds until the soonest configured daily-time, rolling over to tomorrow if every one of today's has already passed. Long.MAX_VALUE if none are configured. */
    private long millisUntilNextDailyTime() {
        List<LocalTime> times = config.scheduledRaceDailyTimes();
        if (times.isEmpty()) return Long.MAX_VALUE;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soonest = null;

        for (LocalTime t : times) {
            LocalDateTime candidate = LocalDateTime.of(now.toLocalDate(), t);
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusDays(1);
            }
            if (soonest == null || candidate.isBefore(soonest)) {
                soonest = candidate;
            }
        }

        return Duration.between(now, soonest).toMillis();
    }

    // ---------------- Cycle ----------------

    private void runCycle() {
        if (!running) return;
        if (activeCycleTrackId != null) return; // a cycle is already open/running - never overlap

        if (!config.scheduledRaceEnabled() || config.scheduledRaceDailyTimes().isEmpty()) {
            return; // scheduled races off entirely - nothing opens until re-enabled + /md race forcecycle or a restart
        }

        List<String> candidates = new ArrayList<>();
        for (String id : tracks.autoRaceEligibleIds()) {
            MarbleTrack track = tracks.getTrack(id);
            if (track == null || track.size() < 2) continue;
            if (races.isOpen(id) || races.isRunning(id)) continue;
            candidates.add(id);
        }
        if (candidates.isEmpty()) {
            plugin.getLogger().info("[ScheduledRaceManager] No eligible track free right now - will keep trying.");
            // Keep the idle countdown honest even though nothing actually opened.
            nextCycleAtMillis = System.currentTimeMillis() + millisUntilNextDailyTime();
            scheduleReopenSoon(); // transient (e.g. an admin mid-edit on the only eligible track) - retry rather than going silent until the next race conclusion
            return;
        }

        String trackId = candidates.get(rng.nextInt(candidates.size()));
        if (!races.open(null, trackId)) {
            plugin.getLogger().info("[ScheduledRaceManager] Couldn't open '" + trackId + "' - will keep trying.");
            scheduleReopenSoon();
            return;
        }

        openTrackId = trackId;
        activeCycleTrackId = trackId;

        long windowMs = Math.max(MIN_WINDOW_MILLIS, millisUntilNextDailyTime());
        long windowMinutes = Math.max(1, windowMs / 60_000L);

        List<Integer> announceMinutes = config.scheduledRaceAnnounceMinutesBefore();
        for (int minutesBefore : announceMinutes) {
            if (minutesBefore <= 0 || minutesBefore >= windowMinutes) continue;
            long delayTicks = (windowMinutes - minutesBefore) * 1200L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> announceReminder(trackId, minutesBefore), delayTicks);
        }

        // Set BEFORE announceOpen() - announceOpen() (via startBossBar/discord.onOpen)
        // immediately reads nextCycleAtMillis for its very first countdown display, so
        // it has to already reflect this cycle's actual close time.
        nextCycleAtMillis = System.currentTimeMillis() + windowMs;
        nextTask = Bukkit.getScheduler().runTaskLater(plugin, () -> resolveCycle(trackId), windowMs / 50L);

        announceOpen(trackId, windowMs);
    }

    /** Formats a duration as "H:MM:SS" once it's an hour or more, else "M:SS" - entry windows can now span many hours (e.g. between the last daily-time of one day and the first of the next). */
    static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }

    private void announceOpen(String trackId, long windowMs) {
        int fullDust = config.scheduledRaceWinnerDust();
        int aiDust = config.scheduledRaceWinnerDustVsAi();

        Component msg = Component.text("A race on ", NamedTextColor.GOLD)
                .append(Component.text(trackId, NamedTextColor.YELLOW))
                .append(Component.text(" opens for entries! ", NamedTextColor.GOLD))
                .append(Component.text("/md join", NamedTextColor.AQUA))
                .append(Component.text(" - starts in " + formatDuration(windowMs / 1000) + ".", NamedTextColor.GOLD));

        Component rewardMsg = Component.text("1st place gets " + fullDust + " Dust, less for runners-up", NamedTextColor.GREEN)
                .append(Component.text(" (" + aiDust + " for 1st if AI has to fill the field, none if nobody joins).", NamedTextColor.GRAY));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
            p.sendMessage(rewardMsg);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
        }

        startBossBar(trackId, windowMs);
        discord.onOpen(trackId);
    }

    private void announceReminder(String trackId, int minutesLeft) {
        if (!running || !trackId.equals(openTrackId)) return; // already resolved/cancelled somehow

        int joined = races.lobbyCount(trackId);
        Component msg = Component.text(minutesLeft + " minute" + (minutesLeft == 1 ? "" : "s") + " left to ", NamedTextColor.GOLD)
                .append(Component.text("/md join", NamedTextColor.AQUA))
                .append(Component.text(" the race on ", NamedTextColor.GOLD))
                .append(Component.text(trackId, NamedTextColor.YELLOW))
                .append(Component.text(" (" + joined + " joined so far)", NamedTextColor.GRAY));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
        }
    }

    private void resolveCycle(String trackId) {
        if (!running) return;

        openTrackId = null;
        stopBossBar();
        discord.onClose(trackId);
        races.close(null, trackId);

        List<RaceManager.RaceEntry> lobby = races.lobbySnapshot(trackId);

        if (lobby.size() >= 2) {
            awaitingOutcomeTrackId = trackId;
            races.start(null, trackId);
            // Next cycle isn't scheduled here - activeCycleTrackId stays set
            // until onRaceFinished actually fires, so the next race can't
            // start until this one truly finishes.

            // RaceManager.start() runs its own private "On your marks.../3../2../1../GO!"
            // countdown - rather than touching that just to append a reward line,
            // land a matching broadcast at the same GO moment via the same shared
            // constant it uses internally, so this can never drift out of sync with it.
            int dust = config.scheduledRaceWinnerDust();
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> broadcastToServer(Component.text(dust + " Dust on the line for 1st, less for the runners-up!", NamedTextColor.GREEN)),
                    RaceManager.COUNTDOWN_TOTAL_TICKS);
            return;
        }

        // enter() takes the marble out of the player's hand the moment they
        // join (see RaceManager#enter) - the lobby entry's own `helmet`
        // field is the only thing left holding it. If this solo entrant
        // isn't actually going to race (owner offline right now), that
        // marble has to be handed back here or it's just gone - it isn't
        // used anywhere in the showcase fallback below.
        RaceManager.RaceEntry solo = null;
        if (lobby.size() == 1) {
            RaceManager.RaceEntry candidate = lobby.get(0);
            Player owner = Bukkit.getPlayer(candidate.owner);
            if (owner != null && owner.isOnline()) {
                solo = candidate;
            } else {
                races.returnMarbleToOwner(candidate.owner, candidate.helmet);
            }
        }
        races.clear(trackId);

        MarbleTrack track = tracks.getTrack(trackId);
        if (track == null || track.size() < 2) {
            activeCycleTrackId = null; // nothing actually ran - release the gate now
            scheduleReopenSoon();
            return;
        }

        // Next cycle isn't scheduled here either - both paths release
        // activeCycleTrackId (and schedule the next cycle) at their own
        // conclusion (maybeConclude / the showcase's own finish block).
        if (solo != null) {
            runAiFillRace(trackId, track, solo);
        } else {
            runShowcaseRace(trackId, track);
        }
    }

    // ---------------- 2+ real players: normal RaceManager flow ----------------

    private void onRaceFinished(String trackId, List<RaceManager.RaceEntry> finishOrder) {
        if (!trackId.equals(awaitingOutcomeTrackId)) return; // not our scheduled race
        awaitingOutcomeTrackId = null;

        if (!finishOrder.isEmpty()) {
            // Tiered by place (see MdConfig#scheduledRaceDustForPlace) rather
            // than winner-take-all - players reported 1st place snowballing
            // further ahead every race off the full reward alone, so 2nd/3rd
            // etc. now earn a tapered share too instead of nothing.
            int baseDust = config.scheduledRaceWinnerDust();
            List<PlacePayout> payouts = new ArrayList<>();

            for (int i = 0; i < finishOrder.size(); i++) {
                RaceManager.RaceEntry entry = finishOrder.get(i);
                int dust = config.scheduledRaceDustForPlace(baseDust, i);

                if (dust > 0) {
                    Player p = Bukkit.getPlayer(entry.owner);
                    if (p != null && p.isOnline()) {
                        dustManager.addDust(p, dust);
                    }
                }

                String name = Bukkit.getOfflinePlayer(entry.owner).getName();
                if (name == null) name = entry.owner.toString();
                payouts.add(new PlacePayout(name, dust));
            }

            announceServerPodium(trackId, payouts);
        }

        // This race has genuinely finished - release the gate and open the next one.
        activeCycleTrackId = null;
        scheduleReopenSoon();
    }

    // ---------------- 1 real player: AI fills the rest ----------------

    private void runAiFillRace(String trackId, MarbleTrack track, RaceManager.RaceEntry solo) {
        Player player = Bukkit.getPlayer(solo.owner);
        if (player == null || !player.isOnline()) {
            // Went offline in the gap between resolveCycle() reading the
            // lobby and this actually running (playCountdown's few seconds
            // of delay) - their marble was already taken at enter() time,
            // and the showcase fallback below doesn't use it, so it has to
            // come back here.
            races.returnMarbleToOwner(solo.owner, solo.helmet);
            runShowcaseRace(trackId, track);
            return;
        }

        int aiCount = Math.max(0, config.scheduledRaceAiFillCount());
        int total = 1 + aiCount;
        int dust = config.scheduledRaceWinnerDustVsAi();

        watch.start(player, trackId);

        List<Location> grid = races.startingGrid(track, total);
        FinishTracker tracker = new FinishTracker(total);
        List<MarbleRunner> runners = new ArrayList<>(total);

        // Apply the player's loadout (RaceManager#start does the same
        // for its own 2+-player path) - buildStatsRunner's MarbleData
        // overload uses raw, un-loadout-adjusted stats, which is correct
        // for the AI racers below but was silently skipping the human's
        // own loadout choice entirely.
        MarbleStats effectiveStats = solo.loadout.applyTo(solo.data.getStats());
        MarbleRunner playerRunner = races.buildStatsRunner(track, grid.get(0), solo.helmet, effectiveStats, () -> {
            FinishTracker.Finish f = tracker.recordFinish(player.getName(), true, solo.data, solo.marbleDisplayName);
            broadcastPlaceFinish(tracker.finishes.size(), f);
            // Same "give it back the instant their own runner finishes,
            // not once the whole field does" timing as RaceManager#onFinish.
            races.returnMarbleToOwner(solo.owner, solo.helmet);
            maybeConclude(trackId, tracker, player);
        });
        // Held at the grid until playCountdown()'s GO moment - same as
        // RaceManager#start - so entrants actually see the field lined up
        // instead of marbles popping into existence already moving.
        playerRunner.hold();
        engine.addRunner(playerRunner);
        runners.add(playerRunner);

        HeadEntry[] aiHeads = pickDistinctHeads(aiCount);
        for (int i = 0; i < aiCount; i++) {
            MarbleData aiData = randomAiData();
            ItemStack helmet = buildAiHelmet(i + 1, aiHeads[i]);
            int racerNumber = i + 1;

            MarbleRunner aiRunner = races.buildStatsRunner(track, grid.get(i + 1), helmet, aiData, () -> {
                FinishTracker.Finish f = tracker.recordFinish("AI Racer " + racerNumber, false, aiData, null);
                broadcastPlaceFinish(tracker.finishes.size(), f);
                maybeConclude(trackId, tracker, player);
            });
            aiRunner.hold();
            engine.addRunner(aiRunner);
            runners.add(aiRunner);
        }

        playCountdown(dust + " Dust for 1st (AI in the field, less for lower places)!", track.getLaps(), runners);
    }

    private void maybeConclude(String trackId, FinishTracker tracker, Player humanPlayer) {
        if (!tracker.isComplete() || tracker.concluded) return;
        concludeAiFillRace(trackId, tracker, humanPlayer);
    }

    private void concludeAiFillRace(String trackId, FinishTracker tracker, Player humanPlayer) {
        if (tracker.concluded) return;
        tracker.concluded = true;

        // Same tiered-by-place payout as a real race (see onRaceFinished), just
        // against the reduced AI-involved base amount, and using wherever the
        // human actually placed among the AI rather than only paying out if
        // they placed 1st outright. Built as a full-field podium (not a
        // single winner line) since the overall winner might be an AI while
        // the human still earns something for 2nd/3rd - a single "X wins!
        // (+N Dust)" line would misattribute the reward to whoever placed 1st.
        List<PlacePayout> payouts = new ArrayList<>();
        int humanDust = 0;
        for (int i = 0; i < tracker.finishes.size(); i++) {
            FinishTracker.Finish f = tracker.finishes.get(i);
            int placeDust = f.isHuman() ? config.scheduledRaceDustForPlace(config.scheduledRaceWinnerDustVsAi(), i) : 0;
            if (f.isHuman()) humanDust = placeDust;
            payouts.add(new PlacePayout(f.name(), placeDust));
        }
        if (humanDust > 0) {
            dustManager.addDust(humanPlayer, humanDust);
        }

        announceServerPodium(trackId, payouts);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (humanPlayer.isOnline() && watch.isWatching(humanPlayer)) {
                watch.stop(humanPlayer, true);
                // Now safe - see RaceManager#returnMarbleToOwner's javadoc on
                // why the marble (queued when their own runner finished,
                // above) has to wait until after watch.stop()'s own
                // inventory restore before it's actually handed back.
                races.flushPendingReturns(humanPlayer);
            }
        }, 100L);

        // This race has genuinely finished - release the gate and open the next one.
        activeCycleTrackId = null;
        scheduleReopenSoon();
    }

    // ---------------- 0 real players: pure AI showcase ----------------

    private void runShowcaseRace(String trackId, MarbleTrack track) {
        int aiCount = Math.max(2, config.scheduledRaceAiShowCount());

        List<Location> grid = races.startingGrid(track, aiCount);
        FinishTracker tracker = new FinishTracker(aiCount);
        HeadEntry[] aiHeads = pickDistinctHeads(aiCount);
        List<MarbleRunner> runners = new ArrayList<>(aiCount);

        for (int i = 0; i < aiCount; i++) {
            MarbleData aiData = randomAiData();
            ItemStack helmet = buildAiHelmet(i + 1, aiHeads[i]);
            int racerNumber = i + 1;

            MarbleRunner aiRunner = races.buildStatsRunner(track, grid.get(i), helmet, aiData, () -> {
                FinishTracker.Finish f = tracker.recordFinish("AI Racer " + racerNumber, false, aiData, null);
                broadcastPlaceFinish(tracker.finishes.size(), f);
                if (tracker.isComplete()) {
                    concludeShowcaseRace(trackId, tracker);
                }
            });
            // Held at the grid until playCountdown()'s GO moment - see the
            // matching comment in runAiFillRace().
            aiRunner.hold();
            engine.addRunner(aiRunner);
            runners.add(aiRunner);
        }

        playCountdown("Just for the show - no Dust this time (nobody joined).", track.getLaps(), runners);
    }

    private void concludeShowcaseRace(String trackId, FinishTracker tracker) {
        if (tracker.concluded) return;
        tracker.concluded = true;

        if (!tracker.finishes.isEmpty()) {
            announceServerResult(trackId, tracker.finishes.get(0).name(), 0);
        }

        // This race has genuinely finished - release the gate and open the next one.
        activeCycleTrackId = null;
        scheduleReopenSoon();
    }

    // ---------------- Shared helpers ----------------

    private static final int COUNTDOWN_SECONDS = 3;

    /**
     * "Get ready..." -> 3... 2... 1... -> GO! (+ the stakes for this
     * specific race), matching RaceManager's own cadence, but server-wide.
     * heldRunners must already be spawned (via races.buildStatsRunner) and
     * hold()-ed before this is called, so entrants see the field lined up
     * on the grid throughout the countdown instead of marbles popping into
     * existence already moving right as "GO!" appears - same as a real
     * RaceManager#start race. They're release()-d together at the GO beat.
     */
    private void playCountdown(String rewardLine, int laps, List<MarbleRunner> heldRunners) {
        broadcastToServer(Component.text("Get ready...", NamedTextColor.YELLOW));
        if (laps > 1) {
            broadcastToServer(Component.text("This race is " + laps + " laps.", NamedTextColor.GRAY));
        }
        playSoundToServer(Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);

        for (int i = COUNTDOWN_SECONDS; i >= 1; i--) {
            int secondsLeft = i;
            long delayTicks = (long) (COUNTDOWN_SECONDS - i + 1) * 20L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                broadcastToServer(Component.text(secondsLeft + "...", NamedTextColor.YELLOW, TextDecoration.BOLD));
                playSoundToServer(Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }, delayTicks);
        }

        long goDelay = (long) (COUNTDOWN_SECONDS + 1) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            broadcastToServer(Component.text("GO! ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text(rewardLine, NamedTextColor.GRAY)));
            playSoundToServer(Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            for (MarbleRunner r : heldRunners) r.release();
        }, goDelay);
    }

    private void broadcastToServer(Component msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
    }

    private void playSoundToServer(Sound sound, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), sound, volume, pitch);
    }

    private void broadcastPlaceFinish(int place, FinishTracker.Finish f) {
        Component line = Component.text("#" + place + " finished: ", NamedTextColor.GRAY)
                .append(Component.text(f.name(), NamedTextColor.YELLOW))
                .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                .append(races.buildMarbleNameComponent(f.marbleDisplayName(), f.data()))
                .append(Component.text(")", NamedTextColor.DARK_GRAY))
                .append(Component.text("  -  " + RaceManager.formatTime(f.elapsedMs()), NamedTextColor.GREEN));

        broadcastToServer(line);
    }

    private void announceServerResult(String trackId, String winnerName, int dust) {
        Component msg = Component.text("=== Race Results (" + trackId + ") ===", NamedTextColor.GOLD);
        Component winLine = Component.text(winnerName, NamedTextColor.YELLOW)
                .append(Component.text(" wins!", NamedTextColor.GREEN))
                .append(dust > 0
                        ? Component.text(" (+" + dust + " Dust)", NamedTextColor.GREEN)
                        : Component.empty());

        broadcastToServer(msg);
        broadcastToServer(winLine);
    }

    private record PlacePayout(String name, int dust) {}

    /** Full-field results with a tiered Dust payout per place (see MdConfig#scheduledRaceDustForPlace), medal-colored like RaceManager's own results. */
    private void announceServerPodium(String trackId, List<PlacePayout> payouts) {
        broadcastToServer(Component.text("=== Race Results (" + trackId + ") ===", NamedTextColor.GOLD));

        for (int i = 0; i < payouts.size(); i++) {
            PlacePayout p = payouts.get(i);
            NamedTextColor medal = switch (i) {
                case 0 -> NamedTextColor.GOLD;
                case 1 -> NamedTextColor.GRAY;
                case 2 -> NamedTextColor.DARK_RED;
                default -> NamedTextColor.WHITE;
            };

            Component line = Component.text((i + 1) + ". ", medal)
                    .append(Component.text(p.name(), NamedTextColor.YELLOW))
                    .append(p.dust() > 0
                            ? Component.text(" (+" + p.dust() + " Dust)", NamedTextColor.GREEN)
                            : Component.empty());

            broadcastToServer(line);
        }
    }

    // ---------------- Boss bar (entry-window countdown, everyone online) ----------------

    private void startBossBar(String trackId, long windowMs) {
        stopBossBar();

        entryWindowEndsAtMillis = System.currentTimeMillis() + windowMs;

        countdownBar = Bukkit.createBossBar(" ", BarColor.YELLOW, BarStyle.SOLID);
        for (Player p : Bukkit.getOnlinePlayers()) countdownBar.addPlayer(p);
        countdownBar.setVisible(true);
        updateBossBarTitle(trackId, windowMs);

        bossBarTickTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> updateBossBarTitle(trackId, windowMs), 20L, 20L);
    }

    private void updateBossBarTitle(String trackId, long windowMs) {
        if (countdownBar == null) return;

        long remainingMs = Math.max(0, entryWindowEndsAtMillis - System.currentTimeMillis());

        int joined = races.lobbyCount(trackId);
        String plural = joined == 1 ? "" : "s";

        countdownBar.setTitle(ChatColor.GOLD + "Race on " + ChatColor.YELLOW + trackId +
                ChatColor.GOLD + " - " + formatDuration(remainingMs / 1000) +
                ChatColor.GOLD + " - " + ChatColor.AQUA + joined + " player" + plural + " joined" +
                ChatColor.GOLD + " - " + ChatColor.AQUA + "/md join" + ChatColor.GOLD + "!");

        double progress = windowMs <= 0 ? 0 : Math.max(0.0, Math.min(1.0, remainingMs / (double) windowMs));
        countdownBar.setProgress(progress);
    }

    private void stopBossBar() {
        if (bossBarTickTask != null) {
            bossBarTickTask.cancel();
            bossBarTickTask = null;
        }
        if (countdownBar != null) {
            countdownBar.removeAll();
            countdownBar = null;
        }
    }

    /** So a player who logs in mid-countdown still sees the boss bar. */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (countdownBar != null) {
            countdownBar.addPlayer(e.getPlayer());
        }
    }

    // ---------------- AI helpers ----------------

    private MarbleData randomAiData() {
        int mid = 45 + rng.nextInt(20); // 45-64 per stat, same mid-tier band as the tutorial's AI
        MarbleStats stats = new MarbleStats(mid, mid, mid, mid, mid);
        return new MarbleData(UUID.randomUUID(), "scheduled_race_ai", "ai", MarbleRarity.COMMON,
                stats, null, System.currentTimeMillis(), 0, 0);
    }

    private HeadEntry[] pickDistinctHeads(int count) {
        HeadEntry[] out = new HeadEntry[count];
        if (headPool == null) return out;

        List<HeadEntry> all = headPool.all();
        for (int i = 0; i < count; i++) {
            HeadEntry candidate = headPool.random();
            if (candidate != null && all.size() > 1) {
                int attempts = 0;
                boolean clash;
                do {
                    clash = false;
                    for (int j = 0; j < i; j++) {
                        if (out[j] != null && out[j].base64().equals(candidate.base64())) {
                            clash = true;
                            break;
                        }
                    }
                    if (clash) candidate = headPool.random();
                } while (clash && ++attempts < 10);
            }
            out[i] = candidate;
        }
        return out;
    }

    private ItemStack buildAiHelmet(int racerNumber, HeadEntry head) {
        ItemStack helmet = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = helmet.getItemMeta();
        if (meta == null) return helmet;

        if (meta instanceof SkullMeta skullMeta && head != null) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
            profile.setProperty(new ProfileProperty("textures", head.base64()));
            skullMeta.setPlayerProfile(profile);
            meta = skullMeta;
        }

        meta.setDisplayName(ChatColor.GRAY + "AI Racer " + racerNumber);
        helmet.setItemMeta(meta);
        return helmet;
    }

    private static final class FinishTracker {
        private record Finish(String name, boolean isHuman, MarbleData data, String marbleDisplayName, long elapsedMs) {}

        private final long startMs = System.currentTimeMillis();
        private final List<Finish> finishes = new ArrayList<>();
        private final int total;
        private volatile boolean concluded = false;

        FinishTracker(int total) {
            this.total = total;
        }

        synchronized Finish recordFinish(String name, boolean isHuman, MarbleData data, String marbleDisplayName) {
            Finish f = new Finish(name, isHuman, data, marbleDisplayName, System.currentTimeMillis() - startMs);
            finishes.add(f);
            return f;
        }

        boolean isComplete() {
            return finishes.size() >= total;
        }
    }
}
