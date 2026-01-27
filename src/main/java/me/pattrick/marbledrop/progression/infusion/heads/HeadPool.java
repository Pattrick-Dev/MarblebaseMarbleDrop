package me.pattrick.marbledrop.progression.infusion.heads;

import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class HeadPool {

    private final Plugin plugin;
    private final Random rng = new Random();
    private final List<HeadEntry> entries = new ArrayList<>();

    public HeadPool(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            File data = plugin.getDataFolder();
            if (!data.exists()) data.mkdirs();

            File file = new File(data, "heads.yml");
            if (!file.exists()) {
                // copies from src/main/resources/heads.yml inside your jar
                plugin.saveResource("heads.yml", false);
            }

            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            entries.clear();

            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                HeadEntry parsed = parseLine(line);
                if (parsed != null) entries.add(parsed);
            }

            plugin.getLogger().info("[MarbleDrop] Loaded " + entries.size() + " marble heads.");
        } catch (Exception ex) {
            plugin.getLogger().severe("[MarbleDrop] Failed to load heads.yml: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public boolean isLoaded() {
        return !entries.isEmpty();
    }

    public HeadEntry random() {
        if (entries.isEmpty()) return null;
        return entries.get(rng.nextInt(entries.size()));
    }

    private HeadEntry parseLine(String line) {
        // Your delimiter appears as "==-"
        int idx = line.indexOf("==-");
        int base64EndLen = 2;
        int sepLen = 3;

        // Fallback if any line ends with "=-"
        if (idx < 0) {
            idx = line.indexOf("=-");
            base64EndLen = 1;
            sepLen = 2;
        }

        if (idx < 0) return null;

        String base64 = line.substring(0, idx + base64EndLen);
        String nameTeam = line.substring(idx + sepLen);

        int dash = nameTeam.lastIndexOf('-');
        if (dash < 0) return null;

        String displayName = ChatColor.translateAlternateColorCodes('&', nameTeam.substring(0, dash));
        String team = nameTeam.substring(dash + 1).trim();

        return new HeadEntry(base64, displayName, team);
    }
}
