package me.pattrick.marbledrop.progression.infusion;

import me.pattrick.marbledrop.MarbleRarity;

import java.util.Random;

public final class RarityRoller {

    private final Random rng = new Random();

    /**
     * Roll rarity using base weights, boosted by effectiveValue.
     * effectiveValue is "amount spent + hidden attunement".
     */
    public MarbleRarity roll(int effectiveValue) {

        // Base weights (sum doesn't need to be 100)
        double common = 70.0;
        double uncommon = 20.0;
        double rare = 8.0;
        double epic = 1.8;
        double legendary = 0.2;

        // Boost factor: 0.0 to ~1.0+ depending on value
        // Tune: 0.0008 means +0.8 boost per 1000 value.
        double boost = Math.min(1.25, effectiveValue * 0.0008);

        // Shift weight out of common/uncommon into rare+ as boost rises
        // (Keep it simple and safe: never let weights go negative)
        double shift = Math.min(40.0, boost * 20.0); // up to 40 points moved

        // Take most from common, some from uncommon
        double takeFromCommon = Math.min(common - 5.0, shift * 0.7);     // keep at least 5
        double takeFromUncommon = Math.min(uncommon - 2.0, shift * 0.3); // keep at least 2

        common -= takeFromCommon;
        uncommon -= takeFromUncommon;

        double gained = takeFromCommon + takeFromUncommon;

        // Distribute gain into rare/epic/legendary with bias toward rare
        rare += gained * 0.75;
        epic += gained * 0.22;
        legendary += gained * 0.03;

        // Weighted roll
        double total = common + uncommon + rare + epic + legendary;
        double r = rng.nextDouble() * total;

        if ((r -= common) < 0) return MarbleRarity.COMMON;
        if ((r -= uncommon) < 0) return MarbleRarity.UNCOMMON;
        if ((r -= rare) < 0) return MarbleRarity.RARE;
        if ((r -= epic) < 0) return MarbleRarity.EPIC;
        return MarbleRarity.LEGENDARY;
    }
}
