package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.marble.MarbleStat;
import me.pattrick.marbledrop.marble.MarbleStats;

/**
 * A race-only tuning tradeoff an entrant picks in the lobby (see
 * RaceLoadoutGui) before their marble actually races. Never touches the
 * marble item's own stored stats -- applyTo() returns a new MarbleStats
 * used just to build that race's MarbleRunner, so the choice only matters
 * for that one race and the marble itself is untouched.
 * <p>
 * Deltas are flat and clamp at the same 0..100 range every other stat
 * lives in, which is deliberate: a marble already near 100 in a stat this
 * loadout boosts gets little or nothing extra from it (clamped away)
 * while still eating the tradeoff's downside in full. So picking the
 * wrong loadout for an already-strong marble can genuinely make it worse,
 * not just "less optimal" -- the choice has to fit both the marble AND
 * the track, not just chase the biggest number.
 */
public enum RaceLoadout {

    AGGRESSIVE(
            "Aggressive",
            "+Speed, +Accel, -Stability. Faster, but corners bite harder.",
            12, 8, 0, -15, 0
    ),
    BALANCED(
            "Balanced",
            "No changes -- race on your marble's raw stats.",
            0, 0, 0, 0, 0
    ),
    DEFENSIVE(
            "Defensive",
            "+Handling, +Stability, -Speed. Steadier, but slower on straights.",
            -10, 0, 10, 12, 0
    );

    private final String label;
    private final String description;
    private final int speedDelta;
    private final int accelDelta;
    private final int handlingDelta;
    private final int stabilityDelta;
    private final int boostDelta;

    RaceLoadout(String label, String description, int speedDelta, int accelDelta,
                int handlingDelta, int stabilityDelta, int boostDelta) {
        this.label = label;
        this.description = description;
        this.speedDelta = speedDelta;
        this.accelDelta = accelDelta;
        this.handlingDelta = handlingDelta;
        this.stabilityDelta = stabilityDelta;
        this.boostDelta = boostDelta;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** A new MarbleStats with this loadout's tradeoffs applied and clamped -- never mutates {@code base}. */
    public MarbleStats applyTo(MarbleStats base) {
        return new MarbleStats(
                clamp(base.get(MarbleStat.SPEED) + speedDelta),
                clamp(base.get(MarbleStat.ACCEL) + accelDelta),
                clamp(base.get(MarbleStat.HANDLING) + handlingDelta),
                clamp(base.get(MarbleStat.STABILITY) + stabilityDelta),
                clamp(base.get(MarbleStat.BOOST) + boostDelta)
        );
    }

    private static int clamp(int v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }
}
