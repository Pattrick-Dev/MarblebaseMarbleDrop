package me.pattrick.marbledrop;

import me.pattrick.marbledrop.marble.MarbleKeys;
import me.pattrick.marbledrop.progression.*;
import me.pattrick.marbledrop.progression.infusion.InfusionService;
import me.pattrick.marbledrop.progression.infusion.heads.HeadPool;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableAmbient;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableCommand;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableListener;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableManager;
import me.pattrick.marbledrop.progression.taskmenu.TasksMenuListener;
import me.pattrick.marbledrop.progression.upgrades.UpgradeMenuListener;
import me.pattrick.marbledrop.progression.upgrades.UpgradeStationCommand;
import me.pattrick.marbledrop.progression.upgrades.UpgradeStationListener;
import me.pattrick.marbledrop.progression.upgrades.UpgradeStationManager;
import me.pattrick.marbledrop.races.*;
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

    // progression ambience
    private InfusionTableAmbient infusionAmbient;
    private RecyclerAmbient recyclerAmbient;

    public MdConfig cfg() {
        return mdConfig;
    }

    public InfusionTableAmbient getInfusionAmbient() {
        return infusionAmbient;
    }

    public RecyclerAmbient getRecyclerAmbient() {
        return recyclerAmbient;
    }

    @Override
    public void onEnable() {

        // -------------------- Config --------------------
        saveDefaultConfig();
        mdConfig = new MdConfig(this);

        // -------------------- Init marble keys --------------------
        MarbleKeys.init(this);

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

        // -------------------- Racing --------------------
        TrackManager trackManager = new TrackManager(this);
        trackVisualizer = new TrackVisualizer(this, trackManager);

        raceEngine = new MarbleRaceEngine(this);
        raceEngine.start();

        TrackCommand trackCommand = new TrackCommand(trackManager, trackVisualizer, raceEngine);

        // Track GUI listener
        getServer().getPluginManager().registerEvents(
                new TrackGuiListener(this, trackManager, trackVisualizer),
                this
        );

        // Point tool listener
        getServer().getPluginManager().registerEvents(
                new TrackPointToolListener(this, trackManager),
                this
        );

        // Race entry flow
        RaceManager raceManager = new RaceManager(trackManager, raceEngine);

        // Watch manager (inventory safe)
        raceWatchManager = new RaceWatchManager(this, trackManager);
        getServer().getPluginManager().registerEvents(raceWatchManager, this);

        // Wire watch into race manager (auto-watch on start)
        raceManager.setWatchManager(raceWatchManager);

        RaceCommand raceCommand = new RaceCommand(raceManager, trackManager, raceWatchManager);

        // Race GUI listener (click-to-join menu)
        getServer().getPluginManager().registerEvents(
                new RaceGuiListener(trackManager, raceManager),
                this
        );

        // Race Signs (create + click signs) - now expects watch manager too
        raceSignManager = new RaceSignManager(this);
        getServer().getPluginManager().registerEvents(
                new RaceSignListener(trackManager, raceManager, raceSignManager, raceWatchManager),
                this
        );

        // -------------------- Progression system --------------------
        DustManager dustManager = new DustManager(this);
        TaskManager taskManager = new TaskManager(this, dustManager);
        getServer().getPluginManager().registerEvents(
                new ProgressionListener(taskManager),
                this
        );

        // -------------------- Load heads pool --------------------
        HeadPool headPool = new HeadPool(this);
        headPool.load();

        // -------------------- Infusion service --------------------
        InfusionService infusionService = new InfusionService(this, dustManager, headPool);

        // -------------------- Infusion tables --------------------
        InfusionTableManager tableManager = new InfusionTableManager(this);

        infusionAmbient = new InfusionTableAmbient(this, tableManager, dustManager);
        infusionAmbient.start();

        getServer().getPluginManager().registerEvents(
                new InfusionTableListener(tableManager, dustManager, infusionService),
                this
        );

        InfusionTableCommand infusionTableCommand = new InfusionTableCommand(tableManager, infusionAmbient);

        // -------------------- Recycler --------------------
        MarbleRecyclerManager recyclerManager = new MarbleRecyclerManager(this);

        recyclerAmbient = new RecyclerAmbient(this, recyclerManager);
        recyclerAmbient.start();

        MarbleRecyclerCommand marbleRecyclerCommand = new MarbleRecyclerCommand(recyclerManager, recyclerAmbient);

        getServer().getPluginManager().registerEvents(
                new MarbleRecyclerListener(this, recyclerManager, dustManager),
                this
        );

        // -------------------- Upgrades --------------------
        UpgradeStationManager upgradeStations = new UpgradeStationManager(this);
        UpgradeStationCommand upgradeStationCommand = new UpgradeStationCommand(upgradeStations);

        getServer().getPluginManager().registerEvents(
                new UpgradeStationListener(this, upgradeStations, dustManager),
                this
        );

        getServer().getPluginManager().registerEvents(
                new UpgradeMenuListener(dustManager),
                this
        );

        // -------------------- Core listeners --------------------
        getServer().getPluginManager().registerEvents(new ListenEvents(), this);
        getServer().getPluginManager().registerEvents(new TasksMenuListener(this, taskManager), this);

        // -------------------- Commands --------------------
        DustCommand dustCommand = new DustCommand(dustManager, infusionService);
        TasksCommand tasksCommand = new TasksCommand(this, taskManager);
        TasksAdminCommand tasksAdminCommand = new TasksAdminCommand(taskManager);
        DustAdminCommand dustAdminCommand = new DustAdminCommand(dustManager);

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
                raceCommand
        );

        if (getCommand("md") != null) {
            getCommand("md").setExecutor(md);
            getCommand("md").setTabCompleter(new CommandKitTabCompletion());
        } else {
            getLogger().severe("Command 'md' is not defined in plugin.yml!");
        }

        // -------------------- Action bar tracker --------------------
        ActionBarTaskTracker tracker = new ActionBarTaskTracker(this, taskManager);
        tracker.start();
    }

    @Override
    public void onDisable() {

        if (infusionAmbient != null) {
            infusionAmbient.stop();
            infusionAmbient = null;
        }

        if (recyclerAmbient != null) {
            recyclerAmbient.stop();
            recyclerAmbient = null;
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
        raceWatchManager = null;
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
