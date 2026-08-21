package me.pattrick.marbledrop.progression.infusion;

import me.pattrick.marbledrop.marble.MarbleRarity;

import java.util.Random;

public class RarityRoller {

    private static final Random RANDOM = new Random();

    // Per same-day infusion already done, how much of the top band's epic+
    // legendary pool is retained (rest shifts into RARE) - see rollBase.
    // Floored rather than driven to 0 so the top band never fully collapses,
    // just trends down. With the daily cap at 5, the 5th infusion of a day
    // retains only RETAIN_FLOOR.
    private static final double TAPER_PER_INFUSION = 0.22;
    private static final double RETAIN_FLOOR = 0.10;

    /**
     * Weighted roll based on effectiveValue bands.
     * This makes LEGENDARY feel very special and easier to tune than threshold math.
     */
    public static MarbleRarity roll(int effectiveValue) {
        return roll(effectiveValue, 0);
    }

    /**
     * Same roll as {@link #roll(int)}, but infusionsAlreadyToday (0 for a
     * player's first infusion of the day) tapers the top band's odds down -
     * see rollBase. Without this, a player who can afford to spend enough
     * dust to hit the top band on every infusion gets a flat best-odds roll
     * every single time, indefinitely; this makes repeated same-day top-band
     * hits trend toward RARE instead of staying EPIC/LEGENDARY-heavy.
     */
    public static MarbleRarity roll(int effectiveValue, int infusionsAlreadyToday) {
        return rollBase(effectiveValue, Math.max(0, infusionsAlreadyToday));
    }

    /**
     * Core rarity roll logic (weighted, banded).
     * Weights are in basis points: 10000 = 100%
     */
    private static MarbleRarity rollBase(int effectiveValue, int infusionsAlreadyToday) {
        // Clamp at 0 to avoid negative weirdness
        int v = Math.max(0, effectiveValue);

        // Choose weights based on effective value band
        int commonW;
        int uncommonW;
        int rareW;
        int epicW;
        int legendaryW;

        if (v < 150) {
            // 50-149 typical "minimum" infusions: LEGENDARY is impossible here
            commonW = 7000;
            uncommonW = 2500;
            rareW = 500;
            epicW = 0;
            legendaryW = 0;
        } else if (v < 250) {
            // 150-249: investing
            commonW = 4500;
            uncommonW = 3500;
            rareW = 1800;
            epicW = 200;
            legendaryW = 0;
        } else if (v < 350) {
            // 250-349: serious
            commonW = 2000;
            uncommonW = 3500;
            rareW = 3500;
            epicW = 900;
            legendaryW = 100;
        } else if (v < 450) {
            // 350-449: big spend
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

            // Taper repeated same-day hits in this band - shift weight out of
            // the epic+legendary pool into RARE, keeping the epic:legendary
            // ratio within what's left the same rather than collapsing
            // legendary to 0 first.
            double retain = Math.max(RETAIN_FLOOR, 1.0 - TAPER_PER_INFUSION * infusionsAlreadyToday);
            int pool = epicW + legendaryW;
            int retainedPool = (int) Math.round(pool * retain);
            int newLegendaryW = (int) Math.round(retainedPool * (legendaryW / (double) pool));
            int newEpicW = retainedPool - newLegendaryW;
            rareW += pool - retainedPool;
            epicW = newEpicW;
            legendaryW = newLegendaryW;
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
