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
 * <p>
 * The task pool (TaskCatalog) is large (hundreds of entries per type), so no
 * player is ever handed the whole thing at once -- each reset cycle picks a
 * random subset (see DAILY_ACTIVE_COUNT/WEEKLY_ACTIVE_COUNT) and remembers
 * which ids it picked (K_DAILY_ASSIGNED/K_WEEKLY_ASSIGNED, a comma-joined id
 * list in PDC). increment()/claimAll() only ever touch that assigned
 * subset, never the full pool -- both for correctness (a player shouldn't
 * silently earn credit for a task they were never shown) and for cost (a
 * per-event linear scan over a handful of assigned tasks instead of the
 * whole catalog).
 */
public final class TaskManager {

    // How many tasks of each type a player has active at once, drawn fresh
    // from the pool on every reset. Tune freely -- nothing else assumes a
    // specific count, other than TasksMenu having 26 usable slots (27 minus
    // the reset-info clock), so keep dailyCount + weeklyCount comfortably
    // under that.
    private static final int DAILY_ACTIVE_COUNT = 5;
    private static final int WEEKLY_ACTIVE_COUNT = 3;

    private final Plugin plugin;
    private final DustManager dustManager;

    private final List<TaskDefinition> tasks;
    private final List<TaskDefinition> dailyPool;
    private final List<TaskDefinition> weeklyPool;
    private final Random random = new Random();

    private final NamespacedKey K_DAILY_DATE;   // yyyy-MM-dd
    private final NamespacedKey K_WEEKLY_DATE;  // yyyy-MM-dd (week start)
    private final NamespacedKey K_DAILY_ASSIGNED;  // comma-joined task ids currently active
    private final NamespacedKey K_WEEKLY_ASSIGNED; // comma-joined task ids currently active
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

        List<TaskDefinition> daily = new ArrayList<>();
        List<TaskDefinition> weekly = new ArrayList<>();
        for (TaskDefinition t : tasks) {
            if (t.type() == TaskType.DAILY) daily.add(t);
            else if (t.type() == TaskType.WEEKLY) weekly.add(t);
        }
        this.dailyPool = Collections.unmodifiableList(daily);
        this.weeklyPool = Collections.unmodifiableList(weekly);

        this.K_DAILY_DATE = new NamespacedKey(plugin, "tasks_daily_date");
        this.K_WEEKLY_DATE = new NamespacedKey(plugin, "tasks_weekly_date");
        this.K_DAILY_ASSIGNED = new NamespacedKey(plugin, "tasks_daily_assigned");
        this.K_WEEKLY_ASSIGNED = new NamespacedKey(plugin, "tasks_weekly_assigned");

        // Use server timezone (simple + predictable). If you want America/New_York specifically, set it here.
        this.zoneId = ZoneId.systemDefault();
    }

    /** The full task pool, every type -- for admin/lookup use. Players only ever see getActiveTasks(). */
    public List<TaskDefinition> getTasks() {
        return tasks;
    }

    private List<TaskDefinition> poolFor(TaskType type) {
        return (type == TaskType.DAILY) ? dailyPool : weeklyPool;
    }

    private int activeCountFor(TaskType type) {
        return (type == TaskType.DAILY) ? DAILY_ACTIVE_COUNT : WEEKLY_ACTIVE_COUNT;
    }

    private NamespacedKey assignedKeyFor(TaskType type) {
        return (type == TaskType.DAILY) ? K_DAILY_ASSIGNED : K_WEEKLY_ASSIGNED;
    }

    /**
     * This player's currently-active tasks of one type -- a small random
     * subset of the pool, picked at the last reset (see resetCycle()).
     */
    public List<TaskDefinition> getActiveTasks(Player player, TaskType type) {
        ensureResets(player);

        String raw = player.getPersistentDataContainer().get(assignedKeyFor(type), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return List.of();

        List<TaskDefinition> out = new ArrayList<>();
        for (String id : raw.split(",")) {
            TaskDefinition t = getTaskById(id);
            if (t != null) out.add(t);
        }
        return out;
    }

    /** Both types combined -- what TasksMenu shows. */
    public List<TaskDefinition> getActiveTasks(Player player) {
        List<TaskDefinition> out = new ArrayList<>(getActiveTasks(player, TaskType.DAILY));
        out.addAll(getActiveTasks(player, TaskType.WEEKLY));
        return out;
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

        // Re-roll on a date mismatch (the normal case) OR a missing/blank
        // assignment even when the date already matches -- covers a player
        // who reset earlier today/this week under an older build, before
        // the assigned-task list existed at all. Without this, that player
        // would see nothing until the next natural reset, since the date
        // marker alone looks already up to date.
        String storedDaily = pdc.get(K_DAILY_DATE, PersistentDataType.STRING);
        String dailyAssigned = pdc.get(K_DAILY_ASSIGNED, PersistentDataType.STRING);
        if (storedDaily == null || !storedDaily.equals(todayStr) || dailyAssigned == null || dailyAssigned.isBlank()) {
            resetCycle(player, TaskType.DAILY);
            pdc.set(K_DAILY_DATE, PersistentDataType.STRING, todayStr);
        }

        String storedWeekly = pdc.get(K_WEEKLY_DATE, PersistentDataType.STRING);
        String weeklyAssigned = pdc.get(K_WEEKLY_ASSIGNED, PersistentDataType.STRING);
        if (storedWeekly == null || !storedWeekly.equals(weekStartStr) || weeklyAssigned == null || weeklyAssigned.isBlank()) {
            resetCycle(player, TaskType.WEEKLY);
            pdc.set(K_WEEKLY_DATE, PersistentDataType.STRING, weekStartStr);
        }
    }

    /**
     * Picks a fresh random set of active task ids for this type from the
     * pool, stores the assignment, and zeroes progress/claimed/done for
     * exactly those ids. Old assigned ids are left alone -- they're simply
     * unreachable once the assignment string moves on, not worth the extra
     * bookkeeping to clean up.
     * <p>
     * At most one task per trigger -- TaskCatalog generates several
     * differently-worded tasks for the same (trigger, goal) (e.g. "Perform
     * 10 infusions" and "Produce 10 infused marbles" are both just
     * INFUSE_MARBLE x10), and a plain random pick over the whole pool could
     * hand a player two of them at once, which reads as a duplicate task
     * even though the ids differ. Capping one-per-trigger rules that out
     * entirely, and incidentally guarantees more varied assignments too.
     * Always satisfiable here since count (5 or 3) is well under the
     * number of distinct triggers.
     */
    private void resetCycle(Player player, TaskType type) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        List<TaskDefinition> pool = poolFor(type);
        int count = Math.min(activeCountFor(type), pool.size());

        List<TaskDefinition> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);

        Set<TaskTrigger> usedTriggers = new HashSet<>();
        List<TaskDefinition> selected = new ArrayList<>(count);
        for (TaskDefinition t : shuffled) {
            if (selected.size() >= count) break;
            if (!usedTriggers.add(t.trigger())) continue; // already picked this trigger this cycle
            selected.add(t);
        }

        StringBuilder ids = new StringBuilder();
        for (TaskDefinition t : selected) {
            if (ids.length() > 0) ids.append(',');
            ids.append(t.id());

            pdc.set(key(PROGRESS_PREFIX + t.id()), PersistentDataType.INTEGER, 0);
            pdc.set(key(CLAIMED_PREFIX + t.id()), PersistentDataType.BYTE, (byte) 0);
            pdc.set(key(DONE_PREFIX + t.id()), PersistentDataType.BYTE, (byte) 0);
        }

        pdc.set(assignedKeyFor(type), PersistentDataType.STRING, ids.toString());
    }

    public void increment(Player player, TaskTrigger trigger, int amount) {
        if (amount <= 0) return;

        ensureResets(player);

        PersistentDataContainer pdc = player.getPersistentDataContainer();

        for (TaskDefinition t : getActiveTasks(player)) {
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
     * Claims any completed, unclaimed tasks (from the player's currently
     * active set). Returns dust granted.
     */
    public int claimAll(Player player) {
        ensureResets(player);

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int totalDust = 0;

        for (TaskDefinition t : getActiveTasks(player)) {
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

    /** Looks up any task by id, across the WHOLE pool (not just what's currently assigned) -- needed for GUI clicks and admin tooling. */
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
