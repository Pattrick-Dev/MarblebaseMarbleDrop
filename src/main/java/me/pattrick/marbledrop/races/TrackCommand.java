package me.pattrick.marbledrop.races;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TrackCommand implements CommandExecutor {

    private final TrackManager tracks;
    private final MarbleRaceEngine raceEngine;
    private final TrackVisualizer visualizer;


    public TrackCommand(
            TrackManager tracks,
            TrackVisualizer visualizer,
            MarbleRaceEngine raceEngine
    ) {
        this.tracks = tracks;
        this.visualizer = visualizer;
        this.raceEngine = raceEngine;
    }




    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Please run this command as a player!");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track create <id>");
                    return true;
                }

                String id = args[1].toLowerCase();

                if (!tracks.createTrack(id, player.getWorld())) {
                    player.sendMessage(ChatColor.RED + "Track already exists.");
                    return true;
                }

                player.sendMessage(ChatColor.GREEN + "Created track '" + id + "'.");
            }

            case "addpoint" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track addpoint <id>");
                    return true;
                }

                String id = args[1].toLowerCase();

                if (!tracks.addPoint(id, player.getLocation())) {
                    player.sendMessage(ChatColor.RED + "Track not found.");
                    return true;
                }

                int count = tracks.getTrack(id).size();
                player.sendMessage(ChatColor.GREEN + "Added point #" + count + " to '" + id + "'.");
            }

            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track info <id>");
                    return true;
                }

                String id = args[1].toLowerCase();
                MarbleTrack track = tracks.getTrack(id);

                if (track == null) {
                    player.sendMessage(ChatColor.RED + "Track not found.");
                    return true;
                }

                player.sendMessage(ChatColor.YELLOW + "Track: " + id);
                player.sendMessage(ChatColor.GRAY + "World: " + track.getWorld().getName());
                player.sendMessage(ChatColor.GRAY + "Points: " + track.size());
            }

            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track delete <id>");
                    return true;
                }

                String id = args[1].toLowerCase();

                if (!tracks.removeTrack(id)) {
                    player.sendMessage(ChatColor.RED + "Track not found.");
                    return true;
                }

                player.sendMessage(ChatColor.GREEN + "Deleted track '" + id + "'.");
            }
            case "show" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /md track show <id>");
                    return true;
                }
                visualizer.show(player, args[1].toLowerCase());
            }

            case "hide" -> {
                visualizer.hide(player);
            }
            case "run" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /md track run <id>");
                    return true;
                }

                String id = args[1].toLowerCase();
                MarbleTrack track = tracks.getTrack(id);

                if (track == null || track.size() < 2) {
                    player.sendMessage("§cTrack not found or not enough points.");
                    return true;
                }

                MarbleRunner runner =
                        new MarbleRunner(track, track.getPoint(0));
                raceEngine.addRunner(runner);


                player.sendMessage("§aMarble started on track §e" + id);
            }
            case "gui" -> {
                TrackGui.openList(player, tracks);
            }




            default -> sendUsage(player);
        }

        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Track commands:");
        player.sendMessage(ChatColor.GRAY + "/md track create <id>");
        player.sendMessage(ChatColor.GRAY + "/md track addpoint <id>");
        player.sendMessage(ChatColor.GRAY + "/md track info <id>");
        player.sendMessage(ChatColor.GRAY + "/md track delete <id>");
    }
}
