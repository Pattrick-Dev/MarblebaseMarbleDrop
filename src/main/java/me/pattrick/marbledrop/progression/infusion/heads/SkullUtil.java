package me.pattrick.marbledrop.progression.infusion.heads;

import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkullUtil {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"(.*?)\"");

    private SkullUtil() {}

    public static void applyBase64(SkullMeta meta, String base64) {
        try {
            String b64 = base64.trim();

            // Some sources accidentally include a prefix like "textures:" or quotes; strip obvious wrappers
            if (b64.startsWith("\"") && b64.endsWith("\"") && b64.length() > 2) {
                b64 = b64.substring(1, b64.length() - 1);
            }

            String decoded = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);

            Matcher m = URL_PATTERN.matcher(decoded);
            if (!m.find()) {
                throw new IllegalArgumentException("Decoded base64 did not contain a texture url");
            }

            String urlStr = m.group(1);

            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            profile.getTextures().setSkin(new URL(urlStr));
            meta.setOwnerProfile(profile);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply skull texture", e);
        }
    }
}
