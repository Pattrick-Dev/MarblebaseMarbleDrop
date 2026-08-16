package me.pattrick.marbledrop.tutorial;

import java.util.Set;

/**
 * Ordered steps of the forced onboarding tutorial. Teams is intentionally
 * left out since that system isn't fleshed out yet.
 * <p>
 * commandKeywords is still used for /md command gating (blocking every
 * subcommand except the current step's), but actual step COMPLETION is
 * now driven by real gameplay actions (killing the tutorial sheep,
 * receiving a marble, upgrading it, recycling it, finishing a race),
 * not by simply typing the command.
 */
public enum TutorialStep {

    TASKS(
            Set.of("tasks"),
            "Kill the Sheep",
            "Type /tasks, then kill the sheep that spawns.",
            50
    ),
    CRAFT(
            Set.of(), // crafting has no /md command to gate - see TutorialCraftHook
            "Learn the Recipes",
            "Open a crafting table and craft the station shown - the next recipe's ingredients appear once you're done. Each is taken back once crafted; you'll get real ones later.",
            0
    ),
    INFUSION(
            Set.of("table", "infusiontable"),
            "Get Your First Marble",
            "Right-click the Infusion Cauldron in front of you, then click Infuse.",
            0
    ),
    UPGRADE(
            Set.of("upgrade", "upgrades"),
            "Upgrade Your Marble",
            "Right-click the Upgrade Station in front of you and upgrade your marble.",
            0
    ),
    RECYCLER(
            Set.of("recycler", "recycle"),
            "Try the Recycler",
            "Shift + Right-click the Recycler in front of you, holding your marble.",
            0
    ),
    RACE(
            Set.of("race", "races"),
            "Race Against AI",
            "Type /md race to enter a practice race and see how it works.",
            75
    ),
    COMPLETE(
            Set.of(),
            "Tutorial Complete",
            "You're all set. Good luck out there!",
            150
    );

    private final Set<String> commandKeywords;
    private final String title;
    private final String hint;
    private final int rewardDust;

    TutorialStep(Set<String> commandKeywords, String title, String hint, int rewardDust) {
        this.commandKeywords = commandKeywords;
        this.title = title;
        this.hint = hint;
        this.rewardDust = rewardDust;
    }

    public Set<String> commandKeywords() {
        return commandKeywords;
    }

    public String title() {
        return title;
    }

    public String hint() {
        return hint;
    }

    public int rewardDust() {
        return rewardDust;
    }

    public boolean matchesCommand(String sub) {
        return commandKeywords.contains(sub.toLowerCase());
    }

    public TutorialStep next() {
        int ni = ordinal() + 1;
        TutorialStep[] all = values();
        return ni < all.length ? all[ni] : COMPLETE;
    }

    public static int totalSteps() {
        return values().length - 1; // exclude COMPLETE from the "X / Y" count
    }
}
