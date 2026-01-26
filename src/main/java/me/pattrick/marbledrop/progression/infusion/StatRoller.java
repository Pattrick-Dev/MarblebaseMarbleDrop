package me.pattrick.marbledrop.progression.infusion;

import me.pattrick.marbledrop.MarbleRarity;
import me.pattrick.marbledrop.MarbleStats;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public final class StatRoller {

    private static final Random rng = new Random();

    public static ItemStack baseMarbleItem() {
        // Replace with your real marble base item (custom model data / texture / etc.)
        return new ItemStack(Material.SLIME_BALL);
    }

    public static MarbleStats rollStats(MarbleRarity rarity) {
        // Replace with real stat caps/roll rules later.
        // For now: higher rarity = slightly higher range.
        int base = switch (rarity) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 3;
            case EPIC -> 4;
            case LEGENDARY -> 5;
        };

        return new MarbleStats(
                roll(base, base + 5),
                roll(base, base + 5),
                roll(base, base + 5),
                roll(base, base + 5),
                roll(base, base + 5)
        );
    }

    private static int roll(int min, int max) {
        return rng.nextInt((max - min) + 1) + min;
    }
}
