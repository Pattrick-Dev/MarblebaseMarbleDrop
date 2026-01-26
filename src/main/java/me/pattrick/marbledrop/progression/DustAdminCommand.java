package me.pattrick.marbledrop.progression;

import me.pattrick.marbledrop.progression.DustManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DustAdminCommand implements CommandExecutor {

    private final DustManager dust;

    public DustAdminCommand(DustManager dust) {
        this.dust = dust;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("marbledrop.admin.dust")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /dustadmin <give|remove|set> <player> <amount>");
            return true;
        }

        String action = args[0].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found or not online.");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Amount must be a number.");
            return true;
        }

        if (amount < 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be non-negative.");
            return true;
        }

        switch (action) {
            case "give" -> {
                dust.addDust(target, amount);
                sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " dust to " + target.getName() + ".");
                target.sendMessage(ChatColor.YELLOW + "You received " + amount + " Marble Dust.");
            }
            case "remove" -> {
                dust.takeDust(target, amount);
                sender.sendMessage(ChatColor.GREEN + "Removed " + amount + " dust from " + target.getName() + ".");
                target.sendMessage(ChatColor.RED + "" + amount + " Marble Dust was removed.");
            }
            case "set" -> {
                dust.setDust(target, amount);
                sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s dust to " + amount + ".");
                target.sendMessage(ChatColor.YELLOW + "Your Marble Dust was set to " + amount + ".");
            }
            default -> sender.sendMessage(ChatColor.RED + "Invalid action. Use give, remove, or set.");
        }

        return true;
    }
}
