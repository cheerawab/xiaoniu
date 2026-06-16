package co.partygame.core.game;

import co.partygame.common.protocol.packets.backend.GameResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents one active game session on a backend server.
 *
 * <p>A game session contains 4-8 players on a single SWM world. It tracks
 * the game state (WAITING, RUNNING, ENDED), manages per-round progression,
 * and accumulates custom options from the lobby request.</p>
 *
 * <p>All public methods are synchronized to ensure thread-safe access
 * when multiple Bukkit scheduler threads interact with the session.</p>
 */
public class GameSession {

    private static final Logger LOGGER = Logger.getLogger(GameSession.class.getName());

    /** Possible states for a game session lifecycle. */
    public enum Status {
        /** Session created but players have not been spawned yet. */
        WAITING,
        /** Game is actively running (rounds in progress). */
        RUNNING,
        /** Game has ended (all rounds completed or forced end). */
        ENDED,
        /** Game was cancelled (players returned to lobby). */
        CANCELLED
    }

    /**
     * A result record for a single player in a game session.
     */
    public static class GameResultEntry {
        private final UUID player;
        private final int score;

        public GameResultEntry(UUID player, int score) {
            this.player = Objects.requireNonNull(player, "UUID must not be null");
            this.score = score;
        }

        public UUID getPlayer() {
            return player;
        }

        public int getScore() {
            return score;
        }

        /**
         * Creates a common-lib compatible GameResult from this entry.
         *
         * @return co.partygame.common.protocol.packets.backend.GameResult
         */
        public GameResult toBackendResult() {
            return new GameResult(player, score);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GameResultEntry that = (GameResultEntry) o;
            return score == that.score && Objects.equals(player, that.player);
        }

        @Override
        public int hashCode() {
            return Objects.hash(player, score);
        }
    }

    private final String sessionId;
    private final String gameId;
    private final String gameType;
    private final String worldName;

    private volatile Status status;
    private final Set<UUID> players;
    private final int totalRounds;
    private volatile int currentRound;

    private final Map<String, Object> customOptions;
    private final List<GameResultEntry> results;

    private volatile long startTime;
    private volatile long endTime;

    /**
     * Creates a new GameSession.
     *
     * @param sessionId  unique session identifier
     * @param gameId     the game type identifier (e.g., "survival")
     * @param gameType   the display game type name
     * @param worldName  the SWM world name assigned to this session
     * @param totalRounds  total number of rounds for the game
     */
    public GameSession(String sessionId, String gameId, String gameType,
                       String worldName, int totalRounds) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.gameType = Objects.requireNonNull(gameType, "gameType must not be null");
        this.worldName = Objects.requireNonNull(worldName, "worldName must not be null");
        this.totalRounds = Math.max(1, totalRounds);
        this.status = Status.WAITING;
        this.players = ConcurrentHashMap.newKeySet();
        this.customOptions = new ConcurrentHashMap<>();
        this.results = new ArrayList<>();
        this.currentRound = 0;
    }

    // ─── Getters ───────────────────────────────────────────────────

    public String getSessionId() {
        return sessionId;
    }

    public String getGameId() {
        return gameId;
    }

    public String getGameType() {
        return gameType;
    }

    public String getWorldName() {
        return worldName;
    }

    /**
     * Returns the current session status.
     *
     * @return current Status
     */
    public Status getStatus() {
        return status;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public Map<String, Object> getCustomOptions() {
        return Collections.unmodifiableMap(customOptions);
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    // ─── Session Management (synchronized) ─────────────────────────

    /**
     * Starts the game session, spawning all waiting players.
     *
     * @return this session for method chaining
     */
    public synchronized GameSession start() {
        if (status != Status.WAITING) {
            throw new IllegalStateException("Can only start from WAITING status, current: " + status);
        }
        status = Status.RUNNING;
        startTime = System.currentTimeMillis();
        currentRound = 0;
        results.clear();
        LOGGER.info(() -> "Session " + sessionId + " started (type=" + gameType + ", players=" + players.size() + ")");
        return this;
    }

    /**
     * Ends the game session (graceful completion of all rounds).
     *
     * @return this session for method chaining
     */
    public synchronized GameSession end() {
        if (status != Status.RUNNING && status != Status.WAITING) {
            throw new IllegalStateException("Cannot end session in status: " + status);
        }
        status = Status.ENDED;
        endTime = System.currentTimeMillis();
        LOGGER.info(() -> "Session " + sessionId + " ended (duration=" + (endTime - startTime) / 1000 + "s)");
        return this;
    }

    /**
     * Cancels the game session (players returned to lobby without results).
     *
     * @return this session for method chaining
     */
    public synchronized GameSession cancel() {
        if (status != Status.RUNNING && status != Status.WAITING) {
            throw new IllegalStateException("Cannot cancel session in status: " + status);
        }
        status = Status.CANCELLED;
        endTime = System.currentTimeMillis();
        LOGGER.warning(() -> "Session " + sessionId + " cancelled");
        return this;
    }

    /**
     * Adds a player to this session.
     *
     * @param uuid the player's UUID
     * @return true if the player was added
     */
    public synchronized boolean addPlayer(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID must not be null");
        if (players.contains(uuid)) {
            return false;
        }
        if (players.size() >= 8) {
            throw new IllegalStateException("Session is full (" + players.size() + "/8)");
        }
        return players.add(uuid);
    }

    /**
     * Removes a player from this session.
     *
     * @param uuid the player's UUID
     * @return true if the player was removed, false if not found
     */
    public synchronized boolean removePlayer(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID must not be null");
        return players.remove(uuid);
    }

    /**
     * Retrieves a specific player from the session.
     *
     * @param uuid the player's UUID
     * @return the UUID if found, null otherwise
     */
    public UUID getPlayer(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID must not be null");
        return players.contains(uuid) ? uuid : null;
    }

    /**
     * Checks if the session has reached maximum capacity.
     *
     * @return true if the session is at or above max capacity (8 players)
     */
    public boolean isFull() {
        return players.size() >= 8;
    }

    /**
     * Returns a set view of all player UUIDs in this session.
     *
     * @return immutable set of player UUIDs
     */
    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    /**
     * Returns the current number of players in this session.
     *
     * @return player count
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * Sets a custom option for this session.
     *
     * @param key   the option key
     * @param value the option value
     */
    public synchronized void setCustomOption(String key, Object value) {
        if (key == null) throw new IllegalArgumentException("Key must not be null");
        if (value == null) {
            customOptions.remove(key);
            return;
        }
        customOptions.put(key, value);
        LOGGER.fine(() -> "Session " + sessionId + " custom option set: " + key + "=" + value);
    }

    /**
     * Gets the value of a custom option.
     *
     * @param key the option key
     * @return the option value, or null if not set
     */
    public Object getCustomOption(String key) {
        if (key == null) return null;
        return customOptions.get(key);
    }

    /**
     * Marks a round as started in this session.
     *
     * @param round the round number (1-based)
     */
    public synchronized void startRound(int round) {
        if (round < 1 || round > totalRounds) {
            throw new IllegalArgumentException("Invalid round number: " + round + " (valid: 1-" + totalRounds + ")");
        }
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Cannot start round in status: " + status);
        }
        currentRound = round;
        LOGGER.info(() -> "Session " + sessionId + " round " + round + " started (" + currentRound + "/" + totalRounds + ")");
    }

    /**
     * Marks a round as ended in this session.
     *
     * @param round the round number that just ended (1-based)
     */
    public synchronized void endRound(int round) {
        if (round < 1 || round > totalRounds) {
            throw new IllegalArgumentException("Invalid round number: " + round);
        }
        LOGGER.info(() -> "Session " + sessionId + " round " + round + " ended");
    }

    /**
     * Computes and caches all game results. Delegates to the registered
     * {@link IGamePlugin} for scoring logic.
     *
     * @return list of per-player results
     */
    public List<GameResultEntry> getAllResults() {
        return Collections.unmodifiableList(results);
    }

    /**
     * Sets the cached game results for this session.
     *
     * @param resultEntries the list of game result entries
     */
    public synchronized void setResults(Collection<GameResultEntry> resultEntries) {
        Objects.requireNonNull(resultEntries, "Results must not be null");
        results.clear();
        results.addAll(resultEntries);
    }

    /**
     * Converts all cached results to the common-lib backend format.
     *
     * @return list of backend-compatible GameResult packets
     */
    public List<co.partygame.common.protocol.packets.backend.GameResult> toBackendResults() {
        return results.stream()
                .map(GameResultEntry::toBackendResult)
                .collect(Collectors.toList());
    }

    /**
     * Returns true if this session can still accept players.
     *
     * @return true if status is WAITING and not full
     */
    public boolean canAcceptPlayers() {
        return status == Status.WAITING && !isFull();
    }

    @Override
    public String toString() {
        return "GameSession{sessionId=" + sessionId
                + ", gameId=" + gameId
                + ", gameType=" + gameType
                + ", world=" + worldName
                + ", status=" + status
                + ", players=" + players.size() + "/" + totalRounds
                + ", round=" + currentRound + "/" + totalRounds
                + "}";
    }
}
