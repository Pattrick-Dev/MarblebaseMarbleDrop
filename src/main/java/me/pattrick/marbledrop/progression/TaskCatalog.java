package me.pattrick.marbledrop.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The task pool - generated, not hand-written. With hundreds of tasks per
 * type, individually authoring each TaskDefinition would make this file
 * enormous and unreviewable; instead, each trigger gets a small set of
 * flavor-text phrasings and a handful of goal tiers, and every
 * (tier x phrasing) combination becomes one task. TaskManager only ever
 * hands a player a small random subset of this pool per cycle (see
 * DAILY_ACTIVE_COUNT/WEEKLY_ACTIVE_COUNT there) - this class is just the
 * pool they're drawn from.
 * <p>
 * Reward scales with goal size (dustPerUnit), with a small per-tier
 * discount so the biggest tasks don't scale purely linearly.
 */
public final class TaskCatalog {

    private TaskCatalog() {}

    /**
     * One trigger's generation recipe: how it reads in the GUI, how much
     * each unit of progress is worth in dust, and what goal counts it comes
     * in for daily vs. weekly. {n} in a template is replaced with the goal.
     */
    private record Def(TaskTrigger trigger, String idAbbrev, double dustPerUnit,
                        String[] templates, int[] dailyGoals, int[] weeklyGoals) {}

    private static final Def[] DEFS = {
            new Def(TaskTrigger.BREAK_BLOCKS, "brk", 0.40, new String[] {
                    "Break {n} blocks", "Mine {n} blocks", "Dig up {n} blocks", "Punch through {n} blocks",
                    "Demolish {n} blocks", "Excavate {n} blocks", "Chip away {n} blocks", "Tear down {n} blocks",
                    "Quarry {n} blocks", "Clear out {n} blocks"
            }, new int[] {50, 100, 200, 350, 500}, new int[] {500, 1000, 2000, 3500, 5000}),

            new Def(TaskTrigger.KILL_MOBS, "kil", 4.5, new String[] {
                    "Kill {n} mobs", "Defeat {n} mobs", "Slay {n} monsters", "Take down {n} mobs",
                    "Eliminate {n} hostiles", "Clear {n} mobs", "Hunt down {n} mobs", "Vanquish {n} monsters",
                    "Wipe out {n} mobs", "Put down {n} hostile mobs"
            }, new int[] {5, 10, 20, 35, 50}, new int[] {50, 100, 175, 250, 400}),

            new Def(TaskTrigger.FISH_CAUGHT, "fsh", 12.0, new String[] {
                    "Catch {n} fish", "Reel in {n} fish", "Land {n} fish", "Hook {n} fish",
                    "Fish up {n} catches", "Bring in {n} fish", "Cast and catch {n} fish", "Haul in {n} fish",
                    "Net {n} fish", "Snag {n} fish"
            }, new int[] {3, 6, 10, 15, 25}, new int[] {15, 30, 50, 75, 120}),

            new Def(TaskTrigger.WALK_DISTANCE_BLOCKS, "wlk", 0.03, new String[] {
                    "Walk {n} blocks", "Travel {n} blocks", "Wander {n} blocks", "Trek {n} blocks",
                    "Roam {n} blocks", "Journey {n} blocks", "Cover {n} blocks on foot", "Explore {n} blocks of distance",
                    "Hike {n} blocks", "Cross {n} blocks"
            }, new int[] {500, 1000, 1500, 2500, 4000}, new int[] {3000, 6000, 10000, 15000, 25000}),

            new Def(TaskTrigger.RECYCLE_MARBLE, "rcy", 15.0, new String[] {
                    "Recycle {n} marbles", "Grind down {n} marbles", "Break down {n} marbles for dust", "Turn in {n} marbles",
                    "Scrap {n} marbles", "Recycle {n} marbles for Dust", "Send {n} marbles to the grindstone", "Convert {n} marbles into Dust",
                    "Salvage {n} marbles", "Process {n} marbles at a recycler"
            }, new int[] {1, 2, 3, 5, 8}, new int[] {5, 10, 15, 25, 40}),

            // dustPerUnit must clear infusion.min-dust (50, see InfusionService) with
            // room to spare after the per-tier discount, or this task pays out less
            // than it costs to complete - 70 keeps every tier comfortably profitable.
            new Def(TaskTrigger.INFUSE_MARBLE, "inf", 70.0, new String[] {
                    "Infuse {n} marbles", "Create {n} infused marbles", "Roll {n} marble infusions", "Infuse {n} new marbles",
                    "Spend Dust on {n} infusions", "Produce {n} infused marbles", "Craft {n} marbles via infusion", "Perform {n} infusions",
                    "Forge {n} marbles with Dust", "Complete {n} infusion attempts"
            }, new int[] {1, 2, 3, 5, 8}, new int[] {5, 10, 15, 25, 40}),

            new Def(TaskTrigger.WIN_RACE, "win", 60.0, new String[] {
                    "Win {n} races", "Take 1st place in {n} races", "Finish 1st in {n} races", "Cross the line first {n} times",
                    "Claim victory in {n} races", "Win {n} marble races", "Come in 1st place {n} times", "Beat the field {n} times",
                    "Take the checkered flag {n} times", "Win {n} races outright"
            }, new int[] {1, 2, 3, 4, 5}, new int[] {3, 5, 8, 12, 18}),

            new Def(TaskTrigger.PLACE_TOP_3, "top3", 30.0, new String[] {
                    "Place top 3 in {n} races", "Finish on the podium {n} times", "Place in the top 3 {n} times", "Land a podium finish {n} times",
                    "Finish top-3 in {n} races", "Podium {n} times", "Place 1st, 2nd, or 3rd {n} times", "Score {n} podium finishes",
                    "Reach the top 3 {n} times", "Finish among the top 3 {n} times"
            }, new int[] {1, 2, 3, 5, 7}, new int[] {5, 8, 12, 18, 25}),

            new Def(TaskTrigger.FINISH_RACE, "fin", 15.0, new String[] {
                    "Finish {n} races", "Complete {n} races", "Cross the finish line {n} times", "Race to completion {n} times",
                    "Take part in and finish {n} races", "Finish {n} marble races", "Complete {n} race entries", "See {n} races through to the end",
                    "Finish {n} races of any placement", "Wrap up {n} races"
            }, new int[] {1, 2, 3, 5, 8}, new int[] {5, 10, 15, 25, 35}),

            new Def(TaskTrigger.USE_BOOST, "bst", 8.0, new String[] {
                    "Use Boost {n} times", "Trigger {n} Boosts", "Activate Boost {n} times", "Use {n} Boost charges",
                    "Fire off {n} Boosts", "Pop {n} Boosts during races", "Spend {n} Boost charges", "Use your marble's Boost {n} times",
                    "Trigger a Boost {n} times", "Burn {n} Boost charges"
            }, new int[] {2, 4, 6, 10, 15}, new int[] {10, 20, 35, 50, 75}),
    };

    public static List<TaskDefinition> buildDefaults() {
        List<TaskDefinition> tasks = new ArrayList<>();

        for (Def def : DEFS) {
            generate(tasks, def, TaskType.DAILY, "d_", def.dailyGoals());
            generate(tasks, def, TaskType.WEEKLY, "w_", def.weeklyGoals());
        }

        return Collections.unmodifiableList(tasks);
    }

    private static void generate(List<TaskDefinition> out, Def def, TaskType type, String idPrefix, int[] goals) {
        for (int tierIndex = 0; tierIndex < goals.length; tierIndex++) {
            int goal = goals[tierIndex];
            // Mild per-tier discount so the biggest goals don't scale purely
            // linearly (matches how the original hand-picked daily/weekly
            // examples felt - e.g. break-128 paid ~0.39/block, break-2000
            // paid ~0.175/block).
            double tierDiscount = 1.0 - (tierIndex * 0.03);
            int reward = Math.max(10, (int) Math.round(goal * def.dustPerUnit() * tierDiscount));

            String[] templates = def.templates();
            for (int templateIndex = 0; templateIndex < templates.length; templateIndex++) {
                String displayName = templates[templateIndex].replace("{n}", String.valueOf(goal));
                String id = idPrefix + def.idAbbrev() + "_" + goal + "_" + templateIndex;

                out.add(new TaskDefinition(id, type, def.trigger(), goal, reward, displayName));
            }
        }
    }
}
