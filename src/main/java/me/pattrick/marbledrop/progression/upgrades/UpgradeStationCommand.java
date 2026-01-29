package me.pattrick.marbledrop.progression.upgrades;

public final class UpgradeStationCommand implements org.bukkit.command.CommandExecutor {

    private final UpgradeStationManager stations;

    public UpgradeStationCommand(UpgradeStationManager stations) {
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
            player.sendMessage(org.bukkit.ChatColor.YELLOW + "Use: /md upgrades add|remove|count");
            return true;
        }

        if (args[0].equalsIgnoreCase("count")) {
            player.sendMessage(org.bukkit.ChatColor.GRAY + "Upgrade stations: " + org.bukkit.ChatColor.WHITE + stations.getStations().size());
            return true;
        }

        org.bukkit.block.Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType() != org.bukkit.Material.SMITHING_TABLE) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Look at a SMITHING_TABLE (within 6 blocks).");
            return true;
        }

        if (args[0].equalsIgnoreCase("add")) {
            boolean ok = stations.addStation(target.getLocation());
            player.sendMessage(ok
                    ? (org.bukkit.ChatColor.GREEN + "Marked this as an Upgrade Station.")
                    : (org.bukkit.ChatColor.YELLOW + "This is already an Upgrade Station."));
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            boolean ok = stations.removeStation(target.getLocation());
            player.sendMessage(ok
                    ? (org.bukkit.ChatColor.GREEN + "Removed Upgrade Station mark.")
                    : (org.bukkit.ChatColor.YELLOW + "This is not marked as an Upgrade Station."));
            return true;
        }

        player.sendMessage(org.bukkit.ChatColor.YELLOW + "Use: /md upgrades add|remove|count");
        return true;
    }
}
