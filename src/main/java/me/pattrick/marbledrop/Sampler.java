package me.pattrick.marbledrop;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Sampler {

    /**
     * Returns [base64, name, team] from the formatted heads.yml:
     *
     * heads:
     *   1:
     *     base64: ...
     *     name: §e§lYellim
     *     team: Mellow Yellow
     */
    public static ArrayList<String> main() throws IOException {
        return main(null);
    }

    /**
     * Same as {@link #main()}, but when preferredTeam is non-null, with
     * probability infusion.catalyst.team-bias-chance (MdConfig) the pick
     * is filtered down to just that team's heads first - a soft skew,
     * not a guarantee, and it falls back to the full uniform pool if the
     * coin flip fails or that team has no entries in heads.yml.
     */
    public static ArrayList<String> main(String preferredTeam) throws IOException {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MarbleBaseMD");
        if (plugin == null) {
            throw new IOException("Plugin 'MarbleBaseMD' not found (Sampler cannot locate data folder).");
        }

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IOException("Could not create plugin data folder: " + dataFolder.getAbsolutePath());
        }

        // We are now using REAL YAML
        File file = new File(dataFolder, "heads.yml");
        if (!file.exists()) {
            throw new IOException("heads.yml not found in: " + file.getAbsolutePath());
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection heads = yml.getConfigurationSection("heads");
        if (heads == null) {
            throw new IOException("heads.yml is missing top-level 'heads:' section.");
        }

        List<String> keys = new ArrayList<>(heads.getKeys(false));
        if (keys.isEmpty()) {
            throw new IOException("heads.yml has an empty 'heads:' section.");
        }

        List<String> candidateKeys = keys;
        if (preferredTeam != null && !preferredTeam.isBlank()) {
            double chance = (plugin instanceof me.pattrick.marbledrop.Main m) ? m.cfg().catalystTeamBiasChance() : 0.5;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                String wanted = preferredTeam.trim();
                List<String> matching = new ArrayList<>();
                for (String k : keys) {
                    String team = heads.getString(k + ".team");
                    if (team != null && team.trim().equalsIgnoreCase(wanted)) {
                        matching.add(k);
                    }
                }
                if (!matching.isEmpty()) candidateKeys = matching;
            }
        }

        // pick random key ("1", "2", "3", ...) from the (possibly team-filtered) candidates
        String key = candidateKeys.get(ThreadLocalRandom.current().nextInt(candidateKeys.size()));

        String base64 = heads.getString(key + ".base64");
        String name = heads.getString(key + ".name");
        String team = heads.getString(key + ".team");

        if (base64 == null || base64.isEmpty()) {
            throw new IOException("heads.yml entry heads." + key + ".base64 is missing/empty.");
        }
        if (name == null) name = "§fMarble";
        if (team == null) team = "Neutral";

        // Optional debug:
        // Bukkit.getConsoleSender().sendMessage("[Sampler] Picked head #" + key + " name=" + name + " team=" + team);

        ArrayList<String> out = new ArrayList<>();
        out.add(base64);
        out.add(name);
        out.add(team);
        return out;
    }
}
