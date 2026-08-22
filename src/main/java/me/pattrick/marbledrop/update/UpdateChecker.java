package me.pattrick.marbledrop.update;

import me.pattrick.marbledrop.MdConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the GitHub repo's latest Release against the version this jar was
 * built as (plugin.yml's version, filtered from pom.xml at build time by
 * the release workflow - see .github/workflows/release.yml) whenever an op
 * logs in, rather than on a timer - an op joining is already the moment
 * someone able to act on it is around, so there's no point polling GitHub
 * while no op is online to see/restart for it. When the release is newer,
 * downloads its jar straight into Bukkit's own update folder
 * ({@link org.bukkit.Server#getUpdateFolderFile()}, normally
 * plugins/update/) under this jar's own file name - Paper's plugin loader
 * automatically swaps it in on the NEXT server restart on its own. This
 * class never touches the currently-running jar or classloader and never
 * attempts a live hot-swap - that's not something Bukkit/Paper reliably
 * supports, so it only ever stages, never hot-reloads.
 */
public final class UpdateChecker implements Listener {

    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/Pattrick-Dev/MarblebaseMarbleDrop/releases/latest";
    private static final String RELEASE_DOWNLOAD_BASE =
            "https://github.com/Pattrick-Dev/MarblebaseMarbleDrop/releases/download/";
    // Must match pom.xml's <finalName> + ".jar", and the workflow's uploaded asset name.
    private static final String JAR_ASSET_NAME = "MarblebaseDrop.jar";
    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    private final JavaPlugin plugin;
    private final MdConfig config;
    private final String jarFileName;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    // Separate client for the actual asset download - GitHub release assets
    // 302-redirect to the real object storage URL, which the API-check
    // client above has no need to ever follow.
    private final HttpClient downloadHttp = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Non-null once a newer jar has actually been downloaded and staged for
    // the next restart - lets an op who joins after it's already staged
    // (by an earlier op's join) skip straight to the "already staged"
    // notice instead of re-checking GitHub (see onPlayerJoin()).
    private volatile String stagedVersion;

    public UpdateChecker(JavaPlugin plugin, MdConfig config, String jarFileName) {
        this.plugin = plugin;
        this.config = config;
        this.jarFileName = jarFileName;
    }

    /** The version currently running (plugin.yml's version, filtered from pom.xml at build time). See /md version. */
    public String runningVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Non-null once a newer jar has been downloaded and staged for the next restart - see /md version. */
    public String stagedVersion() {
        return stagedVersion;
    }

    /** Manual trigger - see /md update. Runs off-thread and reports back to the sender when done. */
    public void checkNowFor(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Result result = doCheck();
            Bukkit.getScheduler().runTask(plugin, () -> report(sender, result));
        });
    }

    private record Result(boolean justStaged, boolean alreadyStaged, boolean upToDate, String remoteVersion, String error) {}

    private Result doCheck() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(LATEST_RELEASE_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MarblebaseDrop-UpdateChecker")
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) {
                return new Result(false, false, true, null, null); // no releases published yet
            }
            if (res.statusCode() != 200) {
                return new Result(false, false, false, null, "HTTP " + res.statusCode());
            }

            Matcher m = TAG_PATTERN.matcher(res.body());
            if (!m.find()) {
                return new Result(false, false, false, null, "no tag_name in response");
            }
            String tag = m.group(1);
            String remoteVersion = tag.replaceFirst("^v", "");
            String localVersion = plugin.getDescription().getVersion();

            if (compareVersions(remoteVersion, localVersion) <= 0) {
                return new Result(false, false, true, remoteVersion, null);
            }
            if (remoteVersion.equals(stagedVersion)) {
                return new Result(false, true, false, remoteVersion, null);
            }

            downloadAndStage(RELEASE_DOWNLOAD_BASE + tag + "/" + JAR_ASSET_NAME);
            stagedVersion = remoteVersion;
            return new Result(true, false, false, remoteVersion, null);
        } catch (Exception e) {
            return new Result(false, false, false, null, e.getMessage());
        }
    }

    private void downloadAndStage(String downloadUrl) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "MarblebaseDrop-UpdateChecker")
                .GET()
                .build();

        HttpResponse<byte[]> res = downloadHttp.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) {
            throw new IOException("download failed: HTTP " + res.statusCode());
        }

        File updateFolder = plugin.getServer().getUpdateFolderFile();
        if (!updateFolder.exists()) updateFolder.mkdirs();

        Path dest = updateFolder.toPath().resolve(jarFileName);
        Files.write(dest, res.body());
    }

    private void report(CommandSender sender, Result result) {
        if (result.error() != null) {
            sender.sendMessage(ChatColor.RED + "Update check failed: " + result.error());
        } else if (result.justStaged()) {
            sender.sendMessage(ChatColor.GREEN + "Update staged: v" + result.remoteVersion()
                    + " will apply on the next server restart.");
        } else if (result.alreadyStaged()) {
            sender.sendMessage(ChatColor.YELLOW + "v" + result.remoteVersion()
                    + " is already staged - restart the server to apply it.");
        } else if (result.upToDate()) {
            sender.sendMessage(ChatColor.GRAY + "Already up to date"
                    + (result.remoteVersion() != null ? " (latest: v" + result.remoteVersion() + ")." : " (no releases published yet)."));
        }
    }

    private void notifyOnlineOps(String remoteVersion) {
        String msg = ChatColor.GREEN + "[MarbleDrop] Update v" + remoteVersion + " downloaded - restart the server to apply it.";
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp()) p.sendMessage(msg);
        }
    }

    /**
     * Runs the update check whenever an op logs in - this is the ONLY
     * trigger now (see class javadoc for why there's no timer). If a newer
     * release was already staged by an earlier op's join, this skips
     * straight to notifying instead of hitting GitHub again.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!config.updateCheckerEnabled()) return;
        Player p = e.getPlayer();
        if (!p.isOp()) return;

        if (stagedVersion != null) {
            if (config.updateCheckerNotifyOps()) {
                p.sendMessage(ChatColor.GREEN + "[MarbleDrop] Update v" + stagedVersion + " is staged - restart the server to apply it.");
            }
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Result result = doCheck();
            if (result.error() != null) {
                plugin.getLogger().warning("[UpdateChecker] Check failed: " + result.error());
            } else if (result.justStaged()) {
                plugin.getLogger().info("[UpdateChecker] Staged v" + result.remoteVersion()
                        + " - it'll apply on the next server restart.");
                if (config.updateCheckerNotifyOps()) {
                    Bukkit.getScheduler().runTask(plugin, () -> notifyOnlineOps(result.remoteVersion()));
                }
            }
        });
    }

    /**
     * Dot-separated numeric version compare - each component's leading
     * digits are compared, any non-digit suffix (e.g. the "-SNAPSHOT" in a
     * local dev build's "0.0.1-SNAPSHOT") is stripped and ignored. Positive
     * if a > b, 0 if equal, negative if a < b.
     */
    static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = parseComponent(i < pa.length ? pa[i] : "0");
            int vb = parseComponent(i < pb.length ? pb[i] : "0");
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int parseComponent(String s) {
        Matcher m = Pattern.compile("\\d+").matcher(s);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
