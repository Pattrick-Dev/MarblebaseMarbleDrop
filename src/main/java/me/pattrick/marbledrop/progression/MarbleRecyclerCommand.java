package me.pattrick.marbledrop.progression;

import me.pattrick.marbledrop.SilentGive;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
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

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        if (!player.hasPermission("marbledrop.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Use: /md recycler give|remove|count");
            return true;
        }

        if (args[0].equalsIgnoreCase("count")) {
            player.sendMessage(ChatColor.GRAY + "Recyclers: " + ChatColor.WHITE + recyclers.count());
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            SilentGive.give(plugin, player, StationItems.create(plugin, StationType.RECYCLER));
            player.sendMessage(ChatColor.GREEN + "Gave you a Marble Recycler -- place it to create a station.");
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            Block target = player.getTargetBlockExact(6);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Look at the recycler (within 6 blocks).");
                return true;
            }

            boolean ok = recyclers.removeRecycler(target.getLocation());
            if (ok && ambient != null) {
                ambient.removeRecycler(target.getLocation());
            }

            player.sendMessage(ok
                    ? (ChatColor.GREEN + "Removed Marble Recycler registration.")
                    : (ChatColor.YELLOW + "This is not a registered Marble Recycler."));
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Use: /md recycler give|remove|count");
        return true;
    }
}
