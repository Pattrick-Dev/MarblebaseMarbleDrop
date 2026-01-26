package me.pattrick.marbledrop.progression.infusion.table;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public final class InfusionTableProcess {

    private InfusionTableProcess() {}

    public static void run(Player player, Block cauldron, ItemStack marble) {
        World w = cauldron.getWorld();
        Location base = cauldron.getLocation().add(0.5, 1.0, 0.5);
        Location standLoc = cauldron.getLocation().add(0.5, 1.25, 0.5);

        // Spawn invisible display stand
        ArmorStand stand = w.spawn(standLoc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            as.setSmall(true);
            as.setInvulnerable(true);
            as.getEquipment().setHelmet(marble);
        });

        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 1f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!stand.isValid()) {
                    cancel();
                    return;
                }

                // swirl particles
                w.spawnParticle(Particle.ENCHANT, base, 12, 0.25, 0.25, 0.25, 0.02);
                w.spawnParticle(Particle.PORTAL, base, 8, 0.2, 0.2, 0.2, 0.02);

                // occasional sound
                if (ticks % 10 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
                }

                ticks += 5;

                // finish at ~2 seconds
                if (ticks >= 40) {
                    w.spawnParticle(Particle.FIREWORK, base, 1, 0, 0, 0, 0);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.8f);

                    // Give marble
                    if (player.getInventory().firstEmpty() == -1) {
                        w.dropItemNaturally(player.getLocation(), marble);
                    } else {
                        player.getInventory().addItem(marble);
                    }

                    stand.remove();
                    cancel();
                }
            }
        }.runTaskTimer(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(InfusionTableProcess.class), 0L, 5L);
    }
}
