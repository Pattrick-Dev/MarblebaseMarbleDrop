package me.pattrick.marbledrop.progression;

import me.pattrick.marbledrop.progression.taskmenu.TasksMenu;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TasksCommand implements CommandExecutor {

    private final TaskManager taskManager;
    private final Plugin plugin;

    public TasksCommand(Plugin plugin, TaskManager taskManager) {
        this.plugin = plugin;
        this.taskManager = taskManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        taskManager.ensureResets(player);

        // /tasks claim
        if (args.length > 0 && args[0].equalsIgnoreCase("claim")) {
            int gained = taskManager.claimAll(player);
            if (gained <= 0) {
                player.sendMessage(ChatColor.GRAY + "No completed tasks to claim yet.");
            } else {
                player.sendMessage(ChatColor.GREEN + "Claimed " + gained + " Marble Dust!");
            }
            return true;
        }

        // /tasks track <taskId> (used by GUI clicks)
        if (args.length > 0 && args[0].equalsIgnoreCase("track")) {
            if (args.length < 2) {
                return true;
            }

            String id = args[1];
            TaskDefinition t = taskManager.getTaskById(id);
            if (t == null) {
                return true;
            }

            // 🚫 NEW: completed / claimed tasks cannot be tracked
            if (taskManager.isDone(player, t) || taskManager.isClaimed(player, t)) {
                player.sendMessage(ChatColor.GRAY + "That task is already completed.");
                player.sendActionBar("");
                return true;
            }

            String currentlyTracked = taskManager.getTrackedTaskId(player);

            if (currentlyTracked != null && currentlyTracked.equalsIgnoreCase(t.id())) {
                taskManager.clearTrackedTask(player);
                player.sendActionBar("");
                player.sendMessage(ChatColor.GRAY + "Stopped tracking: " + ChatColor.WHITE + t.displayName());
            } else {
                taskManager.setTrackedTask(player, t.id());
                player.sendMessage(ChatColor.GREEN + "Now tracking: " + ChatColor.WHITE + t.displayName());
            }

            return true;
        }

        // otherwise open GUI
        new TasksMenu(
                taskManager,
                new NamespacedKey(plugin, "tasks_menu_task_id")
        ).open(player);
        return true;
    }
}
