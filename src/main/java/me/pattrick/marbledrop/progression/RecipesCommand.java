package me.pattrick.marbledrop.progression;

import me.pattrick.marbledrop.command.Commands;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Standalone /recipes command - opens RecipesMenu, the always-available preview of every station recipe. */
public final class RecipesCommand implements CommandExecutor {

    private final Plugin plugin;

    public RecipesCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = Commands.player(sender);
        if (player == null) return true;

        new RecipesMenu(plugin).open(player, 0);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
        return true;
    }
}
