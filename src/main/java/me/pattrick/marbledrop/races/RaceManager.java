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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.*;

public final class RaceManager {

    public static final int MAX_ENTRIES_PER_TRACK = 16;

    private final TrackManager tracks;
    private final MarbleRaceEngine engine;

    // ✅ Watch manager (optional)
    private RaceWatchManager watch;

    // trackId -> active session
    private final Map<String, RaceSession> active = new HashMap<>();

    // trackId -> lobby entries
    private final Map<String, List<RaceEntry>> lobby = new HashMap<>();

    // ✅ NEW: trackId -> OPEN state
    private final Set<String> openTracks = new HashSet<>();

    public RaceManager(TrackManager tracks, MarbleRaceEngine engine) {
        this.tracks = tracks;
        this.engine = engine;
    }

    // ✅ allow Main to wire watch manager without redesigning flow
    public void setWatchManager(RaceWatchManager watch) {
        this.watch = watch;
    }

    // ----------------------------
    // ✅ OPEN / CLOSE (Lobby state)
    // ----------------------------

    public boolean open(Player opener, String trackId) {
        if (trackId == null || trackId.isBlank()) return false;
        trackId = trackId.toLowerCase();

        if (active.containsKey(trackId)) {
            if (opener != null) opener.sendMessage(ChatColor.RED + "A race is already running on '" + trackId + "'.");
            return false;
        }

        MarbleTrack track = tracks.getTrack(trackId);
        if (track == null || track.size() < 2) {
            if (opener != null) opener.sendMessage(ChatColor.RED + "Track not found or not enough points.");
            return false;
        }

        openTracks.add(trackId);
        lobby.computeIfAbsent(trackId, k -> new ArrayList<>());

        if (opener != null) {
            opener.sendMessage(ChatColor.GREEN + "Track '" + trackId + "' is now OPEN for entries.");
            opener.sendMessage(ChatColor.GRAY + "Players can join via " + ChatColor.AQUA + "/md race");
        }
        return true;
    }

    public boolean close(Player closer, String trackId) {
        if (trackId == null || trackId.isBlank()) return false;
        trackId = trackId.toLowerCase();

        openTracks.remove(trackId);

        if (closer != null) {
            closer.sendMessage(ChatColor.YELLOW + "Track '" + trackId + "' is now CLOSED for entries.");
        }
        return true;
    }

    public boolean isOpen(String trackId) {
        if (trackId == null) return false;
        return openTracks.contains(trackId.toLowerCase());
    }

    public boolean isRunning(String trackId) {
        if (trackId == null) return false;
        return active.containsKey(trackId.toLowerCase());
    }

    public int lobbyCount(String trackId) {
        if (trackId == null) return 0;
        List<RaceEntry> list = lobby.get(trackId.toLowerCase());
        return list == null ? 0 : list.size();
    }

    public boolean hasEntry(String trackId, UUID playerId) {
        if (trackId == null || playerId == null) return false;
        List<RaceEntry> list = lobby.get(trackId.toLowerCase());
        if (list == null) return false;
        for (RaceEntry e : list) {
            if (playerId.equals(e.owner)) return true;
        }
        return false;
    }

    public List<String> openTrackIds() {
        List<String> out = new ArrayList<>(openTracks);
        Collections.sort(out);
        return out;
    }

    // ----------------------------
    // Entries
    // ----------------------------

    public void enter(Player player, String trackId, ItemStack marbleItem) {
        if (player == null) return;
        if (trackId == null || trackId.isBlank()) return;

        trackId = trackId.toLowerCase();

        // ✅ NEW: must be open to join (this is the “tracks open so players can join” rule)
        if (!isOpen(trackId)) {
            player.sendMessage(ChatColor.RED + "That track is not open for entries.");
            player.sendMessage(ChatColor.GRAY + "Wait for an admin to open it, then use " + ChatColor.AQUA + "/md race");
            return;
        }

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
                player.sendMessage(ChatColor.RED + "You are already entered on this track. Right-click it in /md race to leave.");
                return;
            }
        }

        ItemStack helmet = marbleItem.clone();
        helmet.setAmount(1);

        String marbleDisplayName = getMarbleDisplayName(helmet);

        // Stats-driven speed
        double speedPerTick = computeSpeedPerTick(data);

        // Option C personality (you already wired chaos/aggression in MarbleRunner)
        double chaos = computeChaos(data);
        double aggression = computeAggression(data);

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

        RaceSession session = new RaceSession(trackId, starter.getUniqueId(), list);
        active.put(trackId, session);

        Location start = track.getPoint(0).clone();

        starter.sendMessage(ChatColor.GREEN + "Starting race on '" + trackId + "' with " + list.size() + " marbles...");

        // ✅ AUTO-WATCH: put all entered players into watch mode when race starts
        if (watch != null) {
            for (RaceEntry entry : list) {
                Player owner = Bukkit.getPlayer(entry.owner);
                if (owner != null && owner.isOnline()) {
                    watch.start(owner, trackId);
                }
            }
        }

        double radius = 0.35;
        int n = list.size();

        for (int i = 0; i < n; i++) {
            RaceEntry entry = list.get(i);

            double angle = (Math.PI * 2.0) * (i / (double) n);
            double ox = Math.cos(angle) * radius;
            double oz = Math.sin(angle) * radius;

            Location spawn = start.clone().add(ox, 0.0, oz);

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

        // once started: clear lobby + close track
        lobby.remove(trackId);
        openTracks.remove(trackId);
    }

    private void onFinish(String trackId, RaceEntry entry) {
        RaceSession session = active.get(trackId);
        if (session == null) return;

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
        c = c.append(Component.text("Boost: ", NamedTextColor.GRAY)).append(Component.text(String.valueOf(boost), NamedTextColor.WHITE));

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
    // Speed logic + Option C personality
    // ------------------------------------------------------------

    private double computeSpeedPerTick(MarbleData data) {
        MarbleStats stats = data.getStats();

        int speed = stats.get(MarbleStat.SPEED);
        int accel = stats.get(MarbleStat.ACCEL);
        int handling = stats.get(MarbleStat.HANDLING);
        int stability = stats.get(MarbleStat.STABILITY);
        int boost = stats.get(MarbleStat.BOOST);

        double base = 0.014;

        double statBoost =
                (speed * 0.00038) +
                        (accel * 0.00030) +
                        (handling * 0.00012);

        double variance = 0.0012;
        double stab = clamp01(stability / 100.0);
        double randomness = (Math.random() - 0.5) * (variance * (1.05 - 0.85 * stab));

        double boostChance = clamp01(boost * 0.008);
        double boostBonus = (Math.random() < boostChance) ? 0.00055 : 0;

        double finalSpeed = base + statBoost + randomness + boostBonus;

        if (finalSpeed < 0.010) finalSpeed = 0.010;
        if (finalSpeed > 0.030) finalSpeed = 0.030;

        return finalSpeed;
    }

    private double computeChaos(MarbleData data) {
        MarbleStats s = data.getStats();
        double stab = clamp01(s.get(MarbleStat.STABILITY) / 100.0);
        double hand = clamp01(s.get(MarbleStat.HANDLING) / 100.0);

        double chaos = 0.55 - (stab * 0.28) - (hand * 0.18);
        return clamp(chaos, 0.12, 0.65);
    }

    private double computeAggression(MarbleData data) {
        MarbleStats s = data.getStats();
        double b = clamp01(s.get(MarbleStat.BOOST) / 100.0);
        double a = clamp01(s.get(MarbleStat.ACCEL) / 100.0);
        double sp = clamp01(s.get(MarbleStat.SPEED) / 100.0);

        double aggro = 0.35 + (b * 0.30) + (a * 0.20) + (sp * 0.10);
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

            for (RaceEntry e : entries) recipients.add(e.owner);
            if (starter != null) recipients.add(starter);
        }
    }
}
