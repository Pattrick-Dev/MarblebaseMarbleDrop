package me.pattrick.marbledrop.progression;

import me.pattrick.marbledrop.progression.DustManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DustAdminCommand implements CommandExecutor {

    private final DustManager dust;

    public DustAdminCommand(DustManager dust) {
        this.dust = dust;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("marbledrop.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(ChatColor.YELLOW + "Use: /dustadmin <give|remove|set> <player> <amount>");
            return true;
        }

        String action = args[0].toLowerCase();
        String targetName = args[1];

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Amount must be a whole number.");
            return true;
        }

        if (amount < 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be 0 or more.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found (must be online): " + ChatColor.YELLOW + targetName);
            return true;
        }

        int before = dust.getDust(target);

        switch (action) {
            case "give" -> {
                if (amount == 0) {
                    sender.sendMessage(ChatColor.GRAY + "No change (amount was 0).");
                    return true;
                }

                dust.addDust(target, amount);
                int after = dust.getDust(target);

                sender.sendMessage(ChatColor.GREEN + "Gave " + ChatColor.YELLOW + amount
                        + ChatColor.GREEN + " dust to " + ChatColor.AQUA + target.getName()
                        + ChatColor.GRAY + " (" + before + " -> " + after + ")");
                target.sendMessage(ChatColor.GRAY + "You received " + ChatColor.YELLOW + amount + ChatColor.GRAY + " Marble Dust.");
                return true;
            }

            case "remove" -> {
                if (amount == 0) {
                    sender.sendMessage(ChatColor.GRAY + "No change (amount was 0).");
                    return true;
                }

                int toRemove = Math.min(amount, before);
                if (toRemove <= 0) {
                    sender.sendMessage(ChatColor.GRAY + target.getName() + " already has 0 dust.");
                    return true;
                }

                // We know takeDust exists and returns boolean; removing <= before should always succeed.
                dust.takeDust(target, toRemove);
                int after = dust.getDust(target);

                sender.sendMessage(ChatColor.GREEN + "Removed " + ChatColor.YELLOW + toRemove
                        + ChatColor.GREEN + " dust from " + ChatColor.AQUA + target.getName()
                        + ChatColor.GRAY + " (" + before + " -> " + after + ")");
                target.sendMessage(ChatColor.GRAY + "An admin removed " + ChatColor.YELLOW + toRemove + ChatColor.GRAY + " Marble Dust.");
                return true;
            }

            case "set" -> {
                // We don’t assume a setDust() method exists.
                // Implement set by adding/removing the difference.
                int desired = amount;

                if (desired == before) {
                    sender.sendMessage(ChatColor.GRAY + "No change (already " + before + ").");
                    return true;
                }

                if (desired > before) {
                    dust.addDust(target, desired - before);
                } else {
                    int toRemove = before - desired;
                    dust.takeDust(target, toRemove); // should succeed because we’re removing exactly the difference
                }

                int after = dust.getDust(target);
                sender.sendMessage(ChatColor.GREEN + "Set " + ChatColor.AQUA + target.getName()
                        + ChatColor.GREEN + " dust to " + ChatColor.YELLOW + after
                        + ChatColor.GRAY + " (" + before + " -> " + after + ")");
                target.sendMessage(ChatColor.GRAY + "Your Marble Dust is now " + ChatColor.YELLOW + after + ChatColor.GRAY + ".");
                return true;
            }

            default -> {
                sender.sendMessage(ChatColor.YELLOW + "Use: /dustadmin <give|remove|set> <player> <amount>");
                return true;
            }
        }
    }
}
