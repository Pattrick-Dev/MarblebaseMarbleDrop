package me.pattrick.marbledrop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class CommandKit implements CommandExecutor {

    private final JavaPlugin plugin;

    private final CommandExecutor infusionTableCommand;// dust infuse
    private final CommandExecutor dustCommand;
    private final CommandExecutor dustAdminCommand;
    private final CommandExecutor marbleRecyclerCommand;
    private final CommandExecutor tasksCommand;
    private final CommandExecutor tasksAdminCommand;

    private final File filePath;
    private final FileConfiguration config;

    public CommandKit(
            JavaPlugin plugin,
            CommandExecutor infusionTableCommand,
            CommandExecutor dustCommand,
            CommandExecutor dustAdminCommand,
            CommandExecutor marbleRecyclerCommand,
            CommandExecutor tasksCommand,
            CommandExecutor tasksAdminCommand
    ) {
        this.plugin = plugin;
        this.infusionTableCommand = infusionTableCommand;
        this.dustCommand = dustCommand;
        this.dustAdminCommand = dustAdminCommand;
        this.marbleRecyclerCommand = marbleRecyclerCommand;
        this.tasksCommand = tasksCommand;
        this.tasksAdminCommand = tasksAdminCommand;

        this.filePath = new File(plugin.getDataFolder(), "config.yml");
        this.config = YamlConfiguration.loadConfiguration(this.filePath);
    }

    private static String[] shiftArgs(String[] args, int by) {
        if (args.length <= by) return new String[0];
        String[] out = new String[args.length - by];
        System.arraycopy(args, by, out, 0, out.length);
        return out;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!cmd.getName().equalsIgnoreCase("md")) {
            sender.sendMessage(ChatColor.RED + "Use: /md");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Please run all commands as a player!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        // ---------------- ROUTING ----------------

        if (sub.equals("table") || sub.equals("infusiontable")) {
            return infusionTableCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }

        if (sub.equals("recycler") || sub.equals("recycle")) {
            return marbleRecyclerCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }

        if (sub.equals("tasks")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("admin")) {
                return tasksAdminCommand.onCommand(sender, cmd, label, shiftArgs(args, 2));
            }
            return tasksCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }

        if (sub.equals("dust")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("admin")) {
                return dustAdminCommand.onCommand(sender, cmd, label, shiftArgs(args, 2));
            }
            return dustCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }

        // ---------------- CORE / ADMIN ----------------

        switch (sub) {
            case "help" -> {
                sendHelp(player);
                return true;
            }

            case "debug" -> {
                if (!player.hasPermission("marbledrop.debug")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission");
                    return true;
                }

                boolean next = !config.getBoolean("debug-mode");
                config.set("debug-mode", next);

                player.sendMessage(ChatColor.GRAY + "Debug mode: " +
                        (next ? ChatColor.GREEN + "enabled." : ChatColor.RED + "disabled."));

                try {
                    config.save(filePath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }

            case "chance" -> {
                if (!player.hasPermission("marbledrop.chance")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission");
                    return true;
                }

                if (args.length == 1) {
                    player.sendMessage(ChatColor.YELLOW +
                            "The current chance to get a marble is: " +
                            ChatColor.BOLD + config.getString("drop-chance") + "%");
                    return true;
                }

                if (args.length > 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md chance <double>");
                    return true;
                }

                try {
                    double chance = Double.parseDouble(args[1]);

                    if (chance < 0.1 || chance > 100.0) {
                        player.sendMessage(ChatColor.RED + "Chance must be between 0.1 and 100.");
                        return true;
                    }

                    config.set("drop-chance", args[1]);
                    config.save(filePath);

                    player.sendMessage(ChatColor.BLUE + "Chance set to " +
                            ChatColor.AQUA + ChatColor.BOLD + args[1] + "%");

                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Invalid number.");
                }
                return true;
            }

            case "pdc" -> {
                if (!player.hasPermission("marbledrop.debug")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission");
                    return true;
                }

                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null || item.getType().isAir()) {
                    player.sendMessage(ChatColor.RED + "Hold an item first.");
                    return true;
                }

                ItemMeta meta = item.getItemMeta();
                if (meta == null) {
                    player.sendMessage(ChatColor.RED + "No ItemMeta.");
                    return true;
                }

                NamespacedKey marbleKey = new NamespacedKey(plugin, "marble");
                NamespacedKey teamKey = new NamespacedKey(plugin, "marble_team");

                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                boolean isMarble = pdc.getOrDefault(marbleKey,
                        PersistentDataType.BYTE, (byte) 0) == 1;

                player.sendMessage(ChatColor.GOLD + "=== Marble Debug ===");
                player.sendMessage("Is Marble: " +
                        (isMarble ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));

                if (isMarble) {
                    player.sendMessage("Name: " +
                            (meta.hasDisplayName() ? meta.getDisplayName() : "none"));
                    player.sendMessage("Team: " +
                            pdc.getOrDefault(teamKey, PersistentDataType.STRING, "null"));
                }
                return true;
            }

            default -> {
                player.sendMessage(ChatColor.RED + "Unknown subcommand. Use /md.");
                return true;
            }
        }
    }

    private void sendHelp(Player player) {
        boolean admin = player.isOp() || player.hasPermission("marbledrop.admin");

        if (admin) {
            player.sendMessage(ChatColor.GREEN + "MarbleDrop Admin Commands\n" +
                    ChatColor.DARK_GREEN + "/md table\n" +
                    ChatColor.DARK_GREEN + "/md dust\n" +
                    ChatColor.DARK_GREEN + "/md tasks\n" +
                    ChatColor.DARK_GREEN + "/md recycler\n" +
                    ChatColor.DARK_GREEN + "/md debug\n" +
                    ChatColor.DARK_GREEN + "/md chance <double>\n" +
                    ChatColor.DARK_GREEN + "/md pdc");
        } else {
            player.sendMessage(ChatColor.GREEN + "MarbleDrop Commands\n" +
                    ChatColor.DARK_GREEN + "/md dust\n" +
                    ChatColor.DARK_GREEN + "/md tasks\n");
        }
    }
}
