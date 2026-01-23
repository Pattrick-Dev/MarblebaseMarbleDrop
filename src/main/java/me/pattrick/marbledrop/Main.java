package me.pattrick.marbledrop;

import java.io.IOException;

import me.pattrick.marbledrop.progression.*;
import me.pattrick.marbledrop.progression.taskmenu.TasksMenuListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin
{
    public void onEnable() {
        if (!new File(getDataFolder(), "heads.yml").exists()) {
            saveResource("heads.yml", false);
        }
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveResource("config.yml", false);
        }
        if (!new File(getDataFolder(), "cooldowns.yml").exists()) {
            saveResource("cooldowns.yml", false);
        }

        MarbleItem.init(this);
        // Progression system
        DustManager dustManager = new DustManager(this);
        TaskManager taskManager = new TaskManager(this, dustManager);
        getServer().getPluginManager().registerEvents(new ProgressionListener(taskManager), this);

        getCommand("dust").setExecutor(new DustCommand(dustManager));
        getCommand("tasks").setExecutor(new TasksCommand(this, taskManager));
        getCommand("tasksadmin").setExecutor(new TasksAdminCommand(taskManager));
        getCommand("md").setExecutor(new CommandKit());
        getCommand("md").setTabCompleter(new CommandKitTabCompletion());
        getServer().getPluginManager().registerEvents(new ListenEvents(), this);
        getConfig().options().copyDefaults(true);
        saveConfig();
        final File dataFolder = Bukkit.getPluginManager().getPlugin("MarbleBaseMD").getDataFolder();
        final File filePath = new File(dataFolder, "cooldowns.yml");
        final FileConfiguration config = YamlConfiguration.loadConfiguration(filePath);
        if (config.contains("cooldown")) {
            CooldownManager.CooldownCheckEnable();
        }

        ActionBarTaskTracker tracker = new ActionBarTaskTracker(this, taskManager);
        tracker.start();

        getServer().getPluginManager().registerEvents(new TasksMenuListener(this, taskManager), this);


    }
    
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