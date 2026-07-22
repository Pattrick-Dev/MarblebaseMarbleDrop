package me.pattrick.marbledrop.tutorial;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Usage:
 *   /md tutorial status [player]
 *   /md tutorial reset <player>
 *   /md tutorial skip <player>
 */
public final class TutorialCommand implements CommandExecutor {

    private final TutorialManager tutorialManager;

    public TutorialCommand(TutorialManager tutorialManager) {
        this.tutorialManager = tutorialManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /md tutorial <status|reset|skip> [player]");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("status")) {
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

        if (!sender.hasPermission("marbledrop.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (sub.equals("reset")) {
            Player target = resolveTarget(sender, args, 1);
            if (target == null) return true;
            tutorialManager.reset(target);
            sender.sendMessage(ChatColor.GREEN + "Reset the tutorial for " + target.getName() + ".");
            return true;
        }

        if (sub.equals("skip")) {
            Player target = resolveTarget(sender, args, 1);
            if (target == null) return true;
            tutorialManager.skip(target);
            sender.sendMessage(ChatColor.GREEN + "Skipped the tutorial for " + target.getName() + ".");
            return true;
        }

        if (sub.equals("setlocation")) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(ChatColor.RED + "Run this in-game, standing where you want the checkpoint.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /md tutorial setlocation <step>");
                sender.sendMessage(ChatColor.GRAY + "Steps: " + stepNameList());
                return true;
            }
            TutorialStep step = parseStep(args[1]);
            if (step == null) {
                sender.sendMessage(ChatColor.RED + "Unknown step. Valid steps: " + stepNameList());
                return true;
            }
            tutorialManager.locations().set(step, admin.getLocation());
            sender.sendMessage(ChatColor.GREEN + "Checkpoint for " + step.title() + " set to your current location.");
            return true;
        }

        if (sub.equals("clearlocation")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /md tutorial clearlocation <step>");
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

        if (sub.equals("setrace")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /md tutorial setrace <trackId>");
                return true;
            }
            tutorialManager.locations().setRaceTrackId(args[1].toLowerCase());
            sender.sendMessage(ChatColor.GREEN + "Tutorial race track set to '" + args[1].toLowerCase() + "'.");
            return true;
        }

        if (sub.equals("setpost")) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(ChatColor.RED + "Run this in-game, standing where you want players sent after finishing.");
                return true;
            }
            tutorialManager.locations().setPostTutorialLocation(admin.getLocation());
            sender.sendMessage(ChatColor.GREEN + "Post-tutorial area set to your current location.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /md tutorial <status|reset|skip|setlocation|clearlocation|setrace|setpost> [player|step|trackId]");
        return true;
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
