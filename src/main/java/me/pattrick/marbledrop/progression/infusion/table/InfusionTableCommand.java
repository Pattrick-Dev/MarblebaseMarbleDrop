package me.pattrick.marbledrop.progression.infusion.table;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class InfusionTableCommand implements CommandExecutor {

    private final InfusionTableManager tables;
    private final InfusionTableAmbient ambient;

    public InfusionTableCommand(InfusionTableManager tables, InfusionTableAmbient ambient) {
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
            player.sendMessage(ChatColor.YELLOW + "Use: /md table add|remove|count");
            return true;
        }

        if (args[0].equalsIgnoreCase("count")) {
            player.sendMessage(ChatColor.GRAY + "Infusion tables: " + ChatColor.WHITE + tables.getTables().size());
            return true;
        }

        Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType() != Material.CAULDRON) {
            player.sendMessage(ChatColor.RED + "Look at a CAULDRON (within 6 blocks).");
            return true;
        }

        if (args[0].equalsIgnoreCase("add")) {
            boolean ok = tables.addTable(target.getLocation());
            player.sendMessage(ok
                    ? (ChatColor.GREEN + "Marked this cauldron as an Infusion Table.")
                    : (ChatColor.YELLOW + "This cauldron is already an Infusion Table."));
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            boolean ok = tables.removeTable(target.getLocation());
            if (ok) {
                // Remove hologram marker immediately
                if (ambient != null) {
                    ambient.removeTable(target.getLocation());
                }
                player.sendMessage(ChatColor.GREEN + "Removed Infusion Table mark from this cauldron.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "This cauldron is not marked as an Infusion Table.");
            }
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Use: /md table add|remove|count");
        return true;
    }
}
