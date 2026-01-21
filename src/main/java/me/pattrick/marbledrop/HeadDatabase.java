package me.pattrick.marbledrop;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

public class HeadDatabase {

    public static ItemStack getMarbleHead(String user) throws IOException {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        final ItemMeta baseMeta = head.getItemMeta();
        if (baseMeta == null) {
            return head;
        }

        // base64, display name, team
        final ArrayList<String> sample = Sampler.main();
        final String textureBase64 = sample.get(0);
        final String displayName = sample.get(1);
        final String team = sample.get(2);

        // Roll rarity + base stats
        final MarbleRarity rarity = MarbleRarity.roll();
        final MarbleStats stats = MarbleRoller.rollBaseStats(rarity);

        // Build Marble object (id is random UUID string)
        final Marble marble = new Marble(
                UUID.randomUUID().toString(),
                displayName,
                team,
                rarity,
                stats
        );

        // Display name (unchanged behavior)
        baseMeta.setDisplayName(displayName);

        // Lore:
        // - keep your existing “Marblebase Marble” marker
        // - also show rarity + stats to the player
        baseMeta.setLore(Arrays.asList(
                "§7Team: " + team,
                "§7Rarity: §f" + rarity.displayName() + " §8(Cap " + rarity.statCap() + ")",
                " ",
                "§7Speed: §f" + stats.speed(),
                "§7Control: §f" + stats.control(),
                "§7Momentum: §f" + stats.momentum(),
                "§7Stability: §f" + stats.stability(),
                "§7Luck: §f" + stats.luck(),
                " ",
                "§7Found by: " + user,
                "§7On: " + LocalDate.now(),
                " ",
                "§8Marblebase Marble"
        ));

        // Stamp the REAL data onto the item (PDC)
        MarbleItem.applyToMeta(baseMeta, marble);

        // Apply texture
        if (baseMeta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
            profile.setProperty(new ProfileProperty("textures", textureBase64));

            skullMeta.setPlayerProfile(profile);

            // IMPORTANT: apply the same PDC-stamped meta to the skull meta instance
            // (skullMeta is a meta subclass; it should already contain the same container fields,
            // but we keep it safe by re-applying the marble data if needed)
            MarbleItem.applyToMeta(skullMeta, marble);

            head.setItemMeta(skullMeta);
        } else {
            head.setItemMeta(baseMeta);
        }

        return head;
    }
}
