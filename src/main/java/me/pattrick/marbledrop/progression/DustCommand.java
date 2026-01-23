package me.pattrick.marbledrop.progression;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DustCommand implements CommandExecutor {

    private final DustManager dustManager;

    public DustCommand(DustManager dustManager) {
        this.dustManager = dustManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        int dust = dustManager.getDust(player);
        player.sendMessage(ChatColor.GOLD + "Marble Dust: " + ChatColor.YELLOW + dust);
        return true;
    }
}
