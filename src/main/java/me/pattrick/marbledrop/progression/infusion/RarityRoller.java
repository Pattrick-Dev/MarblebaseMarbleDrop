package me.pattrick.marbledrop.progression.infusion;

import me.pattrick.marbledrop.marble.MarbleRarity;

import java.util.Random;

public class RarityRoller {

    private static final Random RANDOM = new Random();

    /**
     * Weighted roll based on effectiveValue bands.
     * This makes LEGENDARY feel very special and easier to tune than threshold math.
     */
    public static MarbleRarity roll(int effectiveValue) {
        return rollBase(effectiveValue);
    }

    /**
     * Roll with optional marble catalyst rarity bias.
     * Uses weighted odds + limited rerolls + small soft floor.
     */
    public static MarbleRarity roll(int effectiveValue, MarbleRarity catalystRarity) {
        MarbleRarity best = rollBase(effectiveValue);

        if (catalystRarity == null) {
            return best;
        }

        // Catalyst should help, but not turn LEGENDARY into "common"
        int extraRolls;
        double floorChance;

        switch (catalystRarity) {
            case UNCOMMON -> {
                extraRolls = 1;
                floorChance = 0.20;
            }
            case RARE -> {
                extraRolls = 1;
                floorChance = 0.30;
            }
            case EPIC -> {
                extraRolls = 2;
                floorChance = 0.40;
            }
            case LEGENDARY -> {
                extraRolls = 2;
                floorChance = 0.50;
            }
            default -> {
                extraRolls = 0;
                floorChance = 0.0;
            }
        }

        // Extra rerolls – take best result
        for (int i = 0; i < extraRolls; i++) {
            MarbleRarity rolled = rollBase(effectiveValue);
            if (rolled.ordinal() > best.ordinal()) {
                best = rolled;
            }
        }

        // Soft rarity floor (small nudge, not a guarantee)
        if (best.ordinal() < catalystRarity.ordinal()) {
            if (RANDOM.nextDouble() < floorChance) {
                best = catalystRarity;
            }
        }

        return best;
    }

    /**
     * Core rarity roll logic (weighted, banded).
     * Weights are in basis points: 10000 = 100%
     */
    private static MarbleRarity rollBase(int effectiveValue) {
        // Clamp at 0 to avoid negative weirdness
        int v = Math.max(0, effectiveValue);

        // Choose weights based on effective value band
        int commonW;
        int uncommonW;
        int rareW;
        int epicW;
        int legendaryW;

        if (v < 150) {
            // 50–149 typical "minimum" infusions: LEGENDARY is impossible here
            commonW = 7000;
            uncommonW = 2500;
            rareW = 500;
            epicW = 0;
            legendaryW = 0;
        } else if (v < 250) {
            // 150–249: investing
            commonW = 4500;
            uncommonW = 3500;
            rareW = 1800;
            epicW = 200;
            legendaryW = 0;
        } else if (v < 350) {
            // 250–349: serious
            commonW = 2000;
            uncommonW = 3500;
            rareW = 3500;
            epicW = 900;
            legendaryW = 100;
        } else if (v < 450) {
            // 350–449: big spend
            commonW = 1000;
            uncommonW = 2500;
            rareW = 4000;
            epicW = 2200;
            legendaryW = 300;
        } else {
            // 450+: endgame
            commonW = 500;
            uncommonW = 1500;
            rareW = 3500;
            epicW = 3500;
            legendaryW = 1000;
        }

        // Safety: normalize to 10000 if someone edits numbers later
        int total = commonW + uncommonW + rareW + epicW + legendaryW;
        if (total <= 0) {
            return MarbleRarity.COMMON;
        }
        if (total != 10000) {
            // Scale weights proportionally to sum to 10000
            commonW = scale(commonW, total);
            uncommonW = scale(uncommonW, total);
            rareW = scale(rareW, total);
            epicW = scale(epicW, total);

            // Assign remainder to legendary to ensure sum is exactly 10000
            legendaryW = 10000 - (commonW + uncommonW + rareW + epicW);
            if (legendaryW < 0) legendaryW = 0;
        }

        int roll = RANDOM.nextInt(10000); // 0..9999

        if (roll < commonW) return MarbleRarity.COMMON;
        roll -= commonW;

        if (roll < uncommonW) return MarbleRarity.UNCOMMON;
        roll -= uncommonW;

        if (roll < rareW) return MarbleRarity.RARE;
        roll -= rareW;

        if (roll < epicW) return MarbleRarity.EPIC;

        return MarbleRarity.LEGENDARY;
    }

    private static int scale(int weight, int total) {
        // (weight / total) * 10000 with rounding
        return (int) Math.round((weight * 10000.0) / total);
    }
}
