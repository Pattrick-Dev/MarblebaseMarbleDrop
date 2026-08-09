package me.pattrick.marbledrop.marble;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Selectable cosmetic particle effects a placed marble can show (see
 * MarbleDisplayMenu's style picker, MarbleKeys#PARTICLE_STYLE). Purely
 * visual -- none of these touch gameplay, stats, or races. Stored on the
 * block's PDC by enum name(), so reordering is safe but renaming a
 * constant will fall back existing marbles to RARITY_DUST (see byId()).
 */
public enum MarbleParticleStyle {

    RARITY_DUST("Rarity Sparkle", Material.REDSTONE) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            Particle.DustOptions dust = new Particle.DustOptions(rarityColor(rarity), 1.0f);
            w.spawnParticle(Particle.DUST, center, 3, 0.2, 0.2, 0.2, 0, dust);
        }
    },
    ENCHANT_SWIRL("Enchant Swirl", Material.ENCHANTED_BOOK) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.ENCHANT, center, 4, 0.3, 0.3, 0.3, 0.5);
        }
    },
    PORTAL_RIFT("Portal Rift", Material.ENDER_PEARL) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.PORTAL, center, 4, 0.25, 0.25, 0.25, 0.3);
        }
    },
    FLAME_WISP("Flame Wisp", Material.BLAZE_POWDER) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.FLAME, center, 2, 0.15, 0.15, 0.15, 0.01);
        }
    },
    SOUL_FIRE("Soul Fire", Material.SOUL_TORCH) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 2, 0.15, 0.15, 0.15, 0.01);
        }
    },
    END_ROD_SPARKLE("End Rod Sparkle", Material.END_ROD) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.END_ROD, center, 2, 0.15, 0.2, 0.15, 0.01);
        }
    },
    DRAGON_BREATH("Dragon Breath", Material.DRAGON_BREATH) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            // Unlike most simple particles, DRAGON_BREATH requires a Float data arg (its getDataType() is Float.class).
            w.spawnParticle(Particle.DRAGON_BREATH, center, 2, 0.2, 0.1, 0.2, 0.005, 1.0f);
        }
    },
    WITCHCRAFT("Witchcraft", Material.BREWING_STAND) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.WITCH, center, 3, 0.2, 0.2, 0.2, 0.01);
        }
    },
    HEART_BURST("Heart Burst", Material.RED_DYE) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.HEART, center, 1, 0.2, 0.2, 0.2, 0);
        }
    },
    SNOWFALL("Snowfall", Material.SNOWBALL) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.SNOWFLAKE, center, 3, 0.25, 0.25, 0.25, 0.02);
        }
    },
    GLOW_SPORES("Glow Spores", Material.GLOW_INK_SAC) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.GLOW, center, 3, 0.2, 0.2, 0.2, 0.01);
        }
    },
    CLOUD_PUFF("Cloud Puff", Material.WHITE_WOOL) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.CLOUD, center, 2, 0.2, 0.1, 0.2, 0.01);
        }
    },
    FIREWORK_SPARKLE("Firework Sparkle", Material.FIREWORK_ROCKET) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.FIREWORK, center, 3, 0.2, 0.2, 0.2, 0.02);
        }
    },
    CRIT_SPARKLE("Crit Sparkle", Material.IRON_SWORD) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.CRIT, center, 3, 0.2, 0.2, 0.2, 0.1);
        }
    },
    ELECTRIC_SPARK("Electric Spark", Material.LIGHTNING_ROD) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.ELECTRIC_SPARK, center, 3, 0.2, 0.2, 0.2, 0.05);
        }
    },
    SCULK_WHISPER("Sculk Whisper", Material.ECHO_SHARD) {
        @Override
        public void spawn(World w, Location center, MarbleRarity rarity) {
            w.spawnParticle(Particle.SCULK_SOUL, center, 2, 0.2, 0.2, 0.2, 0.01);
        }
    };

    private final String displayName;
    private final Material icon;

    MarbleParticleStyle(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public abstract void spawn(World w, Location center, MarbleRarity rarity);

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public MarbleParticleStyle next() {
        MarbleParticleStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public MarbleParticleStyle previous() {
        MarbleParticleStyle[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }

    public static MarbleParticleStyle byId(String id) {
        if (id == null) return RARITY_DUST;
        try {
            return valueOf(id);
        } catch (IllegalArgumentException e) {
            return RARITY_DUST;
        }
    }

    static Color rarityColor(MarbleRarity rarity) {
        if (rarity == null) return Color.WHITE;
        return switch (rarity) {
            case COMMON -> Color.WHITE;
            case UNCOMMON -> Color.LIME;
            case RARE -> Color.AQUA;
            case EPIC -> Color.FUCHSIA;
            case LEGENDARY -> Color.ORANGE;
        };
    }
}
