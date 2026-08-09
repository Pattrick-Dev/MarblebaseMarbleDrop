package me.pattrick.marbledrop.races.team;

import me.pattrick.marbledrop.command.Commands;
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
        Player player = Commands.player(sender);
        if (player == null) return true;

        TeamMenu.open(player, mgr);
        return true;
    }
}
