package co.partygame.partygame.partyframework;

import co.partygame.partygame.config.PartyGameConfig;

import java.util.*;
import java.util.concurrent.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Manages game sessions, rounds, and scoring for the PartyGame framework.
 * <p>
 * A {@code GameSession} represents a single instance of a game being played
 * (e.g., one BedWars match). It manages:
 * <ul>
 *   <li>World allocation (via the SWM WorldPool)</li>
 *   <li>Player enrollment and teleportation</li>
 *   <li>Round lifecycle (setup, playing, end, cleanup)</li>
 *   <li>Score tracking and placement resolution</li>
 * </ul>
 */
public final class GameSession {

    private final JavaPlugin plugin;
    private final String id;
    private final String gameType;
    private final PartyGameConfig config;

    private volatile SessionState state;
    private final List<String> players;
    private final Set<String> activePlayers;
    private String allocatedWorld;
    private Round currentRound;
    private final Map<String, ScoreEntry> scores;
    private final Scoreboard scoreboard;
    private ScheduledFuture<?> roundTimerHandle;
    private long startTimeMillis;
    private long endTimeMillis;

    /**
     * Current states of a game session.
     */
    public enum SessionState {
        WAITING,    // Waiting for players
        STARTING,   // Countdown, not yet active
        ACTIVE,     // Round(s) in progress
        ENDING,     // Final countdown
        ENDED       // Round/game finished, cleanup pending
    }

    /**
     * Score entry per player.
     */
    public static class ScoreEntry {
        public final String playerName;
        public final int score;
        public final int placement; // Position (1, 2, 3...)

        public ScoreEntry(String playerName, int score, int placement) {
            this.playerName = playerName;
            this.score = score;
            this.placement = placement;
        }
    }

    /**
     * Represents a single round within a session.
     */
    public static class Round {
        private final int number;
        private final long durationSeconds;
        private volatile long startTime;
        private volatile long endTime;
        private RoundState currentRoundState;

        public Round(int number, long durationSeconds) {
            this.number = number;
            this.durationSeconds = durationSeconds;
            this.startTime = 0;
            this.endTime = 0;
            this.currentRoundState = RoundState.PREPARING;
        }

        public int getNumber() { return number; }
        public long getDurationSeconds() { return durationSeconds; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public RoundState getCurrentRoundState() { return currentRoundState; }

        public void start() {
            startTime = System.currentTimeMillis();
            endTime = startTime + durationSeconds * 1000L;
            currentRoundState = RoundState.PLAYING;
        }

        public void end() {
            currentRoundState = RoundState.FINISHED;
            endTime = System.currentTimeMillis();
        }

        public boolean isPlaying() {
            return currentRoundState == RoundState.PLAYING;
        }

        public boolean hasEnded() {
            return currentRoundState == RoundState.FINISHED;
        }

        public long getTimeRemainingMs() {
            if (currentRoundState != RoundState.PLAYING) return 0;
            return Math.max(0, endTime - System.currentTimeMillis());
        }

        public long getTimeRemainingSeconds() {
            return getTimeRemainingMs() / 1000;
        }
    }

    /**
     * Possible states for a round.
     */
    public enum RoundState {
        PREPARING,
        PLAYING,
        ENDING,
        FINISHED;
    }

    /**
     * Create a new game session.
     *
     * @param id unique session identifier
     * @param gameType the game type (e.g., "bedwars", "skywars")
     * @param config configuration accessor
     * @param plugin the PartyGame plugin instance (for Bukkit access)
     */
    public GameSession(String id, String gameType, PartyGameConfig config, JavaPlugin plugin) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.gameType = Objects.requireNonNull(gameType, "gameType cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");

        this.state = SessionState.WAITING;
        this.players = new CopyOnWriteArrayList<>();
        this.activePlayers = ConcurrentHashMap.newKeySet();
        this.allocatedWorld = null;
        this.scores = new ConcurrentHashMap<>();
        this.startTimeMillis = 0;
        this.endTimeMillis = 0;

        // Create a session-specific scoreboard
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    }

    // ============================================================
    // Session Lifecycle
    // ============================================================

    /**
     * Start the session ??set state to STARTING.
     */
    public boolean start(List<Player> players) {
        if (state != SessionState.WAITING) {
            return false;
        }

        this.players.clear();
        this.players.addAll(players.stream()
                .map(Player::getName)
                .toList());

        this.activePlayers.addAll(this.players);
        this.state = SessionState.STARTING;

        // Start the first round
        currentRound = new Round(1, config.getRoundDurationSeconds());
        currentRound.start();
        this.state = SessionState.ACTIVE;
        this.startTimeMillis = System.currentTimeMillis();

        if (config.isLogSessionEvents()) {
            plugin.getLogger().info(
                    "Session '" + id + "' (" + gameType + ") started with "
                    + players.size() + " players.");
        }

        return true;
    }

    /**
     * End the session ??trigger cleanup.
     */
    public void end() {
        if (state == SessionState.ENDED) return;

        state = SessionState.ENDING;
        currentRound.end();

        if (roundTimerHandle != null) {
            roundTimerHandle.cancel(false);
        }

        this.endTimeMillis = System.currentTimeMillis();

        // Resolve final placement and scores
        resolvePlacement();

        // Notify players
        for (String playerName : players) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null && player.isOnline()) {
                ScoreEntry entry = scores.getOrDefault(playerName, new ScoreEntry(playerName, 0, 0));
                player.sendMessage("§e[PartyGame] §rRound complete! Score: §b" + entry.score
                        + " §r/ Placement: §b#" + entry.placement);
            }
        }

        // Transition to ended
        state = SessionState.ENDED;

        if (config.isLogSessionEvents()) {
            plugin.getLogger().info(
                    "Session '" + id + "' (" + gameType + ") ended.");
        }
    }

    // ============================================================
    // Player Management
    // ============================================================

    /**
     * Add a player to this session.
     */
    public boolean addPlayer(String playerName) {
        if (state != SessionState.WAITING && state != SessionState.STARTING) {
            return false;
        }
        if (players.contains(playerName) || activePlayers.contains(playerName)) {
            return false; // Already a player
        }
        if (players.size() >= config.getMaxPlayersPerGame()) {
            return false; // Full
        }
        players.add(playerName);
        return true;
    }

    /**
     * Remove a player from this session (e.g. due to disconnect).
     */
    public void removePlayer(String playerName) {
        players.remove(playerName);
        activePlayers.remove(playerName);
    }

    public void leavePlayer(String playerName) {
        if (state != SessionState.ACTIVE) return;

        activePlayers.remove(playerName);
        scores.computeIfAbsent(playerName, k -> new ScoreEntry(playerName, 0, 0));

        if (config.isLogSessionEvents()) {
            plugin.getLogger().info("Session '" + id + "': player "
                    + playerName + " left the game.");
        }
    }

    /**
     * Return the list of all enrolled players.
     */
    public List<String> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    /**
     * Return the list of active (non-dead/removed) players.
     */
    public List<String> getActivePlayers() {
        return new ArrayList<>(activePlayers);
    }

    /**
     * Check if a player is in this session.
     */
    public boolean hasPlayer(String playerName) {
        return players.contains(playerName);
    }

    /**
     * Check if a player is currently active.
     */
    public boolean isActivePlayer(String playerName) {
        return activePlayers.contains(playerName);
    }

    // ============================================================
    // Score Management
    // ============================================================

    /**
     * Add score to a player.
     */
    public void addScore(String playerName, int score) {
        ScoreEntry entry = scores.computeIfAbsent(playerName,
                k -> new ScoreEntry(playerName, 0, 0));
        int newScore = entry.score + score;
        scores.put(playerName, new ScoreEntry(playerName, newScore, entry.placement));
    }

    /**
     * Get a player's current score.
     */
    public int getScore(String playerName) {
        ScoreEntry entry = scores.get(playerName);
        return entry != null ? entry.score : 0;
    }

    /**
     * Resolve final placement based on scores.
     * Players are sorted by score descending; tied scores get same rank.
     */
    private void resolvePlacement() {
        List<ScoreEntry> sorted = new ArrayList<>(scores.values());
        sorted.sort((a, b) -> Integer.compare(b.score, a.score));

        int rank = 1;
        Map<String, ScoreEntry> updatedEntries = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            ScoreEntry entry = sorted.get(i);
            updatedEntries.put(entry.playerName, new ScoreEntry(entry.playerName, entry.score, rank));
            if (i < sorted.size() - 1) {
                ScoreEntry next = sorted.get(i + 1);
                if (next.score < entry.score) rank = i + 2;
            }
        }
        scores.clear();
        scores.putAll(updatedEntries);
    }

    /**
     * Get all score entries.
     */
    public Map<String, ScoreEntry> getScores() {
        return Collections.unmodifiableMap(scores);
    }

    // ============================================================
    // Query API
    // ============================================================

    public String getId() { return id; }
    public String getGameType() { return gameType; }
    public SessionState getState() { return state; }
    public Round getCurrentRound() { return currentRound; }
    public int getPlayerCount() { return players.size(); }
    public int getActivePlayerCount() { return activePlayers.size(); }
    /**
     * Returns the session's allocation world.
     */
    public String getAllocatedWorld() { return allocatedWorld; }
    public void setAllocatedWorld(String world) { this.allocatedWorld = world; }

    public long getStartTimeMillis() { return startTimeMillis; }
    public long getEndTimeMillis() { return endTimeMillis; }

    public long getSessionDurationSeconds() {
        if (endTimeMillis == 0) return (System.currentTimeMillis() - startTimeMillis) / 1000;
        return (endTimeMillis - startTimeMillis) / 1000;
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    public boolean isFull() {
        return players.size() >= config.getMaxPlayersPerGame();
    }

    public int getRemainingPlayerSlots() {
        return Math.max(0, config.getMaxPlayersPerGame() - players.size());
    }

    /**
     * Get a summary string for display.
     */
    public String toSummary() {
        return String.format("[%s|%s|Players:%d/%d|%s|%s]",
                id, gameType.toUpperCase(),
                players.size(), config.getMaxPlayersPerGame(),
                state.name(),
                currentRound != null ? "R" + currentRound.getNumber() : "N/A");
    }
}
