package me.pattrick.marbledrop.marble;

public enum MarbleRarity {
    COMMON(60, 250),
    UNCOMMON(68, 280),
    RARE(75, 310),
    EPIC(82, 340),
    LEGENDARY(90, 370);

    private final int perStatCap;
    private final int totalCap;

    MarbleRarity(int perStatCap, int totalCap) {
        this.perStatCap = perStatCap;
        this.totalCap = totalCap;
    }

    public int getPerStatCap() {
        return perStatCap;
    }

    public int getTotalCap() {
        return totalCap;
    }
}
