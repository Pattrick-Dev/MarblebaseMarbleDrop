package me.pattrick.marbledrop.progression;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.*;
import java.util.*;

/**
 * Stores task progress in player PDC. No YAML. No DB.
 */
public final class TaskManager {

    private final Plugin plugin;
    private final DustManager dustManager;
    private final List<TaskDefinition> tasks;

    private final NamespacedKey K_DAILY_DATE;   // yyyy-MM-dd
    private final NamespacedKey K_WEEKLY_DATE;  // yyyy-MM-dd (week start)
    private final NamespacedKey K_TRACKED_TASK;

    // Prefixes for per-task keys
    // progress: tp_<taskId>
    // claimed:  tc_<taskId>  (0/1) -> reward claimed for the current cycle
    // complete: tdone_<taskId> (0/1) -> reached goal for the current cycle
    private final String PROGRESS_PREFIX = "tp_";
    private final String CLAIMED_PREFIX = "tc_";
    private final String DONE_PREFIX = "tdone_";

    private final ZoneId zoneId;

    public TaskManager(Plugin plugin, DustManager dustManager) {

        this.plugin = plugin;
        this.dustManager = dustManager;
        this.K_TRACKED_TASK = new NamespacedKey(plugin, "tasks_tracked_task");
        this.tasks = TaskCatalog.buildDefaults();

        this.K_DAILY_DATE = new NamespacedKey(plugin, "tasks_daily_date");
        this.K_WEEKLY_DATE = new NamespacedKey(plugin, "tasks_weekly_date");

        // Use server timezone (simple + predictable). If you want America/New_York specifically, set it here.
        this.zoneId = ZoneId.systemDefault();
    }

    public List<TaskDefinition> getTasks() {
        return tasks;
    }

    /**
     * Call this before reading/updating tasks each time (lightweight).
     */
    public void ensureResets(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        LocalDate today = LocalDate.now(zoneId);
        String todayStr = today.toString();

        // Week start = Monday
        LocalDate weekStart = today;
        while (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            weekStart = weekStart.minusDays(1);
        }
        String weekStartStr = weekStart.toString();

        String storedDaily = pdc.get(K_DAILY_DATE, PersistentDataType.STRING);
        if (storedDaily == null || !storedDaily.equals(todayStr)) {
            resetCycle(player, TaskType.DAILY);
            pdc.set(K_DAILY_DATE, PersistentDataType.STRING, todayStr);
        }

        String storedWeekly = pdc.get(K_WEEKLY_DATE, PersistentDataType.STRING);
        if (storedWeekly == null || !storedWeekly.equals(weekStartStr)) {
            resetCycle(player, TaskType.WEEKLY);
            pdc.set(K_WEEKLY_DATE, PersistentDataType.STRING, weekStartStr);
        }
    }

    private void resetCycle(Player player, TaskType type) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        for (TaskDefinition t : tasks) {
            if (t.type() != type) continue;

            NamespacedKey progressKey = key(PROGRESS_PREFIX + t.id());
            NamespacedKey claimedKey = key(CLAIMED_PREFIX + t.id());
            NamespacedKey doneKey = key(DONE_PREFIX + t.id());

            pdc.set(progressKey, PersistentDataType.INTEGER, 0);
            pdc.set(claimedKey, PersistentDataType.BYTE, (byte) 0);
            pdc.set(doneKey, PersistentDataType.BYTE, (byte) 0);
        }
    }

    public void increment(Player player, TaskTrigger trigger, int amount) {
        if (amount <= 0) return;

        ensureResets(player);

        PersistentDataContainer pdc = player.getPersistentDataContainer();

        for (TaskDefinition t : tasks) {
            if (t.trigger() != trigger) continue;

            NamespacedKey progressKey = key(PROGRESS_PREFIX + t.id());
            NamespacedKey doneKey = key(DONE_PREFIX + t.id());
            NamespacedKey claimedKey = key(CLAIMED_PREFIX + t.id());

            Integer cur = pdc.get(progressKey, PersistentDataType.INTEGER);
            int current = (cur != null) ? cur : 0;

            int updated = Math.min(t.goal(), current + amount);
            pdc.set(progressKey, PersistentDataType.INTEGER, updated);

            // Mark done if goal reached
            if (updated >= t.goal()) {
                pdc.set(doneKey, PersistentDataType.BYTE, (byte) 1);

                // Auto-claim ONCE
                Byte claimed = pdc.get(claimedKey, PersistentDataType.BYTE);
                boolean alreadyClaimed = claimed != null && claimed == (byte) 1;

                if (!alreadyClaimed) {
                    pdc.set(claimedKey, PersistentDataType.BYTE, (byte) 1);
                    dustManager.addDust(player, t.rewardDust());

                    // Optional: small feedback (remove if you want it silent)
                    player.sendMessage(ChatColor.GREEN + "Task completed! +" + ChatColor.GOLD + t.rewardDust()
                            + ChatColor.GREEN + " dust (" + ChatColor.WHITE + t.displayName() + ChatColor.GREEN + ")");
                }
            }
        }
    }

    public int getProgress(Player player, TaskDefinition t) {
        ensureResets(player);
        Integer cur = player.getPersistentDataContainer().get(key(PROGRESS_PREFIX + t.id()), PersistentDataType.INTEGER);
        return cur != null ? Math.max(0, cur) : 0;
    }

    public boolean isDone(Player player, TaskDefinition t) {
        ensureResets(player);
        Byte b = player.getPersistentDataContainer().get(key(DONE_PREFIX + t.id()), PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    public boolean isClaimed(Player player, TaskDefinition t) {
        ensureResets(player);
        Byte b = player.getPersistentDataContainer().get(key(CLAIMED_PREFIX + t.id()), PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    /**
     * Claims any completed, unclaimed tasks. Returns dust granted.
     */
    public int claimAll(Player player) {
        ensureResets(player);

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int totalDust = 0;

        for (TaskDefinition t : tasks) {
            boolean done = isDone(player, t);
            boolean claimed = isClaimed(player, t);
            if (!done || claimed) continue;

            totalDust += t.rewardDust();
            pdc.set(key(CLAIMED_PREFIX + t.id()), PersistentDataType.BYTE, (byte) 1);
        }

        if (totalDust > 0) {
            dustManager.addDust(player, totalDust);
        }

        return totalDust;
    }

    private NamespacedKey key(String key) {
        // task keys are per-plugin namespace; key strings must be <= 64-ish, so keep ids short.
        return new NamespacedKey(plugin, key);
    }

    // ===== Task tracking (Action Bar) =====

    public TaskDefinition getTaskById(String id) {
        if (id == null) return null;
        for (TaskDefinition t : tasks) {
            if (t.id().equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    public String getTrackedTaskId(Player player) {
        ensureResets(player);
        return player.getPersistentDataContainer()
                .get(K_TRACKED_TASK, PersistentDataType.STRING);
    }

    public void setTrackedTask(Player player, String taskId) {
        if (taskId == null) return;

        TaskDefinition t = getTaskById(taskId);
        if (t == null) return;

        player.getPersistentDataContainer()
                .set(K_TRACKED_TASK, PersistentDataType.STRING, t.id());
    }

    public void clearTrackedTask(Player player) {
        player.getPersistentDataContainer().remove(K_TRACKED_TASK);
    }

    public void forceReset(Player player, TaskType type) {
        resetCycle(player, type);

        // Update the stored cycle markers so ensureResets doesn't instantly re-reset weirdly
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        LocalDate today = LocalDate.now(zoneId);
        String todayStr = today.toString();

        LocalDate weekStart = today;
        while (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            weekStart = weekStart.minusDays(1);
        }
        String weekStartStr = weekStart.toString();

        if (type == TaskType.DAILY) {
            pdc.set(K_DAILY_DATE, PersistentDataType.STRING, todayStr);
        } else if (type == TaskType.WEEKLY) {
            pdc.set(K_WEEKLY_DATE, PersistentDataType.STRING, weekStartStr);
        }
    }

    public void forceResetAll(Player player) {
        forceReset(player, TaskType.DAILY);
        forceReset(player, TaskType.WEEKLY);
    }

    public long getSecondsUntilDailyReset() {
        LocalDate today = LocalDate.now(zoneId);
        LocalDateTime nextMidnight = today.plusDays(1).atStartOfDay();
        return Duration.between(LocalDateTime.now(zoneId), nextMidnight).getSeconds();
    }

    public long getSecondsUntilWeeklyReset() {
        LocalDateTime now = LocalDateTime.now(zoneId);

        LocalDate nextMonday = LocalDate.now(zoneId);
        while (nextMonday.getDayOfWeek() != DayOfWeek.MONDAY) {
            nextMonday = nextMonday.plusDays(1);
        }

        LocalDateTime nextWeekReset = nextMonday.atStartOfDay();
        if (!nextWeekReset.isAfter(now)) {
            nextWeekReset = nextWeekReset.plusWeeks(1);
        }

        return Duration.between(now, nextWeekReset).getSeconds();
    }

    public String formatDuration(long seconds) {
        if (seconds < 0) seconds = 0;

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }


}
