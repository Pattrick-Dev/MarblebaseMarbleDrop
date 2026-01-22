package me.pattrick.marbledrop;

import java.util.concurrent.ThreadLocalRandom;

public class MarbleRoller {

    /**
     * Base stat roll rules:
     * - Rarity decides the overall “band”
     * - Each stat gets rolled in that band with slight personality differences
     * - Nothing exceeds rarity cap (cap is mainly for upgrades later, but we enforce it anyway)
     */
    public static MarbleStats rollBaseStats(MarbleRarity rarity) {
        int min, max;

        switch (rarity) {
            case COMMON -> { min = 10; max = 25; }
            case UNCOMMON -> { min = 20; max = 40; }
            case RARE -> { min = 35; max = 60; }
            case EPIC -> { min = 55; max = 80; }
            case LEGENDARY -> { min = 75; max = 95; }
            default -> { min = 10; max = 25; }
        }

        // “Personality” bias: speed/momentum slightly higher variance
        int speed = roll(min, max + 5, rarity.statCap());
        int control = roll(min, max, rarity.statCap());
        int momentum = roll(min, max + 5, rarity.statCap());
        int stability = roll(min, max, rarity.statCap());

        // Luck should stay smaller so it doesn’t dominate
        int luck = roll(Math.max(1, min / 3), Math.max(5, max / 2), rarity.statCap());

        return new MarbleStats(speed, control, momentum, stability, luck);
    }

    private static int roll(int min, int max, int cap) {
        int v = ThreadLocalRandom.current().nextInt(min, max + 1);
        return Math.min(v, cap);
    }
}
