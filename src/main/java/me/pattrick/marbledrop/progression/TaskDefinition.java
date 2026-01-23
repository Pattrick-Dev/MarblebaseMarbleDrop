package me.pattrick.marbledrop.progression;

public final class TaskDefinition {
    private final String id;
    private final TaskType type;
    private final TaskTrigger trigger;
    private final int goal;
    private final int rewardDust;
    private final String displayName;

    public TaskDefinition(String id, TaskType type, TaskTrigger trigger, int goal, int rewardDust, String displayName) {
        this.id = id;
        this.type = type;
        this.trigger = trigger;
        this.goal = goal;
        this.rewardDust = rewardDust;
        this.displayName = displayName;
    }

    public String id() { return id; }
    public TaskType type() { return type; }
    public TaskTrigger trigger() { return trigger; }
    public int goal() { return goal; }
    public int rewardDust() { return rewardDust; }
    public String displayName() { return displayName; }
}
