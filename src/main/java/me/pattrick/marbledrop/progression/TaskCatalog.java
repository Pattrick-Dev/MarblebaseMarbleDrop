package me.pattrick.marbledrop.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Same-for-everyone tasks (starter SMP set).
 */
public final class TaskCatalog {

    private TaskCatalog() {}

    public static List<TaskDefinition> buildDefaults() {
        List<TaskDefinition> tasks = new ArrayList<>();

        // DAILY
        tasks.add(new TaskDefinition(
                "daily_break_128",
                TaskType.DAILY,
                TaskTrigger.BREAK_BLOCKS,
                128,
                50,
                "Break 128 blocks"
        ));

        tasks.add(new TaskDefinition(
                "daily_kill_12",
                TaskType.DAILY,
                TaskTrigger.KILL_MOBS,
                12,
                60,
                "Kill 12 mobs"
        ));

        tasks.add(new TaskDefinition(
                "daily_walk_1500",
                TaskType.DAILY,
                TaskTrigger.WALK_DISTANCE_BLOCKS,
                1500,
                40,
                "Walk 1500 blocks"
        ));

        // WEEKLY
        tasks.add(new TaskDefinition(
                "weekly_break_2000",
                TaskType.WEEKLY,
                TaskTrigger.BREAK_BLOCKS,
                2000,
                350,
                "Break 2000 blocks"
        ));

        tasks.add(new TaskDefinition(
                "weekly_fish_30",
                TaskType.WEEKLY,
                TaskTrigger.FISH_CAUGHT,
                30,
                400,
                "Catch 30 fish"
        ));

        return Collections.unmodifiableList(tasks);
    }
}
