package me.pattrick.marbledrop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import me.pattrick.marbledrop.marble.MarbleKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

public class HeadDatabase {

    /**
     * Returns a PLAYER_HEAD with:
     * - skull texture set from heads.yml (base64)
     * - display name set from heads.yml (name)
     * - team written to MODERN PDC (MarbleKeys.TEAM_KEY) so InfusionService can read it
     *
     * IMPORTANT:
     * - NO stats rolling here
     * - NO rarity/stats PDC here
     *
     * InfusionService / MarbleItem.write(...) handles the full schema.
     */
    public static ItemStack getMarbleHead(String user) throws IOException {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);

        final ItemMeta meta = head.getItemMeta();
        if (meta == null) return head;

        // Sampler returns [base64, name, team]
        final ArrayList<String> sample = Sampler.main();
        final String textureBase64 = safeGet(sample, 0);
        final String displayName  = safeGet(sample, 1);
        final String team         = safeGet(sample, 2);

        // Always keep a visible name
        if (displayName != null && !displayName.isEmpty()) {
            meta.setDisplayName(displayName);
        }

        // ✅ Write team to MODERN PDC so InfusionService doesn't default to Neutral
        if (team != null && !team.isEmpty() && MarbleKeys.TEAM_KEY != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(MarbleKeys.TEAM_KEY, PersistentDataType.STRING, team);
        }

        // Apply texture
        if (meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);

            if (textureBase64 != null && !textureBase64.isEmpty()) {
                profile.setProperty(new ProfileProperty("textures", textureBase64));
            }

            skullMeta.setPlayerProfile(profile);

            // Ensure the name survives the cast
            if (displayName != null && !displayName.isEmpty()) {
                skullMeta.setDisplayName(displayName);
            }

            // ✅ Team must be written on the actual meta we set onto the item
            if (team != null && !team.isEmpty() && MarbleKeys.TEAM_KEY != null) {
                PersistentDataContainer pdc = skullMeta.getPersistentDataContainer();
                pdc.set(MarbleKeys.TEAM_KEY, PersistentDataType.STRING, team);
            }

            head.setItemMeta(skullMeta);
        } else {
            head.setItemMeta(meta);
        }

        return head;
    }

    private static String safeGet(ArrayList<String> list, int idx) {
        if (list == null) return null;
        if (idx < 0 || idx >= list.size()) return null;
        return list.get(idx);
    }
}
