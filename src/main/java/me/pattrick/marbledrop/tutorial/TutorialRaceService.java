package me.pattrick.marbledrop.tutorial;

import me.pattrick.marbledrop.marble.MarbleData;
import me.pattrick.marbledrop.marble.MarbleItem;
import me.pattrick.marbledrop.marble.MarbleRarity;
import me.pattrick.marbledrop.marble.MarbleStats;
import me.pattrick.marbledrop.progression.infusion.heads.HeadEntry;
import me.pattrick.marbledrop.progression.infusion.heads.HeadPool;
import me.pattrick.marbledrop.races.MarbleRaceEngine;
import me.pattrick.marbledrop.races.MarbleRunner;
import me.pattrick.marbledrop.races.MarbleTrack;
import me.pattrick.marbledrop.races.RaceManager;
import me.pattrick.marbledrop.races.RaceWatchManager;
import me.pattrick.marbledrop.races.TrackManager;
import me.pattrick.marbledrop.races.TrackPhysics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Solo-vs-AI practice race for the RACE tutorial step. Deliberately
 * bypasses RaceManager's shared per-track lobby/open/active bookkeeping
 * entirely (that system allows only one running race per track ID at a
 * time) and instead constructs MarbleRunner instances directly via
 * RaceManager.createPhysics/buildStatsRunner. This means multiple
 * players can each run their own tutorial race on the same configured
 * track simultaneously without colliding on track-level locks.
 * <p>
 * Each call to launchRunners() builds its own TrackPhysics (one dyn4j
 * World per race), so a player's practice race collides only with its
 * own AI opponents -- it's fully isolated from real races and from any
 * other player running the tutorial on the same track at the same time.
 * Concurrent tutorial racers on the same configured track never interact
 * physics-wise; their marbles can visually overlap at the shared spawn
 * point, but that's cosmetic only, not a correctness issue.
 */
public final class TutorialRaceService {

    private final Plugin plugin;
    private final TutorialManager tutorialManager;
    private final TutorialLocationStore locationStore;
    private final RaceManager raceManager;
    private final TrackManager trackManager;
    private final MarbleRaceEngine engine;
    private final RaceWatchManager watchManager;
    private final HeadPool headPool;

    private final Set<UUID> racing = ConcurrentHashMap.newKeySet();
    private final Random rng = new Random();

    public TutorialRaceService(Plugin plugin, TutorialManager tutorialManager, TutorialLocationStore locationStore,
                                RaceManager raceManager, TrackManager trackManager, MarbleRaceEngine engine,
                                RaceWatchManager watchManager, HeadPool headPool) {
        this.plugin = plugin;
        this.tutorialManager = tutorialManager;
        this.locationStore = locationStore;
        this.raceManager = raceManager;
        this.trackManager = trackManager;
        this.engine = engine;
        this.watchManager = watchManager;
        this.headPool = headPool;
    }

    public void startRace(Player player) {
        if (racing.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "You're already racing, hang tight!");
            return;
        }

        String trackId = locationStore.getRaceTrackId();
        if (trackId == null) {
            player.sendMessage(ChatColor.RED + "No tutorial race track is configured yet.");
            player.sendMessage(ChatColor.GRAY + "An admin needs to run /md tutorial setrace <trackId>.");
            return;
        }

        MarbleTrack track = trackManager.getTrack(trackId);
        if (track == null) {
            player.sendMessage(ChatColor.RED + "The configured tutorial track (" + trackId + ") no longer exists.");
            player.sendMessage(ChatColor.GRAY + "An admin needs to run /md tutorial setrace <trackId> with a valid track.");
            return;
        }
        if (track.size() < 2) {
            player.sendMessage(ChatColor.RED + "The configured tutorial track (" + trackId + ") only has " + track.size() + " point(s).");
            player.sendMessage(ChatColor.GRAY + "An admin needs to add at least 2 points to it with /md track addpoint " + trackId + ".");
            return;
        }

        ItemStack marble = TutorialMarbleUtil.findFirstMarble(player);
        if (marble == null) {
            player.sendMessage(ChatColor.RED + "You need your marble to race! Make sure you have it.");
            return;
        }

        MarbleData data = MarbleItem.read(marble);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "Couldn't read your marble's data. Try re-infusing.");
            return;
        }

        racing.add(player.getUniqueId());

        // Reuse the real race-watch flow (same experience as a genuine race)
        // rather than just leaving the player standing wherever they were.
        // Teleport them in first so the countdown plays out at the track.
        watchManager.start(player, trackId);

        player.sendMessage(ChatColor.YELLOW + "Get ready...");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.sendMessage(ChatColor.YELLOW + "3...");
        }, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.sendMessage(ChatColor.YELLOW + "2...");
        }, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.sendMessage(ChatColor.YELLOW + "1...");
        }, 60L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                racing.remove(player.getUniqueId());
                return;
            }
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "GO!");
            launchRunners(player, track, marble, data);
        }, 80L);
    }

    private void launchRunners(Player player, MarbleTrack track, ItemStack marble, MarbleData data) {
        AtomicBoolean playerFinished = new AtomicBoolean(false);
        RaceResultTracker tracker = new RaceResultTracker(3);

        TrackPhysics physics = raceManager.createPhysics(track);
        List<Location> grid = raceManager.startingGrid(track, 3);

        Location playerSpawn = grid.get(0);
        MarbleRunner playerRunner = raceManager.buildStatsRunner(physics, playerSpawn, marble, data, () -> {
            if (!playerFinished.compareAndSet(false, true)) return; // FinishListener can only ever fire once per runner in practice, but stay safe
            player.sendMessage(ChatColor.GREEN + "You finished the race!");
            tracker.recordFinish(player.getName(), data);
            racing.remove(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                if (watchManager.isWatching(player)) {
                    // teleportBack=true here is deliberate: stop() restores
                    // flight/gamemode AND the landing teleport in one
                    // synchronous call, so there's never a tick where
                    // they're flight-disabled in survival mode without
                    // also already being back on solid ground (skipping
                    // the teleport risked leaving them mid-air over the
                    // track with no flight -- fall damage/death).
                    watchManager.stop(player, true);
                    // Then redirect to the RACE checkpoint (a spot the
                    // tutorial actually controls) so they don't end up
                    // wherever they happened to be standing when /md race
                    // was typed -- that's how players were ending up back
                    // in the recycler room. Same tick as the line above,
                    // so this is still a single safe teleport overall.
                    Location raceCheckpoint = locationStore.get(TutorialStep.RACE);
                    if (raceCheckpoint != null) player.teleport(raceCheckpoint);
                }
                maybeSendResults(player, tracker);
                tutorialManager.completeStep(player, TutorialStep.RACE);
            }, 40L);
        }, player);
        engine.addRunner(playerRunner);

        // Two AI opponents, mid-tier stats, each given a distinct random
        // texture pulled from the same heads.yml pool real marbles use
        // (rather than cloning the player's own marble's custom skin) so
        // the player can immediately pick out which marble is theirs, and
        // so the two AI marbles look distinct from each other too.
        HeadEntry[] aiHeads = pickTwoDistinctHeads();

        for (int i = 0; i < 2; i++) {
            int mid = 45 + rng.nextInt(20); // 45-64 per stat
            MarbleStats aiStats = new MarbleStats(mid, mid, mid, mid, mid);
            MarbleData aiData = new MarbleData(
                    UUID.randomUUID(), "tutorial_ai", "ai", MarbleRarity.COMMON,
                    aiStats, null, System.currentTimeMillis(), 0, 0
            );

            Location aiSpawn = grid.get(i + 1);

            int racerNumber = i + 1;
            ItemStack aiHelmet = buildAiHelmet(racerNumber, aiHeads[i]);

            MarbleRunner aiRunner = raceManager.buildStatsRunner(physics, aiSpawn, aiHelmet, aiData, () -> {
                // AI finishing is purely cosmetic -- it doesn't affect the player's tutorial progress.
                tracker.recordFinish("AI Racer " + racerNumber, aiData);
                if (player.isOnline()) maybeSendResults(player, tracker);
            }, player);
            engine.addRunner(aiRunner);
        }
    }

    /**
     * Sends the timing tower once, whenever the third (last) racer crosses
     * the line -- same hoverable-name format as a real race's results (see
     * RaceManager.broadcastResults), so hovering a name shows that marble's
     * team/rarity/level/stats here too.
     */
    private void maybeSendResults(Player player, RaceResultTracker tracker) {
        if (tracker.resultsSent || !tracker.isComplete()) return;
        tracker.resultsSent = true;

        player.sendMessage(Component.text("=== Race Results ===", NamedTextColor.GOLD));

        List<FinishEntry> finishes = tracker.finishes;
        for (int i = 0; i < finishes.size(); i++) {
            FinishEntry entry = finishes.get(i);
            NamedTextColor medal = switch (i) {
                case 0 -> NamedTextColor.GOLD;
                case 1 -> NamedTextColor.GRAY;
                case 2 -> NamedTextColor.DARK_RED;
                default -> NamedTextColor.WHITE;
            };

            Component line = Component.text((i + 1) + ". ", medal)
                    .append(raceManager.buildMarbleNameComponent(entry.name(), entry.data()))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(RaceManager.formatTime(entry.elapsedMs()), NamedTextColor.GREEN));

            player.sendMessage(line);
        }
    }

    /** Picks two AI head textures that differ from each other when the pool has more than one entry. */
    private HeadEntry[] pickTwoDistinctHeads() {
        if (headPool == null) return new HeadEntry[]{null, null};

        HeadEntry first = headPool.random();
        HeadEntry second = headPool.random();

        if (first != null && second != null && headPool.all().size() > 1) {
            int attempts = 0;
            while (second.base64().equals(first.base64()) && attempts < 10) {
                second = headPool.random();
                attempts++;
            }
        }
        return new HeadEntry[]{first, second};
    }

    private static final class RaceResultTracker {
        private final long startMs = System.currentTimeMillis();
        private final List<FinishEntry> finishes = new ArrayList<>();
        private final int totalRacers;
        private boolean resultsSent = false;

        RaceResultTracker(int totalRacers) {
            this.totalRacers = totalRacers;
        }

        void recordFinish(String name, MarbleData data) {
            finishes.add(new FinishEntry(name, System.currentTimeMillis() - startMs, data));
        }

        boolean isComplete() {
            return finishes.size() >= totalRacers;
        }
    }

    private record FinishEntry(String name, long elapsedMs, MarbleData data) {}

    /**
     * Builds an AI opponent's helmet. Kept as PLAYER_HEAD (same item type
     * as real marbles) so the size/scale on the armor stand matches -- a
     * block material would render oversized in the helmet slot. Takes an
     * explicit head (see pickTwoDistinctHeads) rather than rolling its
     * own, so the two AI opponents can be guaranteed to differ; falls
     * back to a plain default head if none was available.
     */
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

    public void handleQuit(Player player) {
        racing.remove(player.getUniqueId());
    }
}
