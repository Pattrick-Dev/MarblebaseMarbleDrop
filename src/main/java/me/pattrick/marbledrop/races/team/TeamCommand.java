package me.pattrick.marbledrop.races.team;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TeamCommand implements CommandExecutor {

    private final CustomTeamManager mgr;

    public TeamCommand(CustomTeamManager mgr) {
        this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Run this as a player.");
            return true;
        }
        TeamMenu.open(p, mgr);
        return true;
    }
}
