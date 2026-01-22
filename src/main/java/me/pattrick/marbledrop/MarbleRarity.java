package me.pattrick.marbledrop;

import java.util.concurrent.ThreadLocalRandom;

public enum MarbleRarity {
    COMMON("Common", 40, 60),
    UNCOMMON("Uncommon", 55, 25),
    RARE("Rare", 70, 10),
    EPIC("Epic", 85, 4),
    LEGENDARY("Legendary", 100, 1);

    private final String displayName;
    private final int statCap;
    private final int weight;

    MarbleRarity(String displayName, int statCap, int weight) {
        this.displayName = displayName;
        this.statCap = statCap;
        this.weight = weight;
    }

    public String displayName() {
        return displayName;
    }

    public int statCap() {
        return statCap;
    }

    public int weight() {
        return weight;
    }

    public static MarbleRarity roll() {
        int total = 0;
        for (MarbleRarity r : values()) total += r.weight();

        int pick = ThreadLocalRandom.current().nextInt(1, total + 1);
        int running = 0;

        for (MarbleRarity r : values()) {
            running += r.weight();
            if (pick <= running) return r;
        }
        return COMMON;
    }
}
