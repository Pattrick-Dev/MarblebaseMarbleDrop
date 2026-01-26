package me.pattrick.marbledrop.progression.infusion;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class InfusionCommand implements CommandExecutor {

    private final InfusionService infusionService;

    public InfusionCommand(InfusionService infusionService) {
        this.infusionService = infusionService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        // /dust infuse <amount>
        if (args.length >= 1 && args[0].equalsIgnoreCase("infuse")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Use: /dust infuse <amount>");
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[1]);
            } catch (Exception ex) {
                player.sendMessage(ChatColor.RED + "That isn't a valid number.");
                return true;
            }

            infusionService.infuse(player, amount);
            return true;
        }

        return false; // let your existing /dust behavior handle balance display
    }
}
