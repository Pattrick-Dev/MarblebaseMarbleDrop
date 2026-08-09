package me.pattrick.marbledrop.progression;

/**
 * What kind of gameplay action increments a task.
 */
public enum TaskTrigger {
    BREAK_BLOCKS,
    KILL_MOBS,
    FISH_CAUGHT,
    WALK_DISTANCE_BLOCKS,

    // Marble/racing-specific -- see MarbleRecyclerListener, InfusionService,
    // RaceManager#onFinish, and RaceBoostListener for where these fire.
    RECYCLE_MARBLE,
    INFUSE_MARBLE,
    WIN_RACE,
    PLACE_TOP_3,
    FINISH_RACE,
    USE_BOOST
}
