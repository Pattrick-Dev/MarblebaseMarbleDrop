package me.pattrick.marbledrop.marble;

import java.util.UUID;

public final class MarbleData {
    public static final int SCHEMA_VERSION = 1;

    private final UUID id;
    private final String marbleKey;     // e.g. "minty_flav"
    private final String teamKey;       // e.g. "minty_maniacs"
    private final MarbleRarity rarity;

    private final MarbleStats stats;

    private final UUID foundBy;         // player UUID
    private final long createdAt;       // epoch millis

    private final int xp;               // optional now
    private final int level;            // optional now

    public MarbleData(
            UUID id,
            String marbleKey,
            String teamKey,
            MarbleRarity rarity,
            MarbleStats stats,
            UUID foundBy,
            long createdAt,
            int xp,
            int level
    ) {
        this.id = id;
        this.marbleKey = marbleKey;
        this.teamKey = teamKey;
        this.rarity = rarity;
        this.stats = stats;
        this.foundBy = foundBy;
        this.createdAt = createdAt;
        this.xp = xp;
        this.level = level;
    }

    public UUID getId() { return id; }
    public String getMarbleKey() { return marbleKey; }
    public String getTeamKey() { return teamKey; }
    public MarbleRarity getRarity() { return rarity; }
    public MarbleStats getStats() { return stats; }
    public UUID getFoundBy() { return foundBy; }
    public long getCreatedAt() { return createdAt; }
    public int getXp() { return xp; }
    public int getLevel() { return level; }
}
