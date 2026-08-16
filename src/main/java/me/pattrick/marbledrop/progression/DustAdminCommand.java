package me.pattrick.marbledrop.progression;

import me.pattrick.marbledrop.command.Commands;
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
        if (!Commands.requireAdmin(sender)) return true;

        if (args.length != 3) {
            Commands.usage(sender, "/md dust admin <give|remove|set> <player> <amount>");
            return true;
        }

        String action = args[0].toLowerCase();

        Integer amount = Commands.intArg(sender, args[2], "Amount");
        if (amount == null) return true;

        if (amount < 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be 0 or more.");
            return true;
        }

        Player target = Commands.online(sender, args[1]);
        if (target == null) return true;

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

            // "take" is an alias for "remove" - DustManager's own method is takeDust().
            case "remove", "take" -> {
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
                // We don't assume a setDust() method exists.
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
                    dust.takeDust(target, toRemove); // should succeed because we're removing exactly the difference
                }

                int after = dust.getDust(target);
                sender.sendMessage(ChatColor.GREEN + "Set " + ChatColor.AQUA + target.getName()
                        + ChatColor.GREEN + " dust to " + ChatColor.YELLOW + after
                        + ChatColor.GRAY + " (" + before + " -> " + after + ")");
                target.sendMessage(ChatColor.GRAY + "Your Marble Dust is now " + ChatColor.YELLOW + after + ChatColor.GRAY + ".");
                return true;
            }

            default -> {
                Commands.usage(sender, "/md dust admin <give|remove|set> <player> <amount>");
                return true;
            }
        }
    }
}
