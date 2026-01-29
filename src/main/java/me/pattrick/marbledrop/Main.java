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
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class Main extends JavaPlugin {

    private InfusionTableAmbient infusionAmbient;
    private RecyclerAmbient recyclerAmbient;

    public InfusionTableAmbient getInfusionAmbient() {
        return infusionAmbient;
    }

    public RecyclerAmbient getRecyclerAmbient() {
        return recyclerAmbient;
    }

    @Override
    public void onEnable() {
        MarbleKeys.init(this);
        // -------------------- Ensure resource files exist --------------------
        if (!new File(getDataFolder(), "heads.yml").exists()) {
            saveResource("heads.yml", false);
        }
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveResource("config.yml", false);
        }

        // infusion tables storage
        File infusionTablesFile = new File(getDataFolder(), "infusion_tables.yml");
        if (!infusionTablesFile.exists()) {
            try {
                getDataFolder().mkdirs();
                infusionTablesFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // recyclers storage
        File recyclersFile = new File(getDataFolder(), "recyclers.yml");
        if (!recyclersFile.exists()) {
            try {
                getDataFolder().mkdirs();
                recyclersFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // ✅ upgrade stations storage (NEW)
        File upgradeStationsFile = new File(getDataFolder(), "upgrade_stations.yml");
        if (!upgradeStationsFile.exists()) {
            try {
                getDataFolder().mkdirs();
                upgradeStationsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // -------------------- Init marble keys --------------------
        MarbleKeys.init(this);

        // -------------------- Progression system --------------------
        DustManager dustManager = new DustManager(this);
        TaskManager taskManager = new TaskManager(this, dustManager);
        getServer().getPluginManager().registerEvents(new ProgressionListener(taskManager), this);

        // -------------------- Load heads pool --------------------
        HeadPool headPool = new HeadPool(this);
        headPool.load();

        // -------------------- Infusion service --------------------
        InfusionService infusionService = new InfusionService(this, dustManager, headPool);

        // -------------------- Infusion tables (single manager + ambient) --------------------
        InfusionTableManager tableManager = new InfusionTableManager(this);

        infusionAmbient = new InfusionTableAmbient(this, tableManager, dustManager);
        infusionAmbient.start();

        getServer().getPluginManager().registerEvents(
                new InfusionTableListener(tableManager, dustManager, infusionService),
                this
        );

        InfusionTableCommand infusionTableCommand = new InfusionTableCommand(tableManager, infusionAmbient);

        // -------------------- Recycler (single manager + ambient) --------------------
        MarbleRecyclerManager recyclerManager = new MarbleRecyclerManager(this);

        recyclerAmbient = new RecyclerAmbient(this, recyclerManager);
        recyclerAmbient.start();

        MarbleRecyclerCommand marbleRecyclerCommand = new MarbleRecyclerCommand(recyclerManager, recyclerAmbient);

        getServer().getPluginManager().registerEvents(
                new MarbleRecyclerListener(this, recyclerManager, dustManager),
                this
        );

        // -------------------- Upgrades (NEW: station + GUI) --------------------
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

        // -------------------- Command executors (existing constructors) --------------------
        DustCommand dustCommand = new DustCommand(dustManager, infusionService);
        TasksCommand tasksCommand = new TasksCommand(this, taskManager);
        TasksAdminCommand tasksAdminCommand = new TasksAdminCommand(taskManager);
        me.pattrick.marbledrop.progression.DustAdminCommand dustAdminCommand =
                new me.pattrick.marbledrop.progression.DustAdminCommand(dustManager);

        // -------------------- Register ONLY /md (router) --------------------
        // ✅ Added upgradeStationCommand into the router list
        CommandKit md = new CommandKit(
                this,
                infusionTableCommand,
                dustCommand,
                dustAdminCommand,
                marbleRecyclerCommand,
                tasksCommand,
                tasksAdminCommand,
                upgradeStationCommand
        );

        if (getCommand("md") != null) {
            getCommand("md").setExecutor(md);
            getCommand("md").setTabCompleter(new CommandKitTabCompletion());
        } else {
            getLogger().severe("Command 'md' is not defined in plugin.yml!");
        }

        // -------------------- Config handling --------------------
        getConfig().options().copyDefaults(true);
        saveConfig();

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
    }
}
