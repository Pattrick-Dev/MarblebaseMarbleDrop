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
import java.io.IOException;

public class CommandKit implements CommandExecutor {

    private final JavaPlugin plugin;

    private final CommandExecutor infusionTableCommand;// dust infuse
    private final CommandExecutor dustCommand;
    private final CommandExecutor dustAdminCommand;
    private final CommandExecutor marbleRecyclerCommand;
    private final CommandExecutor tasksCommand;
    private final CommandExecutor tasksAdminCommand;

    // ✅ NEW
    private final CommandExecutor upgradeStationCommand;

    private final File filePath;
    private final FileConfiguration config;

    public CommandKit(
            JavaPlugin plugin,
            CommandExecutor infusionTableCommand,
            CommandExecutor dustCommand,
            CommandExecutor dustAdminCommand,
            CommandExecutor marbleRecyclerCommand,
            CommandExecutor tasksCommand,
            CommandExecutor tasksAdminCommand,
            UpgradeStationCommand upgradeStationCommand
    ) {
        this.plugin = plugin;
        this.infusionTableCommand = infusionTableCommand;
        this.dustCommand = dustCommand;
        this.dustAdminCommand = dustAdminCommand;
        this.marbleRecyclerCommand = marbleRecyclerCommand;
        this.tasksCommand = tasksCommand;
        this.tasksAdminCommand = tasksAdminCommand;

        // ✅ store it
        this.upgradeStationCommand = upgradeStationCommand;

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

        // ✅ NEW: upgrades routing
        if (sub.equals("upgrade") || sub.equals("upgrades")) {
            return upgradeStationCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
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

                PersistentDataContainer pdc = meta.getPersistentDataContainer();

                // legacy keys
                NamespacedKey legacyMarble = new NamespacedKey(plugin, "marble");
                NamespacedKey legacyTeam = new NamespacedKey(plugin, "marble_team");

                byte legacyFlag = pdc.getOrDefault(legacyMarble, PersistentDataType.BYTE, (byte) 0);
                String legacyTeamVal = pdc.getOrDefault(legacyTeam, PersistentDataType.STRING, "null");

                // modern keys (MarbleKeys)
                boolean mkId = (me.pattrick.marbledrop.marble.MarbleKeys.MARBLE_ID != null)
                        && pdc.has(me.pattrick.marbledrop.marble.MarbleKeys.MARBLE_ID, PersistentDataType.STRING);
                boolean mkKey = (me.pattrick.marbledrop.marble.MarbleKeys.MARBLE_KEY != null)
                        && pdc.has(me.pattrick.marbledrop.marble.MarbleKeys.MARBLE_KEY, PersistentDataType.STRING);
                boolean mkRarity = (me.pattrick.marbledrop.marble.MarbleKeys.RARITY != null)
                        && pdc.has(me.pattrick.marbledrop.marble.MarbleKeys.RARITY, PersistentDataType.STRING);

                String idVal = (me.pattrick.marbledrop.marble.MarbleKeys.MARBLE_ID == null) ? "nullKey"
                        : pdc.getOrDefault(me.pattrick.marbledrop.marble.MarbleKeys.MARBLE_ID, PersistentDataType.STRING, "null");
                String keyVal = (me.pattrick.marbledrop.marble.MarbleKeys.MARBLE_KEY == null) ? "nullKey"
                        : pdc.getOrDefault(me.pattrick.marbledrop.marble.MarbleKeys.MARBLE_KEY, PersistentDataType.STRING, "null");
                String rarityVal = (me.pattrick.marbledrop.marble.MarbleKeys.RARITY == null) ? "nullKey"
                        : pdc.getOrDefault(me.pattrick.marbledrop.marble.MarbleKeys.RARITY, PersistentDataType.STRING, "null");

                player.sendMessage(ChatColor.GOLD + "=== Marble Debug ===");
                player.sendMessage(ChatColor.YELLOW + "Legacy:");
                player.sendMessage("  marble(byte)=" + legacyFlag);
                player.sendMessage("  marble_team=" + legacyTeamVal);

                player.sendMessage(ChatColor.YELLOW + "Modern:");
                player.sendMessage("  has marble_id=" + mkId + " val=" + idVal);
                player.sendMessage("  has marble_key=" + mkKey + " val=" + keyVal);
                player.sendMessage("  has rarity=" + mkRarity + " val=" + rarityVal);

                player.sendMessage(ChatColor.YELLOW + "MarbleItem.isMarble=" + me.pattrick.marbledrop.marble.MarbleItem.isMarble(item));

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
                    ChatColor.DARK_GREEN + "/md upgrade\n" +   // ✅ NEW
                    ChatColor.DARK_GREEN + "/md debug\n" +
                    ChatColor.DARK_GREEN + "/md pdc");
        } else {
            player.sendMessage(ChatColor.GREEN + "MarbleDrop Commands\n" +
                    ChatColor.DARK_GREEN + "/md dust\n" +
                    ChatColor.DARK_GREEN + "/md tasks\n" +
                    ChatColor.DARK_GREEN + "/md upgrade\n"); // ✅ NEW (players will use it to open GUI)
        }
    }
}
