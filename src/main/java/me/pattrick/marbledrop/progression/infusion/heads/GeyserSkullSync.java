package me.pattrick.marbledrop.progression.infusion.heads;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Writes plugins/Geyser-Spigot/custom_mappings/marbledrop-skulls.json from
 * every base64 texture in heads.yml, so Bedrock players (via Geyser) see
 * the same marble head texture Java players do - including in their
 * inventory - instead of a blank/default head.
 * See https://geysermc.org/wiki/geyser/custom-skulls/.
 * <p>
 * IMPORTANT: this has to go inside Geyser's custom_mappings/ subfolder,
 * NOT its data-folder root - Geyser's MappingsConfigReader only walks
 * custom_mappings/ for *.json files (any filename; it looks for a
 * top-level "skulls" key + "format_version", not a specific name) and
 * silently ignores anything sitting outside it, with no log line either
 * way (confirmed by reading GeyserMC/Geyser's
 * registry/mappings/MappingsConfigReader.java and
 * registry/populator/CustomSkullRegistryPopulator.java).
 * <p>
 * Geyser reads this file and generates its own resource pack from it at
 * Geyser's OWN startup, so this must run before Geyser-Spigot enables -
 * see {@code loadbefore: [Geyser-Spigot]} in plugin.yml. Every base64
 * string already stored in heads.yml is a full base64-encoded game
 * profile (the same thing SkullUtil/HeadDatabase feed into
 * {@code ProfileProperty("textures", ...)}), which is exactly the format
 * Geyser's "profile" registration type expects - so no conversion is
 * needed, only deduplication.
 */
public final class GeyserSkullSync {

    private GeyserSkullSync() {}

    public static void sync(Plugin plugin, HeadPool pool) {
        File geyserDataDir = new File(plugin.getDataFolder().getParentFile(), "Geyser-Spigot");
        if (!geyserDataDir.exists()) {
            return; // Geyser isn't installed on this server - nothing to do
        }
        File mappingsDir = new File(geyserDataDir, "custom_mappings");
        if (!mappingsDir.exists()) {
            mappingsDir.mkdirs();
        }

        Set<String> uniqueBase64 = new LinkedHashSet<>();
        for (HeadEntry entry : pool.all()) {
            uniqueBase64.add(entry.base64());
        }

        if (uniqueBase64.isEmpty()) {
            plugin.getLogger().warning("[MarbleDrop] No marble heads loaded - skipped writing Geyser custom skull mappings.");
            return;
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n  \"format_version\": 1,\n  \"skulls\": {\n    \"profile\": [\n");
        int i = 0;
        for (String base64 : uniqueBase64) {
            json.append("      \"").append(base64).append('"');
            json.append(++i < uniqueBase64.size() ? ",\n" : "\n");
        }
        json.append("    ]\n  }\n}\n");

        File out = new File(mappingsDir, "marbledrop-skulls.json");
        try {
            Files.writeString(out.toPath(), json.toString(), StandardCharsets.UTF_8);
            plugin.getLogger().info("[MarbleDrop] Wrote " + uniqueBase64.size()
                    + " unique marble head texture(s) to " + out.getPath() + " for Geyser.");
        } catch (IOException ex) {
            plugin.getLogger().warning("[MarbleDrop] Failed to write Geyser custom skull mappings: " + ex.getMessage());
        }
    }
}
