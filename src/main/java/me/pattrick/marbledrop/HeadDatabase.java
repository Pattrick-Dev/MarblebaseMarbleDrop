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

        // Your existing data source (base64, display name, team)
        final ArrayList<String> sample = Sampler.main();

        // Display name (unchanged behavior)
        baseMeta.setDisplayName(sample.get(1));

        // Lore (unchanged behavior)
        baseMeta.setLore(Arrays.asList(
                "§7Team: " + sample.get(2),
                " ",
                "§7Found by: " + user,
                "§7On: " + LocalDate.now(),
                " ",
                "§8Marblebase Marble"
        ));

        // Apply texture using Paper's supported PlayerProfile API (no authlib, no reflection)
        if (baseMeta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
            profile.setProperty(new ProfileProperty("textures", sample.get(0)));

            skullMeta.setPlayerProfile(profile);
            head.setItemMeta(skullMeta);
        } else {
            // Fallback (shouldn't happen for PLAYER_HEAD, but keeps it safe)
            head.setItemMeta(baseMeta);
        }

        return head;
    }
}
