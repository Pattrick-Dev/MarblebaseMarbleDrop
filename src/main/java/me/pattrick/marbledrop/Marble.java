package me.pattrick.marbledrop;

public class Marble {
    private final String id;          // unique, persistent per item
    private final String displayName; // colored name shown to players
    private final String team;        // team label
    private final MarbleRarity rarity;
    private final MarbleStats stats;

    public Marble(String id, String displayName, String team, MarbleRarity rarity, MarbleStats stats) {
        this.id = id;
        this.displayName = displayName;
        this.team = team;
        this.rarity = rarity;
        this.stats = stats;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String team() { return team; }
    public MarbleRarity rarity() { return rarity; }
    public MarbleStats stats() { return stats; }
}
