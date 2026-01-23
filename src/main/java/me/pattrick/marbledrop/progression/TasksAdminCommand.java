package me.pattrick.marbledrop.progression;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TasksAdminCommand implements CommandExecutor {

    private final TaskManager taskManager;

    public TasksAdminCommand(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("marbledrop.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /tasksadmin reset <daily|weekly|all> <player|all>");
            return true;
        }

        if (!args[0].equalsIgnoreCase("reset")) {
            sender.sendMessage(ChatColor.RED + "Usage: /tasksadmin reset <daily|weekly|all> <player|all>");
            return true;
        }

        String which = args[1];
        String target = args[2];

        TaskType type = null;
        boolean both = false;

        if (which.equalsIgnoreCase("daily")) type = TaskType.DAILY;
        else if (which.equalsIgnoreCase("weekly")) type = TaskType.WEEKLY;
        else if (which.equalsIgnoreCase("all")) both = true;
        else {
            sender.sendMessage(ChatColor.RED + "Second arg must be daily, weekly, or all.");
            return true;
        }

        if (target.equalsIgnoreCase("all")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (both) taskManager.forceResetAll(p);
                else taskManager.forceReset(p, type);
                p.sendActionBar(""); // clear any tracked bar immediately
            }
            sender.sendMessage(ChatColor.GREEN + "Reset " + which.toLowerCase() + " tasks for all online players.");
            return true;
        }

        Player p = Bukkit.getPlayerExact(target);
        if (p == null) {
            sender.sendMessage(ChatColor.RED + "Player not found (must be online): " + target);
            return true;
        }

        if (both) taskManager.forceResetAll(p);
        else taskManager.forceReset(p, type);

        p.sendActionBar("");
        sender.sendMessage(ChatColor.GREEN + "Reset " + which.toLowerCase() + " tasks for " + p.getName() + ".");
        return true;
    }
}
