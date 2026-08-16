package me.pattrick.marbledrop.progression.infusion.table;

import me.pattrick.marbledrop.command.Commands;
import me.pattrick.marbledrop.progression.StationCommands;
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
        Player player = Commands.player(sender);
        if (player == null) return true;
        if (!Commands.requireAdmin(player)) return true;

        if (args.length == 0) {
            Commands.usage(player, "/md table give|remove|count|private");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "count" -> {
                return StationCommands.count(player, "Infusion tables", tables.count());
            }

            case "give" -> {
                return StationCommands.give(plugin, player, StationType.INFUSION_TABLE);
            }

            case "remove" -> {
                return StationCommands.remove(player, StationType.INFUSION_TABLE,
                        tables::removeTable,
                        ambient != null ? ambient::removeTable : null);
            }

            // Not part of StationCommands - unique to infusion tables, no shared mechanism needed for one caller.
            case "private" -> {
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

            default -> {
                Commands.usage(player, "/md table give|remove|count|private");
                return true;
            }
        }
    }
}
