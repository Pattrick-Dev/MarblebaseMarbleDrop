package me.pattrick.marbledrop.progression.infusion;

import me.pattrick.marbledrop.marble.MarbleRarity;
import me.pattrick.marbledrop.marble.MarbleStat;
import me.pattrick.marbledrop.marble.MarbleStats;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class StatRoller {

    private StatRoller() {}

    /**
     * Stats are out of 100.
     * We roll within a rarity-based range, then enforce a rarity-based minimum total.
     *
     * Total power is out of 500 (5 stats * 100).
     */
    public static final int STAT_MIN = 1;
    public static final int STAT_MAX = 100;

    public static MarbleStats rollStats(MarbleRarity rarity) {
        return rollStats(rarity, Map.of());
    }

    /**
     * Same roll as {@link #rollStats(MarbleRarity)}, but stats present in
     * statAffinity (weight 0-1) get an extra reroll-take-best chance
     * proportional to their weight - e.g. a Blaze Rod catalyst configured
     * for BOOST gives BOOST a chance to reroll-and-keep-the-better-result,
     * while every other stat rolls exactly as it always has. Pure upside:
     * a stat with affinity never rolls worse than an unbiased roll would.
     */
    public static MarbleStats rollStats(MarbleRarity rarity, Map<MarbleStat, Double> statAffinity) {

        // Rarity tiers (min/max per stat, plus a minimum total power floor)
        int min;
        int max;
        int minTotal;

        switch (rarity) {
            case COMMON -> {
                min = 10;
                max = 45;
                minTotal = 140;  // out of 500
            }
            case UNCOMMON -> {
                min = 25;
                max = 60;
                minTotal = 220;
            }
            case RARE -> {
                min = 40;
                max = 75;
                minTotal = 300;
            }
            case EPIC -> {
                min = 55;
                max = 90;
                minTotal = 380;
            }
            case LEGENDARY -> {
                min = 70;
                max = 100;
                minTotal = 440;
            }
            default -> {
                min = 10;
                max = 45;
                minTotal = 140;
            }
        }

        int speed = rollAffine(min, max, weightOf(statAffinity, MarbleStat.SPEED));
        int accel = rollAffine(min, max, weightOf(statAffinity, MarbleStat.ACCEL));
        int handling = rollAffine(min, max, weightOf(statAffinity, MarbleStat.HANDLING));
        int stability = rollAffine(min, max, weightOf(statAffinity, MarbleStat.STABILITY));
        int boost = rollAffine(min, max, weightOf(statAffinity, MarbleStat.BOOST));

        int total = speed + accel + handling + stability + boost;

        // Enforce minimum total by boosting random stats (never exceeding 100)
        int safety = 5000; // just in case; should never be hit
        while (total < minTotal && safety-- > 0) {
            int pick = ThreadLocalRandom.current().nextInt(5);

            switch (pick) {
                case 0 -> { if (speed < STAT_MAX) { speed++; total++; } }
                case 1 -> { if (accel < STAT_MAX) { accel++; total++; } }
                case 2 -> { if (handling < STAT_MAX) { handling++; total++; } }
                case 3 -> { if (stability < STAT_MAX) { stability++; total++; } }
                case 4 -> { if (boost < STAT_MAX) { boost++; total++; } }
            }

            if (speed >= STAT_MAX && accel >= STAT_MAX && handling >= STAT_MAX && stability >= STAT_MAX && boost >= STAT_MAX) {
                break;
            }
        }

        speed = clamp(speed);
        accel = clamp(accel);
        handling = clamp(handling);
        stability = clamp(stability);
        boost = clamp(boost);

        return new MarbleStats(speed, accel, handling, stability, boost);
    }

    private static int roll(int min, int max) {
        int lo = Math.max(STAT_MIN, min);
        int hi = Math.min(STAT_MAX, max);
        if (hi < lo) hi = lo;
        return ThreadLocalRandom.current().nextInt(lo, hi + 1);
    }

    /** Rolls once; with probability `weight` rolls a second time and keeps the better of the two. weight <= 0 is a single unbiased roll, identical to before this catalyst-affinity feature existed. */
    private static int rollAffine(int min, int max, double weight) {
        int a = roll(min, max);
        if (weight <= 0.0) return a;

        int b = roll(min, max);
        double w = Math.min(1.0, weight);
        return (ThreadLocalRandom.current().nextDouble() < w) ? Math.max(a, b) : a;
    }

    private static double weightOf(Map<MarbleStat, Double> statAffinity, MarbleStat stat) {
        if (statAffinity == null) return 0.0;
        Double w = statAffinity.get(stat);
        return w == null ? 0.0 : w;
    }

    private static int clamp(int value) {
        if (value < STAT_MIN) return STAT_MIN;
        if (value > STAT_MAX) return STAT_MAX;
        return value;
    }
}
