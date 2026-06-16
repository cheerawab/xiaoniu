package co.partygame.matchmaking.storage;

import co.partygame.common.redis.RedisManager;
import co.partygame.common.mysql.MySQLManager;
import co.partygame.common.mysql.tables.DatabaseTables;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Aggregated queue statistics across all game types.
 * Caches local statistics and syncs to MySQL for persistence.
 */
public class QueueStats {

    private static final Logger LOGGER = Logger.getLogger(QueueStats.class.getName());

    private final RedisManager redis;
    private final Map<String, Integer> queueSizes = new HashMap<>();

    public QueueStats(RedisManager redis) {
        this.redis = Objects.requireNonNull(redis);
    }

    /**
     * Updates statistics for a specific game type.
     *
     * @param gameId    the game identifier
     * @param gameType  the game type
     * @param size      current queue size
     * @param avgWait   average wait time in seconds
     */
    public void updateStats(String gameId, String gameType, int size, int avgWait) {
        Objects.requireNonNull(gameId);
        Objects.requireNonNull(gameType);

        String key = "partygame:stats:" + gameId + ":" + gameType;
        Map<String, String> data = new LinkedHashMap<>();
        data.put("size", String.valueOf(size));
        data.put("avgWait", String.valueOf(avgWait));
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));

        redis.hmset(key, data);
        redis.expire(key, 300);

        queueSizes.put(gameType, size);
    }

    /**
     * Increments the active queue size for a game type.
     *
     * @param gameType the game type
     * @return the new size
     */
    public long incrementQueue(String gameType) {
        Objects.requireNonNull(gameType);
        long size = redis.incrExpire("partygame:stats:active:" + gameType, 300);
        queueSizes.merge(gameType, size.intValue(), Integer::sum);
        return size;
    }

    /**
     * Decrements the active queue size for a game type.
     *
     * @param gameType the game type
     * @return the new size
     */
    public long decrementQueue(String gameType) {
        Objects.requireNonNull(gameType);
        return Math.max(0, redis.decr("partygame:stats:active:" + gameType));
    }

    /**
     * Gets the current queue size for a game type.
     *
     * @param gameType the game type
     * @return current queue size, or 0 if not tracked
     */
    public int getQueueSize(String gameType) {
        queueSizes.merge(gameType, 0, (current, delta) -> current + delta);
        return queueSizes.getOrDefault(gameType, 0);
    }

    /**
     * Gets all tracked game types with active queues.
     *
     * @return list of game type names with non-zero queue sizes
     */
    public List<String> getActiveGameTypes() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : queueSizes.entrySet()) {
            if (entry.getValue() > 0) {
                result.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Gets a snapshot of all current queue sizes.
     *
     * @return list of queue size snapshots
     */
    public List<QueueSizeSnapshot> getQueueSizes() {
        List<QueueSizeSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : queueSizes.entrySet()) {
            snapshots.add(new QueueSizeSnapshot(entry.getKey(), entry.getValue()));
        }
        return snapshots;
    }

    /**
     * Cleans up stale queue entries (size dropped to 0).
     * Runs periodically via the scheduler.
     */
    public void cleanupStaleEntries() {
        Set<String> toRemove = new HashSet<>();
        for (Map.Entry<String, Integer> entry : queueSizes.entrySet()) {
            if (entry.getValue() <= 0) {
                toRemove.add(entry.getKey());
            }
        }
        for (String gameType : toRemove) {
            queueSizes.remove(gameType);
            redis.del("partygame:stats:active:" + gameType);
        }
    }

    /**
     * Syncs all in-memory statistics to MySQL for persistence.
     *
     * @param mysql the MySQL manager
     */
    public void syncToSql(MySQLManager mysql) {
        try {
            DatabaseTables.initTables(mysql);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to initialize database tables for QueueStats", e);
        }

        for (Map.Entry<String, Integer> entry : queueSizes.entrySet()) {
            String gameType = entry.getKey();
            int size = entry.getValue();
            if (size <= 0) continue;

            String key = "partygame:stats:persist:" + gameType;
            Map<String, String> data = new HashMap<>();
            data.put("size", String.valueOf(size));
            data.put("savedAt", String.valueOf(System.currentTimeMillis()));
            redis.hmset(key, data);
        }
    }

    /**
     * Holds a snapshot of queue size for a game type.
     */
    public static class QueueSizeSnapshot implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public final String gameType;
        public final int size;
        public final long timestamp;

        public QueueSizeSnapshot(String gameType, int size) {
            this.gameType = Objects.requireNonNull(gameType);
            this.size = size;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
