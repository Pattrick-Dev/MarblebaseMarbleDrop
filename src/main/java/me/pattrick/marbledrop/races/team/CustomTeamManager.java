package me.pattrick.marbledrop.races.team;

import me.pattrick.marbledrop.marble.*;
import me.pattrick.marbledrop.progression.infusion.heads.SkullUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

public final class CustomTeamManager {

    public static final int TEAM_SIZE = 5;
    static final int STARTING_STAT = 5;

    private final Plugin plugin;

    private final NamespacedKey kTeamName;
    private final NamespacedKey kTeamColor;
    private final NamespacedKey[] kName    = new NamespacedKey[TEAM_SIZE];
    private final NamespacedKey[] kTexture = new NamespacedKey[TEAM_SIZE];
    private final NamespacedKey[] kIssued  = new NamespacedKey[TEAM_SIZE];

    private final HttpClient http = HttpClient.newHttpClient();

    public CustomTeamManager(Plugin plugin) {
        this.plugin = plugin;
        kTeamName  = key("ct_team_name");
        kTeamColor = key("ct_team_color");
        for (int i = 0; i < TEAM_SIZE; i++) {
            kName[i]    = key("ct_m" + i + "_name");
            kTexture[i] = key("ct_m" + i + "_tex");
            kIssued[i]  = key("ct_m" + i + "_issued");
        }
    }

    // ---- Team info ----

    public boolean hasTeam(Player p) {
        return !getTeamName(p).isEmpty();
    }

    public String getTeamName(Player p) {
        return p.getPersistentDataContainer().getOrDefault(kTeamName, PersistentDataType.STRING, "");
    }

    public String getTeamColor(Player p) {
        return p.getPersistentDataContainer().getOrDefault(kTeamColor, PersistentDataType.STRING, "WHITE");
    }

    public void setTeamName(Player p, String name) {
        p.getPersistentDataContainer().set(kTeamName, PersistentDataType.STRING, name);
    }

    public void setTeamColor(Player p, String colorName) {
        p.getPersistentDataContainer().set(kTeamColor, PersistentDataType.STRING, colorName);
    }

    // ---- Member data ----

    public String getMemberName(Player p, int slot) {
        return p.getPersistentDataContainer().getOrDefault(kName[slot], PersistentDataType.STRING, "");
    }

    public String getMemberTexture(Player p, int slot) {
        return p.getPersistentDataContainer().getOrDefault(kTexture[slot], PersistentDataType.STRING, "");
    }

    public boolean isMemberIssued(Player p, int slot) {
        Byte b = p.getPersistentDataContainer().get(kIssued[slot], PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    public void setMemberName(Player p, int slot, String name) {
        p.getPersistentDataContainer().set(kName[slot], PersistentDataType.STRING, name);
        // name change invalidates any previously issued marble
        setMemberIssued(p, slot, false);
    }

    public void setMemberTexture(Player p, int slot, String base64) {
        p.getPersistentDataContainer().set(kTexture[slot], PersistentDataType.STRING, base64);
    }

    private void setMemberIssued(Player p, int slot, boolean issued) {
        p.getPersistentDataContainer().set(kIssued[slot], PersistentDataType.BYTE, issued ? (byte) 1 : (byte) 0);
    }

    public void resetIssued(Player p, int slot) {
        setMemberIssued(p, slot, false);
    }

    // ---- Marble item ----

    /**
     * Generates a COMMON marble ItemStack with starting stats for this team member.
     * Marks the slot as issued so it won't be given again without an explicit reset.
     * Returns null if the member has no name or the team has no name.
     */
    public ItemStack issueMarble(Player owner, int slot) {
        String teamName = getTeamName(owner);
        String memberName = getMemberName(owner, slot);
        if (teamName.isEmpty() || memberName.isEmpty()) return null;

        String texture = getMemberTexture(owner, slot);

        MarbleStats stats = new MarbleStats(STARTING_STAT, STARTING_STAT, STARTING_STAT, STARTING_STAT, STARTING_STAT);
        MarbleData data = new MarbleData(
                UUID.randomUUID(),
                sanitize(memberName),
                sanitize(teamName),
                MarbleRarity.COMMON,
                stats,
                owner.getUniqueId(),
                System.currentTimeMillis(),
                0, 1
        );

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);

        // Apply texture before writing marble data
        if (!texture.isEmpty()) {
            ItemMeta m = item.getItemMeta();
            if (m instanceof SkullMeta sm) {
                SkullUtil.applyBase64(sm, texture);
                item.setItemMeta(sm);
            }
        }

        // Set display name (syncLore in write() does not override this)
        ItemMeta m = item.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.AQUA + memberName);
            item.setItemMeta(m);
        }

        MarbleItem.write(item, data);
        setMemberIssued(owner, slot, true);
        return item;
    }

    // ---- Texture fetch ----

    /**
     * Async GET to url; expects a plain base64 texture string in the response body.
     * Callbacks fire on the main thread.
     */
    public void fetchTexture(String url, Runnable onFail, Consumer<String> onSuccess) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "MarblebaseDrop/1.0")
                        .GET()
                        .build();

                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

                if (res.statusCode() != 200 || res.body().isBlank()) {
                    plugin.getServer().getScheduler().runTask(plugin, onFail);
                    return;
                }

                String body = res.body().trim();
                plugin.getServer().getScheduler().runTask(plugin, () -> onSuccess.accept(body));

            } catch (IOException | InterruptedException e) {
                plugin.getServer().getScheduler().runTask(plugin, onFail);
            }
        });
    }

    // ---- Helpers ----

    private static String sanitize(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return s.substring(0, Math.min(s.length(), 40));
    }

    private NamespacedKey key(String k) {
        return new NamespacedKey(plugin, k);
    }
}
