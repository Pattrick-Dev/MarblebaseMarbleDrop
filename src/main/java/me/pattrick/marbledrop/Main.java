package me.pattrick.marbledrop;

import java.io.IOException;

import me.pattrick.marbledrop.progression.*;
import me.pattrick.marbledrop.progression.infusion.InfusionService;
import me.pattrick.marbledrop.progression.infusion.heads.HeadPool;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableCommand;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableListener;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableManager;
import me.pattrick.marbledrop.progression.taskmenu.TasksMenuListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin
{
    @Override
    public void onEnable() {

        // Ensure resource files exist
        if (!new File(getDataFolder(), "heads.yml").exists()) {
            saveResource("heads.yml", false);
        }
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveResource("config.yml", false);
        }
        if (!new File(getDataFolder(), "cooldowns.yml").exists()) {
            saveResource("cooldowns.yml", false);
        }
        // New: infusion tables storage
        if (!new File(getDataFolder(), "infusion_tables.yml").exists()) {
            try {
                getDataFolder().mkdirs();
                new File(getDataFolder(), "infusion_tables.yml").createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Init marble keys
        MarbleItem.init(this);

        // Progression system
        DustManager dustManager = new DustManager(this);
        TaskManager taskManager = new TaskManager(this, dustManager);
        getServer().getPluginManager().registerEvents(new ProgressionListener(taskManager), this);

        // Load heads pool (still used elsewhere / kept intact)
        HeadPool headPool = new HeadPool(this);
        headPool.load();

        // Infusion
        InfusionService infusionService = new InfusionService(this, dustManager, headPool);

        // Commands
        getCommand("dust").setExecutor(new DustCommand(dustManager, infusionService));
        getCommand("tasks").setExecutor(new TasksCommand(this, taskManager));
        getCommand("tasksadmin").setExecutor(new TasksAdminCommand(taskManager));

        // New: infusion table admin command
        InfusionTableManager tableManager = new InfusionTableManager(this);
        getCommand("infusiontable").setExecutor(new InfusionTableCommand(tableManager));

        getCommand("md").setExecutor(new CommandKit());
        getCommand("md").setTabCompleter(new CommandKitTabCompletion());

        // Listeners
        getServer().getPluginManager().registerEvents(new ListenEvents(), this);
        getServer().getPluginManager().registerEvents(new TasksMenuListener(this, taskManager), this);

        // New: infusion table interactions + GUI
        getServer().getPluginManager().registerEvents(
                new InfusionTableListener(tableManager, dustManager, infusionService),
                this
        );

        // Config handling
        getConfig().options().copyDefaults(true);
        saveConfig();

        final File dataFolder = Bukkit.getPluginManager().getPlugin("MarbleBaseMD").getDataFolder();
        final File filePath = new File(dataFolder, "cooldowns.yml");
        final FileConfiguration config = YamlConfiguration.loadConfiguration(filePath);
        if (config.contains("cooldown")) {
            CooldownManager.CooldownCheckEnable();
        }

        // Action bar tracker
        ActionBarTaskTracker tracker = new ActionBarTaskTracker(this, taskManager);
        tracker.start();
    }

    @Override
    public void onDisable() {
        if (!ListenEvents.cooldown.isEmpty()) {
            try {
                CooldownManager.CooldownCheckDisable();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
