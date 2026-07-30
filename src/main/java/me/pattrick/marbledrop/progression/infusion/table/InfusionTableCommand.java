package me.pattrick.marbledrop.progression.infusion.table;

import me.pattrick.marbledrop.SilentGive;
import me.pattrick.marbledrop.progression.StationItems;
import me.pattrick.marbledrop.progression.StationType;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class InfusionTableCommand implements CommandExecutor {

    private final Plugin plugin;
    private final InfusionTableManager tables;
    private final InfusionTableAmbient ambient;

    public InfusionTableCommand(Plugin plugin, InfusionTableManager tables, InfusionTableAmbient ambient) {
        this.plugin = plugin;
        this.tables = tables;
        this.ambient = ambient;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        if (!player.hasPermission("marbledrop.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Use: /md table give|remove|count|private");
            return true;
        }

        if (args[0].equalsIgnoreCase("count")) {
            player.sendMessage(ChatColor.GRAY + "Infusion tables: " + ChatColor.WHITE + tables.getTables().size());
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            SilentGive.give(plugin, player, StationItems.create(plugin, StationType.INFUSION_TABLE));
            player.sendMessage(ChatColor.GREEN + "Gave you an Infusion Cauldron -- place it to create a station.");
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            Block target = player.getTargetBlockExact(6);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Look at the table (within 6 blocks).");
                return true;
            }

            boolean ok = tables.removeTable(target.getLocation());
            if (ok && ambient != null) {
                ambient.removeTable(target.getLocation());
            }
            player.sendMessage(ok
                    ? (ChatColor.GREEN + "Removed Infusion Cauldron registration.")
                    : (ChatColor.YELLOW + "This location is not a registered Infusion Cauldron."));
            return true;
        }

        if (args[0].equalsIgnoreCase("private")) {
            Block target = player.getTargetBlockExact(6);
            if (target == null || !tables.isTable(target.getLocation())) {
                player.sendMessage(ChatColor.RED + "Look at a registered Infusion Cauldron (within 6 blocks).");
                return true;
            }
            boolean nowPrivate = !tables.isPrivate(target.getLocation());
            tables.setPrivate(target.getLocation(), nowPrivate);
            player.sendMessage(nowPrivate
                    ? (ChatColor.GREEN + "This table is now private: each user gets their own lock-free, personal animation.")
                    : (ChatColor.YELLOW + "This table is back to normal: shared animation, one user at a time."));
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Use: /md table give|remove|count|private");
        return true;
    }
}
