package me.pattrick.marbledrop.races;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RaceCommand implements CommandExecutor {

    private final RaceManager races;
    private final TrackManager tracks;
    private final RaceWatchManager watch;

    public RaceCommand(RaceManager races, TrackManager tracks, RaceWatchManager watch) {
        this.races = races;
        this.tracks = tracks;
        this.watch = watch;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Please run this command as a player!");
            return true;
        }

        // ✅ default: open GUI
        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu")) {
            RaceGui.open(player, tracks, races);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            case "open" -> {
                if (!player.hasPermission("marbledrop.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md race open <trackId>");
                    return true;
                }
                races.open(player, args[1].toLowerCase());
                return true;
            }

            case "close" -> {
                if (!player.hasPermission("marbledrop.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md race close <trackId>");
                    return true;
                }
                races.close(player, args[1].toLowerCase());
                return true;
            }

            case "start" -> {
                if (!player.hasPermission("marbledrop.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md race start <trackId>");
                    return true;
                }
                races.start(player, args[1].toLowerCase());
                return true;
            }

            case "clear" -> {
                if (!player.hasPermission("marbledrop.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md race clear <trackId>");
                    return true;
                }
                races.clear(args[1].toLowerCase());
                player.sendMessage(ChatColor.YELLOW + "Cleared race entries for '" + args[1].toLowerCase() + "'.");
                return true;
            }

            case "watch" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md race watch <trackId>");
                    return true;
                }
                watch.start(player, args[1].toLowerCase());
                return true;
            }

            case "unwatch", "leavewatch" -> {
                watch.stop(player, true);
                return true;
            }

            default -> {
                usage(player);
                return true;
            }
        }
    }

    private void usage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Race commands:");
        player.sendMessage(ChatColor.GRAY + "/md race" + ChatColor.DARK_GRAY + " (open menu)");
        player.sendMessage(ChatColor.GRAY + "/md race watch <trackId>" + ChatColor.DARK_GRAY + " (watch w/ inventory saved)");
        player.sendMessage(ChatColor.GRAY + "/md race unwatch" + ChatColor.DARK_GRAY + " (restore inventory)");
        player.sendMessage(ChatColor.GRAY + "/md race open <trackId> " + ChatColor.DARK_GRAY + "(admin)");
        player.sendMessage(ChatColor.GRAY + "/md race close <trackId> " + ChatColor.DARK_GRAY + "(admin)");
        player.sendMessage(ChatColor.GRAY + "/md race start <trackId> " + ChatColor.DARK_GRAY + "(admin)");
        player.sendMessage(ChatColor.GRAY + "/md race clear <trackId> " + ChatColor.DARK_GRAY + "(admin)");
    }
}
