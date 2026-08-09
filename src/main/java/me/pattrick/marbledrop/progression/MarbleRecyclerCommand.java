package me.pattrick.marbledrop.progression;

import me.pattrick.marbledrop.command.Commands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class MarbleRecyclerCommand implements CommandExecutor {

    private final Plugin plugin;
    private final MarbleRecyclerManager recyclers;
    private final RecyclerAmbient ambient;

    public MarbleRecyclerCommand(Plugin plugin, MarbleRecyclerManager recyclers, RecyclerAmbient ambient) {
        this.plugin = plugin;
        this.recyclers = recyclers;
        this.ambient = ambient;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = Commands.player(sender);
        if (player == null) return true;
        if (!Commands.requireAdmin(player)) return true;

        if (args.length == 0) {
            Commands.usage(player, "/md recycler give|remove|count");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "count" -> {
                return StationCommands.count(player, "Recyclers", recyclers.count());
            }

            case "give" -> {
                return StationCommands.give(plugin, player, StationType.RECYCLER);
            }

            case "remove" -> {
                return StationCommands.remove(player, StationType.RECYCLER,
                        recyclers::removeRecycler,
                        ambient != null ? ambient::removeRecycler : null);
            }

            default -> {
                Commands.usage(player, "/md recycler give|remove|count");
                return true;
            }
        }
    }
}
