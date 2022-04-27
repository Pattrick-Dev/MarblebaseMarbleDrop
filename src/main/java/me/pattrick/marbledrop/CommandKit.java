package me.pattrick.marbledrop;

import java.util.UUID;
import java.io.IOException;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import java.io.File;
import org.bukkit.command.CommandExecutor;

public class CommandKit implements CommandExecutor
{
    File dataFolder;
    File filePath;
    FileConfiguration config;
    
    public CommandKit() {
        this.dataFolder = Bukkit.getPluginManager().getPlugin("MarbleBaseMD").getDataFolder();
        this.filePath = new File(this.dataFolder, "config.yml");
        this.config = (FileConfiguration)YamlConfiguration.loadConfiguration(this.filePath);
    }
    
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (sender instanceof Player) {
            final Player player = (Player)sender;
            if (args.length == 0) {
                if (player.isOp() || player.hasPermission("marbledrop.admin")) {
                    player.sendMessage(ChatColor.GREEN + "Marblebase MarbleDrop Plugin (Admin)\n\nBase command: /md\n \n" + ChatColor.DARK_GREEN + "Arguments:\ndebug:" + ChatColor.GREEN + " enable debug mode\n" + ChatColor.DARK_GREEN + "chance <double>:" + ChatColor.GREEN + " check chance and set chance\n" + ChatColor.DARK_GREEN + "cooldown/cd:" + ChatColor.GREEN + " check your cooldown\n" + ChatColor.DARK_GREEN + "removecooldown/rcd <player>:" + ChatColor.GREEN + " remove cooldown of a player");
                }
                else {
                    player.sendMessage(ChatColor.GREEN + "Marblebase MarbleDrop Plugin\nDeveloped by Pattrick\n \n" + ChatColor.DARK_GREEN + "/md cd:" + ChatColor.GREEN + " Check your cooldown until you can find a marble\n");
                }
            }
            else if (args[0].equalsIgnoreCase("debug")) {
                if (!player.hasPermission("marbledrop.debug")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission");
                }
                if (this.config.getBoolean("debug-mode")) {
                    this.config.set("debug-mode", (Object)false);
                    player.sendMessage(ChatColor.GRAY + "Debug mode: " + ChatColor.RED + "disabled.");
                    try {
                        this.config.options().copyDefaults(true);
                        this.config.save(this.filePath);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else if (!this.config.getBoolean("debug-mode")) {
                    this.config.set("debug-mode", (Object)true);
                    player.sendMessage(ChatColor.GRAY + "Debug mode:" + ChatColor.GREEN + " enabled.");
                    try {
                        this.config.options().copyDefaults(true);
                        this.config.save(this.filePath);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            else if (args[0].equalsIgnoreCase("chance")) {
                if (!player.hasPermission("marbledrop.chance")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission");
                }
                if (args.length == 1) {
                    player.sendMessage(ChatColor.YELLOW + "The current chance to get a marble is: " + ChatColor.BOLD + this.config.getString("drop-chance") + ChatColor.RESET + ChatColor.YELLOW + "%");
                }
                else if (args[1] != null && args.length == 2) {
                    try {
                        final double chanceDrop = Double.parseDouble(args[1]);
                        if (chanceDrop > 100.0) {
                            player.sendMessage(ChatColor.RED + "Chance cannot be higher than 100! " + ChatColor.BOLD + args[1] + ChatColor.RESET + ChatColor.RED + " is higher than 100!");
                        }
                        if (chanceDrop < 0.09) {
                            player.sendMessage(ChatColor.RED + "Chance cannot be lower than 0.1! " + ChatColor.BOLD + args[1] + ChatColor.RESET + ChatColor.RED + " is lower than 0.1!");
                        }
                        if (chanceDrop <= 100.0 && chanceDrop >= 0.1) {
                            player.sendMessage(ChatColor.BLUE + "Chance is now set to " + ChatColor.DARK_AQUA + ChatColor.BOLD + args[1] + "%");
                            this.config.set("drop-chance", (Object)args[1]);
                            try {
                                this.config.options().copyDefaults(true);
                                this.config.save(this.filePath);
                            }
                            catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }
                    catch (NumberFormatException e3) {
                        player.sendMessage(ChatColor.RED + "Please input a valid number 0.1 - 100! " + ChatColor.BOLD + args[1] + ChatColor.RESET + ChatColor.RED + " is not a valid number.");
                    }
                }
                else if (args.length > 2) {
                    player.sendMessage(ChatColor.RED + "Too many arguments! Usage: /md chance <Double>");
                }
            }
            else if (args[0].equalsIgnoreCase("cooldown") || args[0].equalsIgnoreCase("cd")) {
                if (!player.hasPermission("marbledrop.cooldown")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission");
                }
                if (!ListenEvents.cooldown.containsKey(player.getUniqueId())) {
                    player.sendMessage(ChatColor.GREEN + "You are able to find a marble!");
                }
                else {
                    final long timeElapsed = System.currentTimeMillis() - ListenEvents.cooldown.get(player.getUniqueId());
                    if (timeElapsed >= 86400000L) {
                        player.sendMessage(ChatColor.GREEN + "You are able to find a marble!");
                    }
                    else {
                        player.sendMessage(ChatColor.RED + "You cannot find another marble for " + (86400000L - timeElapsed) / 1000L / 60L + " minutes");
                    }
                }
            }
            else if (args[0].equalsIgnoreCase("removecooldown") || args[0].equalsIgnoreCase("rcd")) {
                if (!player.hasPermission("marbledrop.removecooldown")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission");
                }
                if (args.length >= 3) {
                    player.sendMessage(ChatColor.RED + "Error: Too many arguments");
                }
                else if (args.length == 1) {
                    player.sendMessage("Usage: /rcd <player>\nRemove a players marble cooldown");
                }
                else if (args.length == 2) {
                    final Player onlinePlayer = Bukkit.getPlayerExact(args[1]);
                    if (onlinePlayer == null) {
                        player.sendMessage(ChatColor.RED + "That player is not online!");
                    }
                    else if (onlinePlayer != null) {
                        final UUID onlineUUID = onlinePlayer.getUniqueId();
                        if (!ListenEvents.cooldown.containsKey(onlineUUID)) {
                            player.sendMessage(ChatColor.RED + "That player is not on cooldown!");
                        }
                        else if (ListenEvents.cooldown.containsKey(onlineUUID)) {
                            ListenEvents.cooldown.remove(onlineUUID);
                            player.sendMessage(ChatColor.GREEN + "Removed " + onlinePlayer.getDisplayName() + "'s cooldown!");
                        }
                    }
                }
            }
            else {
                player.sendMessage(ChatColor.RED + "Error: Argument not found");
            }
        }
        else {
            System.out.print("Please run all commands as a player!");
        }
        return true;
    }
}