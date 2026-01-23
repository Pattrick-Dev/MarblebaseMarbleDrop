package me.pattrick.marbledrop.progression;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class ActionBarTaskTracker extends BukkitRunnable {

    private final Plugin plugin;
    private final TaskManager taskManager;

    public ActionBarTaskTracker(Plugin plugin, TaskManager taskManager) {
        this.plugin = plugin;
        this.taskManager = taskManager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {

            taskManager.ensureResets(player);

            String trackedId = taskManager.getTrackedTaskId(player);
            if (trackedId == null) continue;

            TaskDefinition t = taskManager.getTaskById(trackedId);
            if (t == null) {
                taskManager.clearTrackedTask(player);
                continue;
            }

            int progress = taskManager.getProgress(player, t);
            boolean done = taskManager.isDone(player, t);
            boolean claimed = taskManager.isClaimed(player, t);

            // If task is completed/claimed, stop tracking and clear the action bar
            if (done || claimed) {
                taskManager.clearTrackedTask(player);
                player.sendActionBar("");
                continue;
            }


            String typeColor = (t.type() == TaskType.DAILY)
                    ? ChatColor.AQUA.toString()
                    : ChatColor.LIGHT_PURPLE.toString();

            String status;
            if (claimed) status = ChatColor.DARK_GRAY + "CLAIMED";
            else if (done) status = ChatColor.GREEN + "DONE (/tasks claim)";
            else status = ChatColor.YELLOW + "" + progress + "/" + t.goal();

            String msg =
                    typeColor + "[" + t.type().name() + "] "
                            + ChatColor.WHITE + t.displayName()
                            + ChatColor.GRAY + " • "
                            + status
                            + ChatColor.GRAY + " • "
                            + ChatColor.GOLD + t.rewardDust() + " dust";

            // Paper-safe action bar
            player.sendActionBar(msg);
        }
    }

    public void start() {
        // run every second
        this.runTaskTimer(plugin, 20L, 20L);
    }
}
