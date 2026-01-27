package me.pattrick.marbledrop.progression;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MarbleRecyclerCommand implements CommandExecutor {

    private final MarbleRecyclerManager recyclers;
    private final RecyclerAmbient ambient;

    public MarbleRecyclerCommand(MarbleRecyclerManager recyclers, RecyclerAmbient ambient) {
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
            player.sendMessage(ChatColor.YELLOW + "Use: /marblerecycler add|remove|count");
            return true;
        }

        if (args[0].equalsIgnoreCase("count")) {
            player.sendMessage(ChatColor.GRAY + "Recyclers: " + ChatColor.WHITE + recyclers.count());
            return true;
        }

        Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType() != Material.GRINDSTONE) {
            player.sendMessage(ChatColor.RED + "Look at a GRINDSTONE (within 6 blocks).");
            return true;
        }

        if (args[0].equalsIgnoreCase("add")) {
            boolean ok = recyclers.addRecycler(target.getLocation());
            player.sendMessage(ok
                    ? (ChatColor.GREEN + "Marked this grindstone as a Marble Recycler.")
                    : (ChatColor.YELLOW + "This grindstone is already a Marble Recycler."));
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            boolean ok = recyclers.removeRecycler(target.getLocation());

            // Remove hologram immediately (no ghost stands)
            if (ok && ambient != null) {
                ambient.removeRecycler(target.getLocation());
            }

            player.sendMessage(ok
                    ? (ChatColor.GREEN + "Removed Marble Recycler mark from this grindstone.")
                    : (ChatColor.YELLOW + "This grindstone is not marked as a Marble Recycler."));
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Use: /marblerecycler add|remove|count");
        return true;
    }
}
