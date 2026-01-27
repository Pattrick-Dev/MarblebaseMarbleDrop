package me.pattrick.marbledrop.progression.infusion;

import me.pattrick.marbledrop.MarbleRarity;
import me.pattrick.marbledrop.MarbleStats;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public final class StatRoller {

    /**
     * Stats are out of 99.
     * We roll within a rarity-based range, then enforce a rarity-based minimum total.
     */
    public static final int STAT_MIN = 1;
    public static final int STAT_MAX = 99;

    public static ItemStack baseMarbleItem() {
        // Replace with your real marble base item (custom model data / texture / etc.)
        return new ItemStack(Material.SLIME_BALL);
    }

    public static MarbleStats rollStats(MarbleRarity rarity) {

        // Rarity tiers (min/max per stat, plus a minimum total power floor)
        int min;
        int max;
        int minTotal;

        switch (rarity) {
            case COMMON -> {
                min = 5;
                max = 45;
                minTotal = 120;  // out of 495
            }
            case UNCOMMON -> {
                min = 20;
                max = 60;
                minTotal = 200;
            }
            case RARE -> {
                min = 35;
                max = 75;
                minTotal = 280;
            }
            case EPIC -> {
                min = 55;
                max = 90;
                minTotal = 360;
            }
            case LEGENDARY -> {
                min = 70;
                max = 99;
                minTotal = 425;
            }
            default -> {
                min = 5;
                max = 45;
                minTotal = 120;
            }
        }

        int speed = roll(min, max);
        int control = roll(min, max);
        int momentum = roll(min, max);
        int stability = roll(min, max);
        int luck = roll(min, max);

        int total = speed + control + momentum + stability + luck;

        // Enforce minimum total by boosting random stats (never exceeding 99)
        // This preserves variance while preventing "trash" high-rarity rolls.
        int safety = 2000; // just in case; should never be hit
        while (total < minTotal && safety-- > 0) {
            int pick = ThreadLocalRandom.current().nextInt(5);

            switch (pick) {
                case 0 -> {
                    if (speed < STAT_MAX) {
                        speed++;
                        total++;
                    }
                }
                case 1 -> {
                    if (control < STAT_MAX) {
                        control++;
                        total++;
                    }
                }
                case 2 -> {
                    if (momentum < STAT_MAX) {
                        momentum++;
                        total++;
                    }
                }
                case 3 -> {
                    if (stability < STAT_MAX) {
                        stability++;
                        total++;
                    }
                }
                case 4 -> {
                    if (luck < STAT_MAX) {
                        luck++;
                        total++;
                    }
                }
            }

            // If everything is maxed (should be impossible for these totals), break to avoid an infinite loop.
            if (speed >= STAT_MAX && control >= STAT_MAX && momentum >= STAT_MAX && stability >= STAT_MAX && luck >= STAT_MAX) {
                break;
            }
        }

        // Final clamp safety
        speed = clamp(speed);
        control = clamp(control);
        momentum = clamp(momentum);
        stability = clamp(stability);
        luck = clamp(luck);

        return new MarbleStats(speed, control, momentum, stability, luck);
    }

    private static int roll(int min, int max) {
        int lo = Math.max(STAT_MIN, min);
        int hi = Math.min(STAT_MAX, max);
        if (hi < lo) hi = lo;
        return ThreadLocalRandom.current().nextInt(lo, hi + 1);
    }

    private static int clamp(int value) {
        if (value < STAT_MIN) return STAT_MIN;
        if (value > STAT_MAX) return STAT_MAX;
        return value;
    }
}
