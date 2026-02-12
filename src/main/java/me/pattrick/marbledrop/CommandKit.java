package me.pattrick.marbledrop;

import me.pattrick.marbledrop.progression.upgrades.UpgradeStationCommand;
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

public class CommandKit implements CommandExecutor {

    private final JavaPlugin plugin;
    private final MdConfig mdConfig;

    private final CommandExecutor infusionTableCommand;
    private final CommandExecutor dustCommand;
    private final CommandExecutor dustAdminCommand;
    private final CommandExecutor marbleRecyclerCommand;
    private final CommandExecutor tasksCommand;
    private final CommandExecutor tasksAdminCommand;
    private final CommandExecutor upgradeStationCommand;
    private final CommandExecutor trackCommand;
    private final CommandExecutor raceCommand;

    private final File filePath;
    private FileConfiguration config;

    public CommandKit(
            JavaPlugin plugin,
            MdConfig mdConfig,
            CommandExecutor infusionTableCommand,
            CommandExecutor dustCommand,
            CommandExecutor dustAdminCommand,
            CommandExecutor marbleRecyclerCommand,
            CommandExecutor tasksCommand,
            CommandExecutor tasksAdminCommand,
            UpgradeStationCommand upgradeStationCommand,
            CommandExecutor trackCommand,
            CommandExecutor raceCommand
    ) {

        this.plugin = plugin;
        this.mdConfig = mdConfig;

        this.infusionTableCommand = infusionTableCommand;
        this.dustCommand = dustCommand;
        this.dustAdminCommand = dustAdminCommand;
        this.marbleRecyclerCommand = marbleRecyclerCommand;
        this.tasksCommand = tasksCommand;
        this.tasksAdminCommand = tasksAdminCommand;
        this.upgradeStationCommand = upgradeStationCommand;
        this.trackCommand = trackCommand;
        this.raceCommand = raceCommand;

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

        if (args.length == 0) {
            if (sender instanceof Player p) sendHelp(p);
            else sender.sendMessage("Use /md help");
            return true;
        }

        String sub = args[0].toLowerCase();

        // ---------------- ROUTING ----------------

        if (sub.equals("track")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Please run this command as a player!");
                return true;
            }
            return trackCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }

        // ✅ NEW: race routing
        if (sub.equals("race") || sub.equals("races")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Please run this command as a player!");
                return true;
            }
            return raceCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }

        if (sub.equals("table") || sub.equals("infusiontable")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Please run this command as a player!");
                return true;
            }
            return infusionTableCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }

        if (sub.equals("recycler") || sub.equals("recycle")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Please run this command as a player!");
                return true;
            }
            return marbleRecyclerCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }

        if (sub.equals("upgrade") || sub.equals("upgrades")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Please run this command as a player!");
                return true;
            }
            return upgradeStationCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }

        if (sub.equals("tasks")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Please run this command as a player!");
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("admin")) {
                return tasksAdminCommand.onCommand(sender, cmd, label, shiftArgs(args, 2));
            }
            return tasksCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }

        if (sub.equals("dust")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Please run this command as a player!");
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("admin")) {
                return dustAdminCommand.onCommand(sender, cmd, label, shiftArgs(args, 2));
            }
            return dustCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }

        // ---------------- CORE / ADMIN ----------------

        switch (sub) {
            case "help" -> {
                if (sender instanceof Player p) sendHelp(p);
                else sender.sendMessage("Commands: /md reload");
                return true;
            }

            case "reload" -> {
                if (!sender.hasPermission("marbledrop.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission");
                    return true;
                }

                plugin.reloadConfig();
                if (mdConfig != null) mdConfig.reload();
                this.config = YamlConfiguration.loadConfiguration(this.filePath);

                sender.sendMessage(ChatColor.GREEN + "MarbleDrop config reloaded.");
                return true;
            }

            case "debug" -> {
                if (!(sender instanceof Player player)) return true;
                if (!player.hasPermission("marbledrop.debug")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission");
                    return true;
                }

                boolean next = !plugin.getConfig().getBoolean("debug.enabled", false);
                plugin.getConfig().set("debug.enabled", next);
                plugin.saveConfig();

                player.sendMessage(ChatColor.GRAY + "Debug: " +
                        (next ? ChatColor.GREEN + "enabled." : ChatColor.RED + "disabled."));

                if (mdConfig != null) mdConfig.reload();
                return true;
            }

            case "pdc" -> {
                if (!(sender instanceof Player player)) return true;
                if (!player.hasPermission("marbledrop.debug")) return true;

                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null || item.getType().isAir()) return true;

                ItemMeta meta = item.getItemMeta();
                if (meta == null) return true;

                PersistentDataContainer pdc = meta.getPersistentDataContainer();

                NamespacedKey legacyMarble = new NamespacedKey(plugin, "marble");
                NamespacedKey legacyTeam = new NamespacedKey(plugin, "marble_team");

                player.sendMessage(ChatColor.GOLD + "=== Marble Debug ===");
                player.sendMessage("legacy marble=" +
                        pdc.getOrDefault(legacyMarble, PersistentDataType.BYTE, (byte) 0));
                player.sendMessage("legacy team=" +
                        pdc.getOrDefault(legacyTeam, PersistentDataType.STRING, "null"));

                return true;
            }

            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /md help.");
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
                    ChatColor.DARK_GREEN + "/md upgrade\n" +
                    ChatColor.DARK_GREEN + "/md track\n" +
                    ChatColor.DARK_GREEN + "/md race\n" +   // ✅ NEW
                    ChatColor.DARK_GREEN + "/md reload\n" +
                    ChatColor.DARK_GREEN + "/md debug\n" +
                    ChatColor.DARK_GREEN + "/md pdc");
        } else {
            player.sendMessage(ChatColor.GREEN + "MarbleDrop Commands\n" +
                    ChatColor.DARK_GREEN + "/md dust\n" +
                    ChatColor.DARK_GREEN + "/md tasks\n" +
                    ChatColor.DARK_GREEN + "/md upgrade\n" +
                    ChatColor.DARK_GREEN + "/md track\n" +
                    ChatColor.DARK_GREEN + "/md race\n");    // ✅ NEW
        }
    }
}
