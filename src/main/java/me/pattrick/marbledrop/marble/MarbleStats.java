package me.pattrick.marbledrop.marble;

import java.util.EnumMap;
import java.util.Map;

public final class MarbleStats {
    private final EnumMap<MarbleStat, Integer> values = new EnumMap<>(MarbleStat.class);

    public MarbleStats(int speed, int accel, int handling, int stability, int boost) {
        values.put(MarbleStat.SPEED, speed);
        values.put(MarbleStat.ACCEL, accel);
        values.put(MarbleStat.HANDLING, handling);
        values.put(MarbleStat.STABILITY, stability);
        values.put(MarbleStat.BOOST, boost);
    }

    public int get(MarbleStat stat) {
        return values.getOrDefault(stat, 0);
    }

    public void set(MarbleStat stat, int value) {
        values.put(stat, value);
    }

    public int total() {
        int sum = 0;
        for (int v : values.values()) sum += v;
        return sum;
    }

    public Map<MarbleStat, Integer> asMap() {
        return new EnumMap<>(values);
    }
}
