package co.partygame.matchmaking;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class MatchQueue {
    private final String gameType;
    private final List<QueueEntry> entries;
    private final long createdTime;
    private final long ttlSeconds;

    public MatchQueue(String gameType, long ttlSeconds) {
        this.gameType = gameType;
        this.entries = new ArrayList<>();
        this.createdTime = System.currentTimeMillis();
        this.ttlSeconds = ttlSeconds;
    }

    public void addEntry(UUID playerId, String sessionId) {
        entries.add(new QueueEntry(playerId, sessionId));
    }

    public boolean removeEntry(UUID playerId) {
        return entries.removeIf(e -> e.playerId.equals(playerId));
    }

    public List<UUID> getPlayerIds() {
        return entries.stream().map(e -> e.playerId).toList();
    }

    public int getSize() {
        return entries.size();
    }

    public String getGameType() {
        return gameType;
    }

    public boolean isExpired() {
        return (System.currentTimeMillis() - createdTime) > (ttlSeconds * 1000);
    }

    public long getRemainingTime() {
        long elapsed = System.currentTimeMillis() - createdTime;
        long remaining = (ttlSeconds * 1000) - elapsed;
        return remaining / 1000;
    }

    public static class QueueEntry {
        public final UUID playerId;
        public final String sessionId;
        public final long joinTime;

        public QueueEntry(UUID playerId, String sessionId) {
            this.playerId = playerId;
            this.sessionId = sessionId;
            this.joinTime = System.currentTimeMillis();
        }
    }
}
