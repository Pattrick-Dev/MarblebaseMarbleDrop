package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.marble.MarbleData;
import me.pattrick.marbledrop.marble.MarbleItem;
import me.pattrick.marbledrop.marble.MarbleStat;
import me.pattrick.marbledrop.marble.MarbleStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;

public final class RaceManager {

    public static final int MAX_ENTRIES_PER_TRACK = 16;

    private final TrackManager tracks;
    private final MarbleRaceEngine engine;

    // trackId -> active session
    private final Map<String, RaceSession> active = new HashMap<>();

    // trackId -> lobby entries
    private final Map<String, List<RaceEntry>> lobby = new HashMap<>();

    public RaceManager(TrackManager tracks, MarbleRaceEngine engine) {
        this.tracks = tracks;
        this.engine = engine;
    }

    public void enter(Player player, String trackId, ItemStack marbleItem) {
        if (player == null) return;
        if (trackId == null || trackId.isBlank()) return;

        trackId = trackId.toLowerCase();

        if (active.containsKey(trackId)) {
            player.sendMessage(ChatColor.RED + "A race is already running on '" + trackId + "'.");
            return;
        }

        MarbleTrack track = tracks.getTrack(trackId);
        if (track == null || track.size() < 2) {
            player.sendMessage(ChatColor.RED + "Track not found or not enough points.");
            return;
        }

        if (!MarbleItem.isMarble(marbleItem)) {
            player.sendMessage(ChatColor.RED + "Hold a marble in your main hand first.");
            return;
        }

        MarbleData data = MarbleItem.read(marbleItem);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "Could not read marble data.");
            return;
        }

        UUID marbleId = data.getId();

        List<RaceEntry> list = lobby.computeIfAbsent(trackId, k -> new ArrayList<>());

        if (list.size() >= MAX_ENTRIES_PER_TRACK) {
            player.sendMessage(ChatColor.RED + "That race is full (" + MAX_ENTRIES_PER_TRACK + ").");
            return;
        }

        // prevent duplicate marble IDs in same lobby
        for (RaceEntry e : list) {
            if (e.marbleId.equals(marbleId)) {
                player.sendMessage(ChatColor.RED + "That marble is already entered on this track.");
                return;
            }
        }

        // one entry per player per track
        for (RaceEntry e : list) {
            if (e.owner.equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You are already entered on this track. Use /md race leave " + trackId);
                return;
            }
        }

        // Snapshot helmet visual (the marble item itself)
        ItemStack helmet = marbleItem.clone();
        helmet.setAmount(1);

        // Capture display name (fallback if none)
        String marbleDisplayName = getMarbleDisplayName(helmet);

        // Stats-driven speed baseline
        double speedPerTick = computeSpeedPerTick(data);

        // Option C: stats also drive HOW chaotic / aggressive the marble behaves
        double chaos = computeChaos(data);           // lower = cleaner lines, fewer bad hits
        double aggression = computeAggression(data); // higher = more pass attempts / bumping

        list.add(new RaceEntry(player.getUniqueId(), marbleId, helmet, data, marbleDisplayName, speedPerTick, chaos, aggression));

        player.sendMessage(ChatColor.GREEN + "Entered your marble into track '" + trackId + "'.");
        player.sendMessage(ChatColor.GRAY + "Entries: " + list.size() + "/" + MAX_ENTRIES_PER_TRACK);
    }

    public void leave(Player player, String trackId) {
        if (player == null) return;
        if (trackId == null || trackId.isBlank()) return;

        trackId = trackId.toLowerCase();

        if (active.containsKey(trackId)) {
            player.sendMessage(ChatColor.RED + "That race already started. You can't leave now.");
            return;
        }

        List<RaceEntry> list = lobby.get(trackId);

        if (list == null || list.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No entries exist for that track.");
            return;
        }

        boolean removed = list.removeIf(e -> e.owner.equals(player.getUniqueId()));
        if (removed) {
            player.sendMessage(ChatColor.YELLOW + "Removed your entry from '" + trackId + "'.");
            if (list.isEmpty()) lobby.remove(trackId);
        } else {
            player.sendMessage(ChatColor.RED + "You are not entered on that track.");
        }
    }

    public void clear(String trackId) {
        if (trackId == null) return;
        trackId = trackId.toLowerCase();

        if (active.containsKey(trackId)) {
            // we won't kill a running race in this KISS version
            return;
        }

        lobby.remove(trackId);
    }

    public void listToPlayer(Player viewer, String trackId) {
        if (viewer == null) return;
        if (trackId == null || trackId.isBlank()) return;

        trackId = trackId.toLowerCase();

        List<RaceEntry> list = lobby.get(trackId);
        int count = (list == null) ? 0 : list.size();

        viewer.sendMessage(ChatColor.YELLOW + "Race entries for '" + trackId + "': " + ChatColor.WHITE + count);

        if (list == null || list.isEmpty()) return;

        for (RaceEntry e : list) {
            String name = viewer.getServer().getOfflinePlayer(e.owner).getName();
            if (name == null) name = e.owner.toString();

            viewer.sendMessage(ChatColor.GRAY + "- " + name
                    + ChatColor.DARK_GRAY + " | "
                    + ChatColor.AQUA + (e.marbleDisplayName != null ? e.marbleDisplayName : e.marbleId.toString()));
        }
    }

    public void start(Player starter, String trackId) {
        if (starter == null) return;
        if (trackId == null || trackId.isBlank()) return;

        trackId = trackId.toLowerCase();
        final String finalTrackId = trackId;

        if (active.containsKey(trackId)) {
            starter.sendMessage(ChatColor.RED + "A race is already running on '" + trackId + "'.");
            return;
        }

        MarbleTrack track = tracks.getTrack(trackId);
        if (track == null || track.size() < 2) {
            starter.sendMessage(ChatColor.RED + "Track not found or not enough points.");
            return;
        }

        List<RaceEntry> list = lobby.get(trackId);
        if (list == null || list.isEmpty()) {
            starter.sendMessage(ChatColor.RED + "No entries for that track.");
            return;
        }

        // create active session (stores recipients)
        RaceSession session = new RaceSession(trackId, starter.getUniqueId(), list);
        active.put(trackId, session);

        Location start = track.getPoint(0).clone();

        starter.sendMessage(ChatColor.GREEN + "Starting race on '" + trackId + "' with " + list.size() + " marbles...");

        // spread spawn points in a small circle at the start
        double radius = 0.35;
        int n = list.size();

        for (int i = 0; i < n; i++) {
            RaceEntry entry = list.get(i);

            double angle = (Math.PI * 2.0) * (i / (double) n);
            double ox = Math.cos(angle) * radius;
            double oz = Math.sin(angle) * radius;

            Location spawn = start.clone().add(ox, 0.0, oz);

            // ✅ Pass chaos + aggression into MarbleRunner for Option C
            MarbleRunner runner = new MarbleRunner(
                    track,
                    spawn,
                    entry.helmet,
                    entry.speedPerTick,
                    entry.chaos,
                    entry.aggression,
                    () -> onFinish(finalTrackId, entry)
            );

            engine.addRunner(runner);
        }

        // remove lobby entries now that race started
        lobby.remove(trackId);
    }

    private void onFinish(String trackId, RaceEntry entry) {
        RaceSession session = active.get(trackId);
        if (session == null) return;

        // prevent double-finish (defensive)
        if (session.finishedIds.contains(entry.marbleId)) return;

        session.finishedIds.add(entry.marbleId);
        session.finished.add(entry);

        int place = session.finished.size();

        String ownerName = Bukkit.getOfflinePlayer(entry.owner).getName();
        if (ownerName == null) ownerName = entry.owner.toString();

        Component line = Component.text("#" + place + " finished: ", NamedTextColor.GRAY)
                .append(Component.text(ownerName, NamedTextColor.YELLOW))
                .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                .append(buildMarbleNameComponent(entry))
                .append(Component.text(")", NamedTextColor.DARK_GRAY));

        broadcastToSession(session, line);

        if (session.finished.size() >= session.total) {
            broadcastResults(session);
            active.remove(trackId);
        }
    }

    private void broadcastResults(RaceSession session) {
        Component header = Component.text("=== Race Results (" + session.trackId + ") ===", NamedTextColor.GOLD);
        broadcastToSession(session, header);

        for (int i = 0; i < session.finished.size(); i++) {
            RaceEntry e = session.finished.get(i);

            String ownerName = Bukkit.getOfflinePlayer(e.owner).getName();
            if (ownerName == null) ownerName = e.owner.toString();

            NamedTextColor medal = switch (i) {
                case 0 -> NamedTextColor.GOLD;
                case 1 -> NamedTextColor.GRAY;
                case 2 -> NamedTextColor.DARK_RED;
                default -> NamedTextColor.WHITE;
            };

            Component line = Component.text((i + 1) + ". ", medal)
                    .append(Component.text(ownerName, NamedTextColor.YELLOW))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(buildMarbleNameComponent(e));

            broadcastToSession(session, line);
        }
    }

    private void broadcastToSession(RaceSession session, Component msg) {
        if (session == null || msg == null) return;

        for (UUID u : session.recipients) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }

    private Component buildMarbleNameComponent(RaceEntry entry) {
        String name = (entry.marbleDisplayName != null && !entry.marbleDisplayName.isBlank())
                ? entry.marbleDisplayName
                : entry.marbleId.toString();

        Component hover = buildMarbleHover(entry);

        return Component.text(name, NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(hover));
    }

    private Component buildMarbleHover(RaceEntry entry) {
        MarbleData data = entry.data;

        String team = (data.getTeamKey() == null || data.getTeamKey().isBlank()) ? "Neutral" : data.getTeamKey();
        String rarity = (data.getRarity() == null) ? "COMMON" : data.getRarity().name();

        int speed = data.getStats().get(MarbleStat.SPEED);
        int accel = data.getStats().get(MarbleStat.ACCEL);
        int handling = data.getStats().get(MarbleStat.HANDLING);
        int stability = data.getStats().get(MarbleStat.STABILITY);
        int boost = data.getStats().get(MarbleStat.BOOST);

        Component c = Component.empty();

        String title = (entry.marbleDisplayName != null && !entry.marbleDisplayName.isBlank())
                ? entry.marbleDisplayName
                : "Marble";

        c = c.append(Component.text(title, NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
                .append(Component.newline());

        c = c.append(Component.text("Team: ", NamedTextColor.GRAY))
                .append(Component.text(team, NamedTextColor.WHITE))
                .append(Component.newline());

        c = c.append(Component.text("Rarity: ", NamedTextColor.GRAY))
                .append(Component.text(rarity, NamedTextColor.WHITE))
                .append(Component.newline());

        c = c.append(Component.text("Level: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(data.getLevel()), NamedTextColor.WHITE))
                .append(Component.newline());

        c = c.append(Component.text("XP: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(data.getXp()), NamedTextColor.WHITE))
                .append(Component.newline());

        c = c.append(Component.text("----------------", NamedTextColor.DARK_GRAY))
                .append(Component.newline());

        c = c.append(Component.text("Speed: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(speed), NamedTextColor.WHITE)).append(Component.newline());
        c = c.append(Component.text("Accel: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(accel), NamedTextColor.WHITE)).append(Component.newline());
        c = c.append(Component.text("Handling: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(handling), NamedTextColor.WHITE)).append(Component.newline());
        c = c.append(Component.text("Stability: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(stability), NamedTextColor.WHITE)).append(Component.newline());
        c = c.append(Component.text("Boost: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(boost), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("----------------", NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.text("Race Style:", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Chaos: ", NamedTextColor.GRAY)).append(Component.text(String.format(Locale.US, "%.2f", entry.chaos), NamedTextColor.WHITE)).append(Component.newline())
                .append(Component.text("Aggro: ", NamedTextColor.GRAY)).append(Component.text(String.format(Locale.US, "%.2f", entry.aggression), NamedTextColor.WHITE));

        return c;
    }

    private String getMarbleDisplayName(ItemStack item) {
        if (item == null) return "Marble";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "Marble";
        String dn = meta.getDisplayName();
        if (dn == null || dn.isBlank()) return "Marble";
        return ChatColor.stripColor(dn);
    }

    // ------------------------------------------------------------
    // Speed logic (Option C tuned)
    // ------------------------------------------------------------

    private double computeSpeedPerTick(MarbleData data) {
        MarbleStats stats = data.getStats();

        int speed = stats.get(MarbleStat.SPEED);
        int accel = stats.get(MarbleStat.ACCEL);
        int handling = stats.get(MarbleStat.HANDLING);
        int stability = stats.get(MarbleStat.STABILITY);
        int boost = stats.get(MarbleStat.BOOST);

        // baseline similar to what you had
        double base = 0.014;

        // primary performance: SPEED + ACCEL matter most
        double statBoost =
                (speed * 0.00038) +
                        (accel * 0.00030) +
                        (handling * 0.00012);

        // Option C: keep randomness but reduce it, and let STABILITY suppress it
        // lower stability -> more variance (more upsets), higher stability -> more consistent
        double variance = 0.0012; // reduced vs earlier
        double stabilitySuppress = clamp01(stability / 100.0); // assumes typical stat range ~0-100
        double randomness = (Math.random() - 0.5) * (variance * (1.05 - 0.85 * stabilitySuppress));

        // boost: occasional small pop (upsides), but not huge
        double boostChance = clamp01(boost * 0.008); // 0..~0.8 if boost=100 (adjust to your stat ranges)
        double boostBonus = (Math.random() < boostChance) ? 0.00055 : 0;

        double finalSpeed = base + statBoost + randomness + boostBonus;

        if (finalSpeed < 0.010) finalSpeed = 0.010;
        if (finalSpeed > 0.030) finalSpeed = 0.030;

        return finalSpeed;
    }

    // ------------------------------------------------------------
    // Option C behavior mapping: stats -> chaos/aggression
    // ------------------------------------------------------------

    private double computeChaos(MarbleData data) {
        MarbleStats s = data.getStats();

        int stability = s.get(MarbleStat.STABILITY);
        int handling = s.get(MarbleStat.HANDLING);

        // normalize assuming ~0..100 stats; clamp for safety
        double stab = clamp01(stability / 100.0);
        double hand = clamp01(handling / 100.0);

        // higher stability/handling => lower chaos
        // keep a floor so races are still fun
        double chaos = 0.55
                - (stab * 0.28)
                - (hand * 0.18);

        // clamp to a sane range that matches your MarbleRunner expectations
        // (lower chaos => cleaner lines)
        return clamp(chaos, 0.12, 0.65);
    }

    private double computeAggression(MarbleData data) {
        MarbleStats s = data.getStats();

        int boost = s.get(MarbleStat.BOOST);
        int accel = s.get(MarbleStat.ACCEL);
        int speed = s.get(MarbleStat.SPEED);

        double b = clamp01(boost / 100.0);
        double a = clamp01(accel / 100.0);
        double sp = clamp01(speed / 100.0);

        // boost/accel lean aggressive; speed slightly contributes
        double aggro = 0.35
                + (b * 0.30)
                + (a * 0.20)
                + (sp * 0.10);

        return clamp(aggro, 0.20, 0.95);
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    // ------------------------------------------------------------
    // Data
    // ------------------------------------------------------------

    public static final class RaceEntry {
        public final UUID owner;
        public final UUID marbleId;
        public final ItemStack helmet;

        public final MarbleData data;
        public final String marbleDisplayName;

        public final double speedPerTick;

        // Option C: behavior traits passed into MarbleRunner
        public final double chaos;
        public final double aggression;

        public RaceEntry(UUID owner, UUID marbleId, ItemStack helmet,
                         MarbleData data, String marbleDisplayName,
                         double speedPerTick, double chaos, double aggression) {
            this.owner = owner;
            this.marbleId = marbleId;
            this.helmet = helmet;
            this.data = data;
            this.marbleDisplayName = marbleDisplayName;
            this.speedPerTick = speedPerTick;
            this.chaos = chaos;
            this.aggression = aggression;
        }
    }

    private static final class RaceSession {
        final String trackId;
        final int total;

        final List<RaceEntry> finished = new ArrayList<>();
        final Set<UUID> finishedIds = new HashSet<>();

        final Set<UUID> recipients = new HashSet<>();

        RaceSession(String trackId, UUID starter, List<RaceEntry> entries) {
            this.trackId = trackId;
            this.total = entries.size();

            for (RaceEntry e : entries) {
                recipients.add(e.owner);
            }

            if (starter != null) recipients.add(starter);
        }
    }
}
