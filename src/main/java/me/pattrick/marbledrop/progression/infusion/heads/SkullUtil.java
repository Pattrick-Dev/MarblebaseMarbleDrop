package me.pattrick.marbledrop.progression.infusion.heads;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class SkullUtil {

    private SkullUtil() {}

    /**
     * Apply base64 texture using the SAME system as HeadDatabase:
     * Paper PlayerProfile + ProfileProperty("textures", base64).
     *
     * IMPORTANT: We use a stable UUID derived from the base64 so
     * the client can cache each texture reliably.
     */
    public static void applyBase64(SkullMeta meta, String base64) {
        if (meta == null) throw new IllegalArgumentException("meta is null");
        if (base64 == null) throw new IllegalArgumentException("base64 is null");

        String b64 = base64.trim();

        // Strip obvious wrappers (quotes)
        if (b64.startsWith("\"") && b64.endsWith("\"") && b64.length() > 2) {
            b64 = b64.substring(1, b64.length() - 1);
        }

        if (b64.isEmpty()) {
            throw new IllegalArgumentException("base64 is empty");
        }

        // Stable UUID per texture helps caching & prevents “everything looks the same”
        UUID id = UUID.nameUUIDFromBytes(b64.getBytes(StandardCharsets.UTF_8));

        PlayerProfile profile = Bukkit.createProfile(id, null);
        profile.setProperty(new ProfileProperty("textures", b64));
        meta.setPlayerProfile(profile);
    }
}
