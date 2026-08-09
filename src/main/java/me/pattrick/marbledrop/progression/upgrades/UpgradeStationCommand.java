package me.pattrick.marbledrop.progression.upgrades;

import me.pattrick.marbledrop.command.Commands;
import me.pattrick.marbledrop.progression.StationCommands;
import me.pattrick.marbledrop.progression.StationType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class UpgradeStationCommand implements CommandExecutor {

    private final Plugin plugin;
    private final UpgradeStationManager stations;
    private final UpgradeStationAmbient ambient;

    public UpgradeStationCommand(Plugin plugin, UpgradeStationManager stations, UpgradeStationAmbient ambient) {
        this.plugin = plugin;
        this.stations = stations;
        this.ambient = ambient;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = Commands.player(sender);
        if (player == null) return true;
        if (!Commands.requireAdmin(player)) return true;

        if (args.length == 0) {
            Commands.usage(player, "/md upgrades give|remove|count");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "count" -> {
                return StationCommands.count(player, "Upgrade stations", stations.count());
            }

            case "give" -> {
                return StationCommands.give(plugin, player, StationType.UPGRADE_STATION);
            }

            case "remove" -> {
                return StationCommands.remove(player, StationType.UPGRADE_STATION,
                        stations::removeStation,
                        ambient != null ? ambient::removeStation : null);
            }

            default -> {
                Commands.usage(player, "/md upgrades give|remove|count");
                return true;
            }
        }
    }
}
