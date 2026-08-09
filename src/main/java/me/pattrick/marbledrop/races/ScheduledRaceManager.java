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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Runs a fully-automatic, perpetually recurring server race: as soon as one
 * cycle resolves, the next one is scheduled (a few seconds later, or after a
 * short retry delay if it had to be skipped) -- there is always either a race
 * open, running, or about to open, forever, until {@link #stop()}. A random
 * eligible track (see MarbleTrack#isAutoRaceEligible, set via
 * {@code /md track autorace}) opens for entries, a boss bar + chat reminders
 * (at each of {@link MdConfig#scheduledRaceAnnounceMinutesBefore()}) count
 * down the entry window, and at zero the race runs.
 * <p>
 * This deliberately does NOT use a fixed {@code runTaskTimer} period: since
 * the entry window and the timer's own period were both exactly
 * {@code intervalMinutes}, the next timer firing and the current cycle's
 * resolution landed on the same tick, and depending on execution order the
 * new firing could see the old {@code openTrackId} still set and silently
 * skip a whole cycle. Chaining a single {@code runTaskLater} at a time (see
 * {@link #scheduleNextCycleSoon()}/{@link #scheduleRetry()}) means there is
 * never more than one pending scheduled step, so that race is structurally
 * impossible.
 * <p>
 * Three outcomes depending on how many real players joined via {@code /md join}:
 * <ul>
 *   <li><b>2+</b>: handed straight to {@link RaceManager#start} exactly like an
 *   admin-triggered race -- {@link RaceManager.OutcomeListener} picks up the
 *   finish here to award Dust and announce server-wide.</li>
 *   <li><b>1</b>: RaceManager's lobby is bypassed (it has no concept of AI
 *   entries) in favor of building the race directly via
 *   {@link RaceManager#startingGrid}/{@link RaceManager#buildStatsRunner} --
 *   the same low-level pattern
 *   TutorialRaceService uses for its solo-vs-AI practice race -- with AI
 *   filling the rest of the field, behind the same 3-2-1-GO countdown and
 *   live per-place finish tower a normal race gets. Reduced Dust, and only
 *   if the human actually won.</li>
 *   <li><b>0</b>: same direct-build path, but AI-only -- a pure showcase with
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

    // ~5s after enable, so the very first cycle doesn't wait a full interval either.
    private static final long INITIAL_DELAY_TICKS = 100L;
    // Buffer between one cycle resolving and the next one being attempted --
    // just long enough for that cycle's own messages to land before new ones start.
    private static final long NEXT_CYCLE_DELAY_TICKS = 200L;

    private volatile boolean running;
    private BukkitTask nextTask;

    // Non-null only during the open-for-entry window; used by /md join.
    private volatile String openTrackId;

    // Non-null from the moment a track opens for entry until its race (of
    // whichever of the 3 kinds) fully concludes -- the master "a cycle is in
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
    // online (not just entrants) -- created in announceOpen, torn down the
    // moment the window closes in resolveCycle (or on stop()).
    private BossBar countdownBar;
    private BukkitTask bossBarTickTask;
    private volatile long entryWindowEndsAtMillis;

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

        races.setOutcomeListener(this::onRaceFinished);
    }

    public void start() {
        stop();
        running = true;
        nextCycleAtMillis = System.currentTimeMillis() + INITIAL_DELAY_TICKS * 50L;
        nextTask = Bukkit.getScheduler().runTaskLater(plugin, this::runCycle, INITIAL_DELAY_TICKS);
    }

    public void stop() {
        running = false;
        if (nextTask != null) {
            nextTask.cancel();
            nextTask = null;
        }
        stopBossBar();

        // Don't leave a track stuck "open forever" with no scheduler left to
        // ever resolve it -- close it out and refund anyone already joined.
        if (openTrackId != null) {
            String trackId = openTrackId;
            openTrackId = null;
            races.close(null, trackId);
            races.clear(trackId);
        }

        // Release the in-flight gate too -- a race that's still actually
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

    public int minutesUntilNextCycle() {
        long remainingMs = nextCycleAtMillis - System.currentTimeMillis();
        return (int) Math.max(0, Math.ceil(remainingMs / 60000.0));
    }

    /**
     * Called alongside RaceManager#purgeAllRunners (see /md race purge) --
     * a purge force-removes runners without ever calling their normal
     * finish callbacks, so without this, purging a stuck scheduled race
     * would leave activeCycleTrackId set forever and jam the scheduler
     * permanently. No-op if nothing was actually in flight.
     */
    public void releaseStuckCycle() {
        if (activeCycleTrackId == null) return;
        activeCycleTrackId = null;
        awaitingOutcomeTrackId = null;
        scheduleNextCycleSoon();
    }

    /** Admin escape hatch (see /md race forcecycle) -- runs a cycle right now instead of waiting. No-op if a cycle is already open/running. */
    public void forceCycleNow() {
        if (activeCycleTrackId != null) return;
        if (nextTask != null) {
            nextTask.cancel();
            nextTask = null;
        }
        runCycle();
    }

    // ---------------- Cycle ----------------

    private void runCycle() {
        if (!running) return;

        if (!config.scheduledRaceEnabled() || Bukkit.getOnlinePlayers().isEmpty()) {
            scheduleRetry();
            return;
        }
        if (activeCycleTrackId != null) return; // previous cycle's race hasn't fully finished yet -- never overlap

        List<String> candidates = new ArrayList<>();
        for (String id : tracks.autoRaceEligibleIds()) {
            MarbleTrack track = tracks.getTrack(id);
            if (track == null || track.size() < 2) continue;
            if (races.isOpen(id) || races.isRunning(id)) continue;
            candidates.add(id);
        }
        if (candidates.isEmpty()) {
            scheduleRetry();
            return;
        }

        String trackId = candidates.get(rng.nextInt(candidates.size()));
        if (!races.open(null, trackId)) {
            scheduleRetry();
            return;
        }

        openTrackId = trackId;
        activeCycleTrackId = trackId;

        List<Integer> announceMinutes = config.scheduledRaceAnnounceMinutesBefore();
        int intervalMinutes = Math.max(1, config.scheduledRaceIntervalMinutes());

        for (int minutesBefore : announceMinutes) {
            if (minutesBefore <= 0 || minutesBefore >= intervalMinutes) continue;
            long delayTicks = (long) (intervalMinutes - minutesBefore) * 1200L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> announceReminder(trackId, minutesBefore), delayTicks);
        }

        announceOpen(trackId, intervalMinutes);

        nextCycleAtMillis = System.currentTimeMillis() + (long) intervalMinutes * 60_000L;
        nextTask = Bukkit.getScheduler().runTaskLater(plugin, () -> resolveCycle(trackId), (long) intervalMinutes * 1200L);
    }

    /** Skipped this attempt (disabled / no players / nothing free) -- retry soon rather than waiting a full interval. */
    private void scheduleRetry() {
        if (!running) return;
        long delayTicks = Math.max(1, config.scheduledRaceRetryDelaySeconds()) * 20L;
        nextCycleAtMillis = System.currentTimeMillis() + delayTicks * 50L;
        nextTask = Bukkit.getScheduler().runTaskLater(plugin, this::runCycle, delayTicks);
    }

    /** A cycle just finished resolving (whichever branch) -- this is what makes cycling perpetual. */
    private void scheduleNextCycleSoon() {
        if (!running) return;
        nextCycleAtMillis = System.currentTimeMillis() + NEXT_CYCLE_DELAY_TICKS * 50L;
        nextTask = Bukkit.getScheduler().runTaskLater(plugin, this::runCycle, NEXT_CYCLE_DELAY_TICKS);
    }

    private void announceOpen(String trackId, int intervalMinutes) {
        int fullDust = config.scheduledRaceWinnerDust();
        int aiDust = config.scheduledRaceWinnerDustVsAi();

        Component msg = Component.text("A race on ", NamedTextColor.GOLD)
                .append(Component.text(trackId, NamedTextColor.YELLOW))
                .append(Component.text(" opens for entries! ", NamedTextColor.GOLD))
                .append(Component.text("/md join", NamedTextColor.AQUA))
                .append(Component.text(" within " + intervalMinutes + " minutes.", NamedTextColor.GOLD));

        Component rewardMsg = Component.text("1st place gets " + fullDust + " Dust, less for runners-up", NamedTextColor.GREEN)
                .append(Component.text(" (" + aiDust + " for 1st if AI has to fill the field, none if nobody joins).", NamedTextColor.GRAY));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
            p.sendMessage(rewardMsg);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
        }

        startBossBar(trackId, intervalMinutes);
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
        races.close(null, trackId);

        List<RaceManager.RaceEntry> lobby = races.lobbySnapshot(trackId);

        if (lobby.size() >= 2) {
            awaitingOutcomeTrackId = trackId;
            races.start(null, trackId);
            // Next cycle isn't scheduled here -- activeCycleTrackId stays set
            // until onRaceFinished actually fires, so the next race can't
            // start until this one truly finishes.

            // RaceManager.start() runs its own private "On your marks.../3../2../1../GO!"
            // countdown -- rather than touching that just to append a reward line,
            // land a matching broadcast at the same GO moment via the same shared
            // constant it uses internally, so this can never drift out of sync with it.
            int dust = config.scheduledRaceWinnerDust();
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> broadcastToServer(Component.text(dust + " Dust on the line for 1st, less for the runners-up!", NamedTextColor.GREEN)),
                    RaceManager.COUNTDOWN_TOTAL_TICKS);
            return;
        }

        // enter() never removes the marble from the player's inventory in
        // this physics model (see RaceManager#enter) -- it only clones a
        // helmet copy into the RaceEntry -- so there's nothing to "take" or
        // later refund here, just read the entry straight off the lobby
        // snapshot before clearing it.
        RaceManager.RaceEntry solo = null;
        if (lobby.size() == 1) {
            Player owner = Bukkit.getPlayer(lobby.get(0).owner);
            if (owner != null && owner.isOnline()) {
                solo = lobby.get(0);
            }
        }
        races.clear(trackId);

        MarbleTrack track = tracks.getTrack(trackId);
        if (track == null || track.size() < 2) {
            activeCycleTrackId = null; // nothing actually ran -- release the gate now
            scheduleNextCycleSoon();
            return;
        }

        // Next cycle isn't scheduled here either -- both paths release
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
            // than winner-take-all -- players reported 1st place snowballing
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

        // This race has genuinely finished -- release the gate and let the next cycle begin.
        activeCycleTrackId = null;
        scheduleNextCycleSoon();
    }

    // ---------------- 1 real player: AI fills the rest ----------------

    private void runAiFillRace(String trackId, MarbleTrack track, RaceManager.RaceEntry solo) {
        Player player = Bukkit.getPlayer(solo.owner);
        if (player == null || !player.isOnline()) {
            runShowcaseRace(trackId, track);
            return;
        }

        int aiCount = Math.max(0, config.scheduledRaceAiFillCount());
        int total = 1 + aiCount;
        int dust = config.scheduledRaceWinnerDustVsAi();

        watch.start(player, trackId);

        playCountdown(dust + " Dust for 1st (AI in the field, less for lower places)!", track.getLaps(), () -> {
            List<Location> grid = races.startingGrid(track, total);

            FinishTracker tracker = new FinishTracker(total);

            MarbleRunner playerRunner = races.buildStatsRunner(track, grid.get(0), solo.helmet, solo.data, () -> {
                FinishTracker.Finish f = tracker.recordFinish(player.getName(), true, solo.data, solo.marbleDisplayName);
                broadcastPlaceFinish(tracker.finishes.size(), f);
                maybeConclude(trackId, tracker, player);
            });
            engine.addRunner(playerRunner);

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
                engine.addRunner(aiRunner);
            }
        });
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
        // the human still earns something for 2nd/3rd -- a single "X wins!
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
            }
        }, 100L);

        // This race has genuinely finished -- release the gate and let the next cycle begin.
        activeCycleTrackId = null;
        scheduleNextCycleSoon();
    }

    // ---------------- 0 real players: pure AI showcase ----------------

    private void runShowcaseRace(String trackId, MarbleTrack track) {
        int aiCount = Math.max(2, config.scheduledRaceAiShowCount());

        playCountdown("Just for the show -- no Dust this time (nobody joined).", track.getLaps(), () -> {
            List<Location> grid = races.startingGrid(track, aiCount);

            FinishTracker tracker = new FinishTracker(aiCount);
            HeadEntry[] aiHeads = pickDistinctHeads(aiCount);

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
                engine.addRunner(aiRunner);
            }
        });
    }

    private void concludeShowcaseRace(String trackId, FinishTracker tracker) {
        if (tracker.concluded) return;
        tracker.concluded = true;

        if (!tracker.finishes.isEmpty()) {
            announceServerResult(trackId, tracker.finishes.get(0).name(), 0);
        }

        // This race has genuinely finished -- release the gate and let the next cycle begin.
        activeCycleTrackId = null;
        scheduleNextCycleSoon();
    }

    // ---------------- Shared helpers ----------------

    private static final int COUNTDOWN_SECONDS = 3;

    /** "Get ready..." -> 3... 2... 1... -> GO! (+ the stakes for this specific race), matching RaceManager's own cadence, but server-wide. */
    private void playCountdown(String rewardLine, int laps, Runnable onGo) {
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
            onGo.run();
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
                .append(Component.text(" — " + RaceManager.formatTime(f.elapsedMs()), NamedTextColor.GREEN));

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

    private void startBossBar(String trackId, int intervalMinutes) {
        stopBossBar();

        entryWindowEndsAtMillis = System.currentTimeMillis() + (long) intervalMinutes * 60_000L;

        countdownBar = Bukkit.createBossBar(" ", BarColor.YELLOW, BarStyle.SOLID);
        for (Player p : Bukkit.getOnlinePlayers()) countdownBar.addPlayer(p);
        countdownBar.setVisible(true);
        updateBossBarTitle(trackId, intervalMinutes);

        bossBarTickTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> updateBossBarTitle(trackId, intervalMinutes), 20L, 20L);
    }

    private void updateBossBarTitle(String trackId, int intervalMinutes) {
        if (countdownBar == null) return;

        long remainingMs = Math.max(0, entryWindowEndsAtMillis - System.currentTimeMillis());
        long totalSeconds = remainingMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        countdownBar.setTitle(ChatColor.GOLD + "Race on " + ChatColor.YELLOW + trackId +
                ChatColor.GOLD + " -- " + String.format("%d:%02d", minutes, seconds) +
                ChatColor.GOLD + " -- " + ChatColor.AQUA + "/md join" + ChatColor.GOLD + "!");

        double totalMs = intervalMinutes * 60_000.0;
        double progress = totalMs <= 0 ? 0 : Math.max(0.0, Math.min(1.0, remainingMs / totalMs));
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
