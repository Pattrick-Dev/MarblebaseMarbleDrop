package me.pattrick.marbledrop.tutorial;

import me.pattrick.marbledrop.command.Commands;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Usage:
 *   /md tutorial start
 *   /md tutorial status [player]
 *   /md tutorial reset <player>
 *   /md tutorial skip <player>
 *   /md tutorial setlocation <step>
 *   /md tutorial clearlocation <step>
 *   /md tutorial setrace <trackId>
 *   /md tutorial setpost
 *   /md tutorial setcraftframes
 * <p>
 * start/status are open to everyone; every other subcommand requires
 * marbledrop.admin, checked individually in each case below rather than
 * once up front, since start/status need to stay reachable without it.
 */
public final class TutorialCommand implements CommandExecutor {

    private final TutorialManager tutorialManager;
    private final TutorialCraftFrameManager craftFrames;

    public TutorialCommand(TutorialManager tutorialManager, TutorialCraftFrameManager craftFrames) {
        this.tutorialManager = tutorialManager;
        this.craftFrames = craftFrames;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            Commands.usage(sender, "/md tutorial <start|status|reset|skip|setlocation|clearlocation|setrace|setpost|setcraftframes> [player|step|trackId]");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            case "start" -> {
                Player player = Commands.player(sender);
                if (player == null) return true;

                if (tutorialManager.hasCompleted(player)) {
                    player.sendMessage(ChatColor.YELLOW + "You've already completed the tutorial.");
                    return true;
                }
                if (tutorialManager.isActive(player)) {
                    tutorialManager.resume(player); // already started (e.g. from a previous session) -- just re-show the current step
                    return true;
                }
                tutorialManager.start(player);
                return true;
            }

            case "status" -> {
                Player target = resolveTarget(sender, args, 1);
                if (target == null) return true;

                boolean done = tutorialManager.hasCompleted(target);
                boolean active = tutorialManager.isActive(target);
                sender.sendMessage(ChatColor.GOLD + target.getName() + "'s tutorial: " +
                        (done ? ChatColor.GREEN + "complete"
                                : active ? ChatColor.YELLOW + "in progress (" + tutorialManager.getStep(target).title() + ")"
                                : ChatColor.GRAY + "not started"));
                return true;
            }

            case "reset" -> {
                if (!Commands.requireAdmin(sender)) return true;
                Player target = resolveTarget(sender, args, 1);
                if (target == null) return true;
                tutorialManager.reset(target);
                sender.sendMessage(ChatColor.GREEN + "Reset the tutorial for " + target.getName() +
                        ". They'll need to run /md tutorial start again.");
                return true;
            }

            case "skip" -> {
                if (!Commands.requireAdmin(sender)) return true;
                Player target = resolveTarget(sender, args, 1);
                if (target == null) return true;
                tutorialManager.skip(target);
                sender.sendMessage(ChatColor.GREEN + "Skipped the tutorial for " + target.getName() + ".");
                return true;
            }

            case "setlocation" -> {
                if (!Commands.requireAdmin(sender)) return true;
                Player admin = Commands.player(sender);
                if (admin == null) {
                    sender.sendMessage(ChatColor.GRAY + "Run this in-game, standing where you want the checkpoint.");
                    return true;
                }

                if (args.length < 2) {
                    Commands.usage(admin, "/md tutorial setlocation <step>");
                    admin.sendMessage(ChatColor.GRAY + "Steps: " + stepNameList());
                    return true;
                }
                TutorialStep step = parseStep(args[1]);
                if (step == null) {
                    admin.sendMessage(ChatColor.RED + "Unknown step. Valid steps: " + stepNameList());
                    return true;
                }
                tutorialManager.locations().set(step, admin.getLocation());
                admin.sendMessage(ChatColor.GREEN + "Checkpoint for " + step.title() + " set to your current location.");
                return true;
            }

            case "clearlocation" -> {
                if (!Commands.requireAdmin(sender)) return true;
                if (args.length < 2) {
                    Commands.usage(sender, "/md tutorial clearlocation <step>");
                    return true;
                }
                TutorialStep step = parseStep(args[1]);
                if (step == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown step. Valid steps: " + stepNameList());
                    return true;
                }
                tutorialManager.locations().clear(step);
                sender.sendMessage(ChatColor.GREEN + "Cleared checkpoint for " + step.title() + ".");
                return true;
            }

            case "setrace" -> {
                if (!Commands.requireAdmin(sender)) return true;
                if (args.length < 2) {
                    Commands.usage(sender, "/md tutorial setrace <trackId>");
                    return true;
                }
                tutorialManager.locations().setRaceTrackId(args[1].toLowerCase());
                sender.sendMessage(ChatColor.GREEN + "Tutorial race track set to '" + args[1].toLowerCase() + "'.");
                return true;
            }

            case "setpost" -> {
                if (!Commands.requireAdmin(sender)) return true;
                Player admin = Commands.player(sender);
                if (admin == null) {
                    sender.sendMessage(ChatColor.GRAY + "Run this in-game, standing where you want players sent after finishing.");
                    return true;
                }

                tutorialManager.locations().setPostTutorialLocation(admin.getLocation());
                admin.sendMessage(ChatColor.GREEN + "Post-tutorial area set to your current location.");
                return true;
            }

            case "setcraftframes" -> {
                if (!Commands.requireAdmin(sender)) return true;
                Player admin = Commands.player(sender);
                if (admin == null) {
                    sender.sendMessage(ChatColor.GRAY + "Run this in-game with a WorldEdit/FAWE selection around the 9 item frames.");
                    return true;
                }

                craftFrames.setupFromSelection(admin);
                return true;
            }

            default -> {
                Commands.usage(sender, "/md tutorial <start|status|reset|skip|setlocation|clearlocation|setrace|setpost|setcraftframes> [player|step|trackId]");
                return true;
            }
        }
    }

    private TutorialStep parseStep(String raw) {
        try {
            return TutorialStep.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String stepNameList() {
        StringBuilder sb = new StringBuilder();
        for (TutorialStep step : TutorialStep.values()) {
            if (step == TutorialStep.COMPLETE) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(step.name());
        }
        return sb.toString();
    }

    private Player resolveTarget(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player p = Bukkit.getPlayerExact(args[index]);
            if (p == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or not online.");
            }
            return p;
        }
        if (sender instanceof Player p) return p;
        sender.sendMessage(ChatColor.RED + "Specify a player name when running from console.");
        return null;
    }
}
