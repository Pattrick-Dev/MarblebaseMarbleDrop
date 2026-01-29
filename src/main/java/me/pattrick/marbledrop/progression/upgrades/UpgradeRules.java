package me.pattrick.marbledrop.progression.upgrades;

import me.pattrick.marbledrop.marble.MarbleRarity;
import me.pattrick.marbledrop.marble.MarbleStat;

public final class UpgradeRules {

    private UpgradeRules() {}

    /**
     * Per-stat cap. Your MarbleRarity already has perStatCap, so use it directly.
     */
    public static int capFor(MarbleRarity rarity, MarbleStat stat) {
        if (rarity == null) return 0;
        // You can customize per-stat differences later; right now this respects rarity caps.
        return rarity.getPerStatCap();
    }

    /**
     * Dust cost scaling.
     * Simple, predictable curve:
     * - base cost depends on rarity
     * - increases slowly as the stat gets higher
     */
    public static int costFor(MarbleRarity rarity, MarbleStat stat, int currentValue) {
        int base = switch (rarity) {
            case COMMON -> 5;
            case UNCOMMON -> 8;
            case RARE -> 12;
            case EPIC -> 18;
            case LEGENDARY -> 25;
            default -> 10;
        };

        int step = Math.max(0, currentValue) / 10; // +1 cost every 10 points
        int statBias = switch (stat) {
            case SPEED -> 1;
            case ACCEL -> 1;
            case HANDLING -> 1;
            case STABILITY -> 1;
            case BOOST -> 1;
            default -> 1;
        };

        int cost = base + (step * statBias);

        // Always at least 1
        return Math.max(1, cost);
    }
}
