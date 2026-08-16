package me.pattrick.marbledrop;

import me.pattrick.marbledrop.marble.MarbleKeys;
import me.pattrick.marbledrop.marble.MarblePlacementListener;
import me.pattrick.marbledrop.marble.PlacedMarbleManager;
import me.pattrick.marbledrop.marble.MarbleDisplayAmbient;
import me.pattrick.marbledrop.marble.MarbleDisplayMenuListener;
import me.pattrick.marbledrop.progression.*;
import me.pattrick.marbledrop.progression.infusion.InfusionService;
import me.pattrick.marbledrop.progression.infusion.heads.HeadPool;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableAmbient;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableCommand;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableListener;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableManager;
import me.pattrick.marbledrop.progression.RecipesCommand;
import me.pattrick.marbledrop.progression.RecipesMenuListener;
import me.pattrick.marbledrop.feedback.FeedbackCommand;
import me.pattrick.marbledrop.progression.taskmenu.TasksMenuListener;
import me.pattrick.marbledrop.progression.upgrades.UpgradeMenuListener;
import me.pattrick.marbledrop.progression.upgrades.UpgradeStationAmbient;
import me.pattrick.marbledrop.progression.upgrades.UpgradeStationCommand;
import me.pattrick.marbledrop.progression.upgrades.UpgradeStationListener;
import me.pattrick.marbledrop.progression.upgrades.UpgradeStationManager;
import me.pattrick.marbledrop.races.*;
import me.pattrick.marbledrop.races.team.CustomTeamManager;
import me.pattrick.marbledrop.races.team.TeamCommand;
import me.pattrick.marbledrop.races.team.TeamMenuListener;
import me.pattrick.marbledrop.tutorial.TutorialCommand;
import me.pattrick.marbledrop.tutorial.TutorialCraftFrameManager;
import me.pattrick.marbledrop.tutorial.TutorialCraftFrameStore;
import me.pattrick.marbledrop.tutorial.TutorialCraftHook;
import me.pattrick.marbledrop.tutorial.TutorialInteractionGuard;
import me.pattrick.marbledrop.tutorial.TutorialListener;
import me.pattrick.marbledrop.tutorial.TutorialLocationStore;
import me.pattrick.marbledrop.tutorial.TutorialManager;
import me.pattrick.marbledrop.tutorial.TutorialProgressPoller;
import me.pattrick.marbledrop.tutorial.TutorialRaceService;
import me.pattrick.marbledrop.tutorial.TutorialRecyclerHook;
import me.pattrick.marbledrop.tutorial.TutorialTabListPrivacy;
import me.pattrick.marbledrop.tutorial.TutorialTasksHandler;
import me.pattrick.marbledrop.tutorial.TutorialUpgradeHook;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class Main extends JavaPlugin {

    private MdConfig mdConfig;

    // racing
    private TrackVisualizer trackVisualizer;
    private MarbleRaceEngine raceEngine;

    // persisted signs
    private RaceSignManager raceSignManager;

    // watch manager
    private RaceWatchManager raceWatchManager;

    // scheduled server races
    private ScheduledRaceManager scheduledRaceManager;

    // progression ambience
    private InfusionTableAmbient infusionAmbient;
    private RecyclerAmbient recyclerAmbient;
    private UpgradeStationAmbient upgradeAmbient;
    private MarbleDisplayAmbient marbleDisplayAmbient;

    // tutorial
    private TutorialProgressPoller tutorialPoller;
    private TutorialManager tutorialManager;
    private TutorialCraftFrameManager craftFrameManager;

    public MdConfig cfg() {
        return mdConfig;
    }

    public InfusionTableAmbient getInfusionAmbient() {
        return infusionAmbient;
    }

    public RecyclerAmbient getRecyclerAmbient() {
        return recyclerAmbient;
    }

    public MarbleDisplayAmbient getMarbleDisplayAmbient() {
        return marbleDisplayAmbient;
    }

    public UpgradeStationAmbient getUpgradeAmbient() {
        return upgradeAmbient;
    }

    @Override
    public void onEnable() {

        // -------------------- Config --------------------
        saveDefaultConfig();
        mdConfig = new MdConfig(this);

        // -------------------- Init marble keys --------------------
        MarbleKeys.init(this);

        // -------------------- Silent item grants --------------------
        SilentGive.register(this);

        // -------------------- Ensure resource files exist --------------------
        if (!new File(getDataFolder(), "heads.yml").exists()) {
            saveResource("heads.yml", false);
        }

        // -------------------- Storage files --------------------
        ensureFile("infusion_tables.yml");
        ensureFile("recyclers.yml");
        ensureFile("upgrade_stations.yml");
        ensureFile("tracks.yml");
        ensureFile("race-signs.yml");
        ensureFile("race-watch.yml");
        ensureFile("race-pending-marbles.yml");

        // -------------------- Progression system --------------------
        // Moved ahead of Racing (below) so taskManager already exists by
        // the time RaceManager/RaceBoostListener are constructed and can
        // be wired in directly instead of needing a later setter pass.
        DustManager dustManager = new DustManager(this);

        TaskManager taskManager = new TaskManager(this, dustManager);

        ProgressionListener progressionListener = new ProgressionListener(taskManager);
        getServer().getPluginManager().registerEvents(progressionListener, this);

        // -------------------- ProtocolLib-backed inventory overlay --------------------
        // Checked once, early, since both track-building's kit tools below
        // and the race Boost/Glow features further down key off the same
        // RaceInventoryOverlay instance. Softdepend in plugin.yml, not a
        // hard depend - without it, track-building tools fall back to
        // real inventory items and race Boost/Glow fall back to real
        // inventory items too (with RaceWatchManager's own real
        // clear-and-restore protecting those).
        boolean protocolLibEnabled = getServer().getPluginManager().isPluginEnabled("ProtocolLib");
        RaceInventoryOverlay inventoryOverlay = protocolLibEnabled ? new RaceInventoryOverlay(this) : null;

        // -------------------- Racing --------------------
        TrackManager trackManager = new TrackManager(this);
        trackVisualizer = new TrackVisualizer(this, trackManager);

        raceEngine = new MarbleRaceEngine(this);
        raceEngine.start();

        // Tracks who's building which track and, via inventoryOverlay,
        // hides their real inventory behind fake kit-tool items for the
        // duration - see TrackCreationKit.giveKit()/TrackBuildToolsListener.
        // The real inventory is never touched, so there's nothing to save
        // or restore, on disk or otherwise.
        TrackBuildInventoryManager trackBuildInventory = new TrackBuildInventoryManager(inventoryOverlay);
        getServer().getPluginManager().registerEvents(trackBuildInventory, this);

        TrackCommand trackCommand = new TrackCommand(trackManager, trackVisualizer, raceEngine, trackBuildInventory);

        // Track GUI listener
        getServer().getPluginManager().registerEvents(
                new TrackGuiListener(this, trackManager, trackVisualizer, trackBuildInventory),
                this
        );

        // Build kit tools listener (point/undo/preview/watch/finish - see TrackCreationKit)
        getServer().getPluginManager().registerEvents(
                new TrackBuildToolsListener(this, trackManager, trackVisualizer, trackBuildInventory),
                this
        );

        // Race entry flow
        RaceManager raceManager = new RaceManager(trackManager, raceEngine, mdConfig);
        // Handles giving a taken marble back on quit/kick (pre-race lobby)
        // and flushing any queued offline returns on join - see RaceManager's
        // returnMarbleToOwner()/onPlayerQuit()/onPlayerJoin().
        getServer().getPluginManager().registerEvents(raceManager, this);

        // Watch manager (inventory safe)
        raceWatchManager = new RaceWatchManager(this, trackManager, raceEngine);
        getServer().getPluginManager().registerEvents(raceWatchManager, this);

        // Wire watch into race manager (auto-watch on start)
        raceManager.setWatchManager(raceWatchManager);
        raceManager.setTaskManager(taskManager);

        // ProtocolLib-backed features (see RaceGlowPrivacy) - glowPrivacy
        // itself doesn't need to exist without ProtocolLib; inventoryOverlay
        // was already constructed (or left null) above.
        RaceGlowPrivacy glowPrivacy = null;
        if (protocolLibEnabled) {
            glowPrivacy = new RaceGlowPrivacy(this);
            raceWatchManager.setInventoryOverlay(inventoryOverlay);
            raceManager.setInventoryOverlay(inventoryOverlay);
        } else {
            getLogger().info("ProtocolLib not found - marble glow will be visible to everyone instead of just the racer who toggled it, "
                    + "and race items (Boost/Glow) will be real inventory items instead of packet overlays.");
        }

        // Boost/Glow item click handlers - must run before RaceWatchManager's
        // own interact handler cancels everything while watching (see RaceBoostListener/RaceGlowListener).
        getServer().getPluginManager().registerEvents(new RaceBoostListener(this, raceManager, inventoryOverlay, taskManager), this);
        getServer().getPluginManager().registerEvents(new RaceGlowListener(this, raceManager, glowPrivacy, inventoryOverlay), this);

        // Race GUI listener (click-to-join menu)
        getServer().getPluginManager().registerEvents(
                new RaceGuiListener(trackManager, raceManager),
                this
        );

        // Loadout picker (pre-race strategy - click an already-joined track to open it)
        getServer().getPluginManager().registerEvents(
                new RaceLoadoutGuiListener(raceManager),
                this
        );

        // Race Signs (create + click signs) - now expects watch manager too
        raceSignManager = new RaceSignManager(this);
        getServer().getPluginManager().registerEvents(
                new RaceSignListener(trackManager, raceManager, raceSignManager, raceWatchManager),
                this
        );

        // -------------------- Custom teams --------------------
        CustomTeamManager teamManager = new CustomTeamManager(this);
        getServer().getPluginManager().registerEvents(new TeamMenuListener(this, teamManager), this);
        TeamCommand teamCommand = new TeamCommand(teamManager);

        // -------------------- Load heads pool --------------------
        HeadPool headPool = new HeadPool(this);
        headPool.load();

        // -------------------- Scheduled server races --------------------
        // Needs raceManager/raceWatchManager/raceEngine (racing section above)
        // and dustManager/headPool (just above), so it's wired here rather
        // than alongside the rest of the racing setup.
        scheduledRaceManager = new ScheduledRaceManager(
                this, mdConfig, trackManager, raceManager, raceWatchManager, raceEngine, dustManager, headPool
        );
        // Listens for PlayerJoinEvent so a fresh joiner mid-countdown still sees the boss bar.
        getServer().getPluginManager().registerEvents(scheduledRaceManager, this);
        scheduledRaceManager.start();

        getServer().getPluginManager().registerEvents(new RaceMotdListener(scheduledRaceManager), this);
        getServer().getPluginManager().registerEvents(new RaceScheduleMenuListener(this, mdConfig), this);
        getServer().getPluginManager().registerEvents(new RaceDailyTimesMenuListener(this, mdConfig, scheduledRaceManager), this);

        RaceCommand raceCommand = new RaceCommand(raceManager, trackManager, raceWatchManager, scheduledRaceManager, mdConfig);

        // -------------------- Infusion service --------------------
        InfusionService infusionService = new InfusionService(this, dustManager, headPool);
        infusionService.setTaskManager(taskManager);

        // -------------------- Infusion tables --------------------
        InfusionTableManager tableManager = new InfusionTableManager(this);

        infusionAmbient = new InfusionTableAmbient(this, tableManager, dustManager);
        infusionAmbient.start();

        getServer().getPluginManager().registerEvents(
                new InfusionTableListener(tableManager, dustManager, infusionService),
                this
        );

        InfusionTableCommand infusionTableCommand = new InfusionTableCommand(this, tableManager, infusionAmbient);

        // -------------------- Recycler --------------------
        MarbleRecyclerManager recyclerManager = new MarbleRecyclerManager(this);

        recyclerAmbient = new RecyclerAmbient(this, recyclerManager);
        recyclerAmbient.start();

        MarbleRecyclerCommand marbleRecyclerCommand = new MarbleRecyclerCommand(this, recyclerManager, recyclerAmbient);

        getServer().getPluginManager().registerEvents(
                new MarbleRecyclerListener(this, recyclerManager, dustManager, taskManager),
                this
        );

        // -------------------- Upgrades --------------------
        UpgradeStationManager upgradeStations = new UpgradeStationManager(this);

        upgradeAmbient = new UpgradeStationAmbient(this, upgradeStations);
        upgradeAmbient.start();

        // Constructed after upgradeAmbient (not before, like it used to
        // be) so /md upgrades remove can hand it the ambient cleanup
        // callback - same as MarbleRecyclerCommand/InfusionTableCommand
        // already do for their own station types.
        UpgradeStationCommand upgradeStationCommand = new UpgradeStationCommand(this, upgradeStations, upgradeAmbient);

        getServer().getPluginManager().registerEvents(
                new UpgradeStationListener(this, upgradeStations, dustManager),
                this
        );

        getServer().getPluginManager().registerEvents(
                new UpgradeMenuListener(dustManager),
                this
        );

        // -------------------- Station crafting recipes --------------------
        // Infusion tables, upgrade stations, and recyclers are obtained via
        // crafting recipes and become "real" stations only once placed --
        // see StationBlockListener for the place/break registration.
        StationRecipes.registerAll(this);

        getServer().getPluginManager().registerEvents(
                new StationBlockListener(this, tableManager, infusionAmbient, upgradeStations, upgradeAmbient, recyclerManager, recyclerAmbient),
                this
        );

        // -------------------- Tutorial --------------------
        // Wired here (not earlier) because it needs trackManager/raceEngine/
        // raceManager (racing section), dustManager (progression section),
        // and tableManager/recyclerManager/upgradeStations (all just above)
        // to already exist.
        TutorialLocationStore tutorialLocations = new TutorialLocationStore(this);

        TutorialCraftFrameStore craftFrameStore = new TutorialCraftFrameStore(this);
        craftFrameManager = new TutorialCraftFrameManager(this, craftFrameStore);
        craftFrameManager.start();

        // Nullable - see TutorialTabListPrivacy#createIfAvailable. Without
        // ProtocolLib, hiding a tutorial-taker from others just falls back
        // to hideEntity's default behavior (their tab entry disappears too).
        TutorialTabListPrivacy tabListPrivacy = TutorialTabListPrivacy.createIfAvailable(this);

        tutorialManager = new TutorialManager(this, dustManager, mdConfig, tutorialLocations, craftFrameManager, tabListPrivacy);

        TutorialTasksHandler tutorialTasksHandler = new TutorialTasksHandler(this, tutorialManager);
        getServer().getPluginManager().registerEvents(tutorialTasksHandler, this);

        TutorialRaceService tutorialRaceService = new TutorialRaceService(
                this, tutorialManager, tutorialLocations, raceManager, trackManager, raceEngine, raceWatchManager, headPool
        );

        getServer().getPluginManager().registerEvents(
                new TutorialListener(this, tutorialManager, tutorialTasksHandler, tutorialRaceService),
                this
        );

        getServer().getPluginManager().registerEvents(
                new TutorialRecyclerHook(this, tutorialManager, recyclerManager),
                this
        );

        getServer().getPluginManager().registerEvents(
                new TutorialCraftHook(this, tutorialManager),
                this
        );

        getServer().getPluginManager().registerEvents(
                new TutorialInteractionGuard(this, tutorialManager, tableManager, upgradeStations, recyclerManager),
                this
        );

        getServer().getPluginManager().registerEvents(
                new TutorialUpgradeHook(tutorialManager),
                this
        );

        TutorialProgressPoller poller = new TutorialProgressPoller(this, tutorialManager);
        poller.start();
        this.tutorialPoller = poller;

        TutorialCommand tutorialCommand = new TutorialCommand(tutorialManager, craftFrameManager);

        // -------------------- Placed marble displays --------------------
        PlacedMarbleManager placedMarbleManager = new PlacedMarbleManager(this);
        marbleDisplayAmbient = new MarbleDisplayAmbient(this, placedMarbleManager);
        marbleDisplayAmbient.start();

        // -------------------- Core listeners --------------------
        getServer().getPluginManager().registerEvents(new ListenEvents(), this);
        getServer().getPluginManager().registerEvents(
                new MarblePlacementListener(placedMarbleManager, marbleDisplayAmbient), this);
        getServer().getPluginManager().registerEvents(new MarbleDisplayMenuListener(), this);
        getServer().getPluginManager().registerEvents(new TasksMenuListener(this, taskManager), this);
        getServer().getPluginManager().registerEvents(new RecipesMenuListener(this), this);

        // -------------------- Commands --------------------
        DustCommand dustCommand = new DustCommand(dustManager);
        TasksCommand tasksCommand = new TasksCommand(this, taskManager);
        TasksAdminCommand tasksAdminCommand = new TasksAdminCommand(taskManager);
        DustAdminCommand dustAdminCommand = new DustAdminCommand(dustManager);
        RecipesCommand recipesCommand = new RecipesCommand(this);

        // -------------------- Register ONLY /md (router) --------------------
        CommandKit md = new CommandKit(
                this,
                mdConfig,
                infusionTableCommand,
                dustCommand,
                dustAdminCommand,
                marbleRecyclerCommand,
                tasksCommand,
                tasksAdminCommand,
                upgradeStationCommand,
                trackCommand,
                raceCommand,
                teamCommand,
                tutorialCommand,
                recipesCommand,
                scheduledRaceManager
        );

        CommandKitTabCompletion mdTabCompletion = new CommandKitTabCompletion(trackManager);

        if (getCommand("md") != null) {
            getCommand("md").setExecutor(md);
            getCommand("md").setTabCompleter(mdTabCompletion);
        } else {
            getLogger().severe("Command 'md' is not defined in plugin.yml!");
        }

        if (getCommand("tasks") != null) {
            getCommand("tasks").setExecutor(md);
            getCommand("tasks").setTabCompleter(mdTabCompletion);
        } else {
            getLogger().severe("Command 'tasks' is not defined in plugin.yml!");
        }

        if (getCommand("recipes") != null) {
            getCommand("recipes").setExecutor(md);
            getCommand("recipes").setTabCompleter(mdTabCompletion);
        } else {
            getLogger().severe("Command 'recipes' is not defined in plugin.yml!");
        }

        // -------------------- Feedback --------------------
        // Shares a backend with the marblebase.net website's feedback
        // form (a Cloudflare Pages Function + D1), tagged source=minecraft.
        // See website-side setup notes for the corresponding endpoint.
        getConfig().addDefault("feedback.endpoint-url", "https://marblebase.net/api/feedback");
        getConfig().addDefault("feedback.secret", "CHANGE_ME");
        getConfig().options().copyDefaults(true);
        saveConfig();

        String feedbackUrl = getConfig().getString("feedback.endpoint-url");
        String feedbackSecret = getConfig().getString("feedback.secret");

        if (getCommand("feedback") != null) {
            getCommand("feedback").setExecutor(new FeedbackCommand(this, feedbackUrl, feedbackSecret));
        } else {
            getLogger().severe("Command 'feedback' is not defined in plugin.yml!");
        }

        // -------------------- Action bar tracker --------------------
        ActionBarTaskTracker tracker = new ActionBarTaskTracker(this, taskManager);
        tracker.start();
    }

    @Override
    public void onDisable() {

        if (tutorialPoller != null) {
            tutorialPoller.stop();
            tutorialPoller = null;
        }

        if (tutorialManager != null) {
            tutorialManager.shutdown();
            tutorialManager = null;
        }

        if (scheduledRaceManager != null) {
            scheduledRaceManager.stop();
            scheduledRaceManager = null;
        }

        if (infusionAmbient != null) {
            infusionAmbient.stop();
            infusionAmbient = null;
        }

        if (recyclerAmbient != null) {
            recyclerAmbient.stop();
            recyclerAmbient = null;
        }

        if (marbleDisplayAmbient != null) {
            marbleDisplayAmbient.stop();
            marbleDisplayAmbient = null;
        }

        if (craftFrameManager != null) {
            craftFrameManager.stop();
            craftFrameManager = null;
        }

        if (trackVisualizer != null) {
            trackVisualizer.shutdown();
            trackVisualizer = null;
        }

        if (raceEngine != null) {
            raceEngine.stop();
            raceEngine = null;
        }

        raceSignManager = null;

        if (raceWatchManager != null) {
            raceWatchManager.shutdown();
            raceWatchManager = null;
        }

        mdConfig = null;
    }

    private void ensureFile(String name) {
        File f = new File(getDataFolder(), name);
        if (!f.exists()) {
            try {
                getDataFolder().mkdirs();
                f.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Failed to create " + name + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
