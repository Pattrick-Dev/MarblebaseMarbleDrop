package me.pattrick.marbledrop.progression.upgrades;

import me.pattrick.marbledrop.progression.StationItems;
import me.pattrick.marbledrop.progression.StationType;

public final class UpgradeStationCommand implements org.bukkit.command.CommandExecutor {

    private final org.bukkit.plugin.Plugin plugin;
    private final UpgradeStationManager stations;

    public UpgradeStationCommand(org.bukkit.plugin.Plugin plugin, UpgradeStationManager stations) {
        this.plugin = plugin;
        this.stations = stations;
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "Players only.");
            return true;
        }

        if (!(player.isOp() || player.hasPermission("marbledrop.admin"))) {
            player.sendMessage(org.bukkit.ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(org.bukkit.ChatColor.YELLOW + "Use: /md upgrades give|remove|count");
            return true;
        }

        if (args[0].equalsIgnoreCase("count")) {
            player.sendMessage(org.bukkit.ChatColor.GRAY + "Upgrade stations: " + org.bukkit.ChatColor.WHITE + stations.getStations().size());
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            me.pattrick.marbledrop.SilentGive.give(plugin, player, StationItems.create(plugin, StationType.UPGRADE_STATION));
            player.sendMessage(org.bukkit.ChatColor.GREEN + "Gave you an Upgrade Station -- place it to create a station.");
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            org.bukkit.block.Block target = player.getTargetBlockExact(6);
            if (target == null) {
                player.sendMessage(org.bukkit.ChatColor.RED + "Look at the station (within 6 blocks).");
                return true;
            }

            boolean ok = stations.removeStation(target.getLocation());
            player.sendMessage(ok
                    ? (org.bukkit.ChatColor.GREEN + "Removed Upgrade Station registration.")
                    : (org.bukkit.ChatColor.YELLOW + "This is not a registered Upgrade Station."));
            return true;
        }

        player.sendMessage(org.bukkit.ChatColor.YELLOW + "Use: /md upgrades give|remove|count");
        return true;
    }
}
