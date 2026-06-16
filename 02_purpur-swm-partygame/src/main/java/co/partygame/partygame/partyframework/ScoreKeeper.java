package co.partygame.partygame.partyframework;

import co.partygame.partygame.config.PartyGameConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages persistent scores across game sessions.
 * <p>
 * Tracks total points, win/loss counts, and placement history for players.
 * Uses a background cleaner to prune stale entries.
 */
public final class ScoreKeeper {

    private final PartyGameConfig config;
    private final Map<String, PlayerScore> scores;
    private volatile boolean running;

    private static final class PlayerScore {
        final String playerName;
        long totalPoints;
        int wins;
        int losses;
        Map<Integer, Integer> placementHistory; // placement -> count
        long lastPlayedAt;

        PlayerScore(String playerName) {
            this.playerName = playerName;
            this.totalPoints = 0;
            this.wins = 0;
            this.losses = 0;
            this.placementHistory = new HashMap<>();
            this.lastPlayedAt = 0;
        }
    }

    public ScoreKeeper(PartyGameConfig config) {
        this.config = Objects.requireNonNull(config);
        this.scores = new ConcurrentHashMap<>();
        this.running = false;
    }

    /**
     * Record a completed game result for all players in a session.
     *
     * @param session the finished game session
     */
    public void recordCompletion(GameSession session) {
        Map<String, GameSession.ScoreEntry> entries = session.getScores();

        for (Map.Entry<String, GameSession.ScoreEntry> entry : entries.entrySet()) {
            String playerName = entry.getKey();
            GameSession.ScoreEntry scoreEntry = entry.getValue();

            PlayerScore ps = scores.computeIfAbsent(playerName, PlayerScore::new);

            ps.totalPoints += scoreEntry.score;
            ps.lastPlayedAt = System.currentTimeMillis();

            if (scoreEntry.placement == 1) {
                ps.wins++;
            } else {
                ps.losses++;
            }

            ps.placementHistory.merge(scoreEntry.placement, 1, Integer::sum);
        }
    }

    /**
     * Get a player's total score.
     */
    public long getScore(String playerName) {
        PlayerScore ps = scores.get(playerName);
        return ps != null ? ps.totalPoints : 0;
    }

    /**
     * Get a player's win count.
     */
    public int getWins(String playerName) {
        PlayerScore ps = scores.get(playerName);
        return ps != null ? ps.wins : 0;
    }

    /**
     * Get all known players and their scores, sorted by total score descending.
     */
    public List<Map.Entry<String, Long>> getLeaderboard(int topN) {
        return scores.values().stream()
                .map(ps -> Map.entry(ps.playerName, ps.totalPoints))
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .toList();
    }

    /**
     * Check if a player has any recorded scores.
     */
    public boolean hasPlayer(String playerName) {
        return scores.containsKey(playerName);
    }

    /**
     * Clear all scores.
     */
    public void clearAll() {
        scores.clear();
    }

    /**
     * Start the background scorer cleaner.
     */
    public void start() {
        running = true;
        // Background decay of old scores
        Thread decayThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(3600000); // 1 hour
                    applyDecay();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "partygame-scorekeeper-decay");
        decayThread.setDaemon(true);
        decayThread.start();
    }

    /**
     * Stop the scorer.
     */
    public void stop() {
        running = false;
    }

    private void applyDecay() {
        long decayPerHour = config.getWinPoints(); // decay factor from config if needed
        if (decayPerHour <= 0) return;

        long now = System.currentTimeMillis();
        for (Map.Entry<String, PlayerScore> entry : scores.entrySet()) {
            PlayerScore ps = entry.getValue();
            long idleSeconds = (now - ps.lastPlayedAt) / 1000;
            if (idleSeconds > 3600) { // Only decay players who haven't played in 1 hour
                long hours = idleSeconds / 3600;
                long decay = hours * decayPerHour;
                if (ps.totalPoints >= decay) {
                    ps.totalPoints -= decay;
                } else {
                    ps.totalPoints = 0;
                }
            }
        }
    }
}
