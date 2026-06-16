package co.partygame.matchmaking;

import co.partygame.common.redis.RedisManager;
import co.partygame.common.util.JsonUtils;
import co.partygame.common.config.ConfigManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages matchmaking queues for both solo and party players.
 * Uses Redis as the single source of truth for cross-lobby sync.
 * 
 * Redis keys:
 *   - partygame:match:{gameId}:{gameType}:queue (sorted set)
 *   - partygame:match:state:{uuid} (hash with sessionId, gameType, timestamp, partyId, gameId)
 */
public class MatchQueue {

    private static final Logger LOGGER = Logger.getLogger(MatchQueue.class.getName());

    private final RedisManager redis;
    private final MatchStrategy strategy;
    private final ConfigManager config;
    private final int queueTtl;

    private final Map<UUID, QueueEntry> localQueue = new ConcurrentHashMap<>();

    public MatchQueue(RedisManager redis, MatchStrategy strategy, ConfigManager config) {
        this.redis = Objects.requireNonNull(redis);
        this.strategy = Objects.requireNonNull(strategy);
        this.config = Objects.requireNonNull(config);
        this.queueTtl = config.getInt("match.queue_ttl", 300);
    }

    /**
     * Adds a player to the matchmaking queue in Redis sorted set.
     * Score is the join timestamp for order of entry.
     *
     * @param player        the player to add
     * @param gameId        the game identifier (e.g., "survival_001")
     * @param gameType      the type of game (e.g., "partygame")
     * @param customOptions custom game options for this queue entry
     * @return true if successfully added
     */
    public boolean addToQueue(Player player, String gameId, String gameType, Map<String, Object> customOptions) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(gameId);
        Objects.requireNonNull(gameType);

        UUID uuid = player.getUniqueId();
        long timestamp = System.currentTimeMillis();

        QueueEntry entry = new QueueEntry(
                uuid,
                player.getName(),
                gameId,
                gameType,
                customOptions != null ? new LinkedHashMap<>(customOptions) : new LinkedHashMap<>(),
                null,
                timestamp
        );

        String redisKey = buildRedisKey(gameId, gameType);
        String serialized = JsonUtils.toJson(entry);

        redis.hset(buildStateKey(uuid), "sessionId", UUID.randomUUID().toString());
        redis.hset(buildStateKey(uuid), "gameType", gameType);
        redis.hset(buildStateKey(uuid), "timestamp", String.valueOf(timestamp));
        redis.hset(buildStateKey(uuid), "partyId", entry.partyId != null ? entry.partyId.toString() : "");
        redis.hset(buildStateKey(uuid), "gameId", gameId);
        redis.hset(buildStateKey(uuid), "playerName", player.getName());

        redis.zAdd(redisKey, timestamp, serialized);

        localQueue.put(uuid, entry);

        LOGGER.info(player.getName() + " added to queue for " + gameType + " (gameId: " + gameId + ")");
        return true;
    }

    /**
     * Removes a player from the matchmaking queue in Redis.
     *
     * @param player the player to remove
     * @return true if successfully removed
     */
    public boolean removeFromQueue(Player player) {
        return removeFromQueue(player.getUniqueId());
    }

    /**
     * Removes a player from the matchmaking queue by UUID.
     *
     * @param uuid the player UUID
     * @return true if successfully removed
     */
    public boolean removeFromQueue(UUID uuid) {
        QueueEntry entry = localQueue.remove(uuid);
        if (entry == null) return false;

        String redisKey = buildRedisKey(entry.gameId, entry.gameType);
        String serialized = JsonUtils.toJson(entry);
        redis.zRem(redisKey, serialized);
        redis.del(buildStateKey(uuid));

        LOGGER.info(entry.name + " removed from queue");
        return true;
    }

    /**
     * Gets the player's position in the queue (0-based from the front).
     * Uses ZREVRANK to rank by score (timestamp).
     *
     * @param player the player to query
     * @return the queue position, or -1 if not in queue
     */
    public int getQueuePosition(Player player) {
        QueueEntry entry = localQueue.get(player.getUniqueId());
        if (entry == null) return -1;

        String redisKey = buildRedisKey(entry.gameId, entry.gameType);
        String serialized = JsonUtils.toJson(entry);
        Long ranking = redis.zRevRank(redisKey, serialized);
        return ranking != null ? ranking.intValue() : -1;
    }

    /**
     * Gets the total number of players in the queue for a specific game type.
     *
     * @param gameType the game type to query
     * @return the number of players in the queue
     */
    public int getQueueSize(String gameType) {
        String key = buildRedisKey("*", gameType);
        Long size = redis.zCard(key);
        return size != null ? size.intValue() : 0;
    }

    /**
     * Gets the next batch of players ready for matching.
     * Returns top N from the sorted set based on match strategy.
     *
     * @return a list of QueueEntry ready for matching
     */
    public List<QueueEntry> getNextBatch() {
        List<QueueEntry> batch = new ArrayList<>();
        int maxPlayers = config.getInt("match.max_players_match", 8);

        for (QueueEntry entry : new ArrayList<>(localQueue.values())) {
            String key = buildRedisKey("*", entry.gameType);
            Set<String> members = redis.zRange(key, 0, maxPlayers - 1);
            for (String serialized : members) {
                try {
                    QueueEntry queued = JsonUtils.fromJson(serialized, QueueEntry.class);
                    if (queued != null && !batch.contains(queued) && batch.size() < maxPlayers) {
                        batch.add(queued);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to deserialize queue entry", e);
                }
            }
        }

        return batch;
    }

    /**
     * Checks if a player is currently in any matchmaking queue.
     *
     * @param player the player to check
     * @return true if the player is in a queue
     */
    public boolean isInQueue(Player player) {
        return localQueue.containsKey(player.getUniqueId());
    }

    /**
     * Checks if a player UUID is in any matchmaking queue.
     *
     * @param uuid the player UUID
     * @return true if the player is in a queue
     */
    public boolean isInQueue(UUID uuid) {
        return localQueue.containsKey(uuid);
    }

    /**
     * Calculates the estimated wait time based on queue size and the average match interval.
     *
     * @param queueSize      the current size of the queue
     * @param avgMatchInterval the average time between matches in seconds
     * @return estimated wait time in seconds, or the configured default
     */
    public int getEstimatedWaitTime(int queueSize, int avgMatchInterval) {
        if (queueSize <= 0) return config.getInt("match.default_wait_estimate", 60);

        int maxPlayers = config.getInt("match.max_players_match", 8);
        int batches = (int) Math.ceil((double) queueSize / maxPlayers);
        int waitTime = batches * avgMatchInterval;

        return Math.min(waitTime, config.getInt("match.default_wait_estimate", 60));
    }

    /**
     * Gets statistics about the queue for a specific game type.
     *
     * @param gameType the game type to query
     * @return a map with count, avgWait, and other stats
     */
    public Map<String, Integer> getQueueStats(String gameType) {
        Map<String, Integer> result = new HashMap<>();
        String key = buildRedisKey("*", gameType);
        Long count = redis.zCard(key);
        result.put("count", count != null ? count.intValue() : 0);

        int waitEstimate = getEstimatedWaitTime(
                count != null ? count.intValue() : 0,
                config.getInt("match.default_wait_estimate", 60)
        );
        result.put("avgWait", waitEstimate);

        return result;
    }

    /**
     * Processes queues by checking for matchable groups.
     * Called periodically by the main scheduler.
     */
    public void processQueues() {
        List<QueueEntry> readyPlayers = new ArrayList<>(localQueue.values());
        if (readyPlayers.isEmpty()) return;

        Map<String, List<QueueEntry>> grouped = strategy.groupByGameType(readyPlayers);
        for (Map.Entry<String, List<QueueEntry>> group : grouped.entrySet()) {
            String gameId = group.getKey();
            List<QueueEntry> candidates = group.getValue();

            int i = 0;
            while (i < candidates.size()) {
                int maxSlots = config.getInt("match.max_players_match", 8);
                int remainingNeeded = Math.min(maxSlots, candidates.size() - i);

                if (remainingNeeded < 1) break;

                List<QueueEntry> window = candidates.subList(i, Math.min(i + remainingNeeded, candidates.size()));
                if (strategy.canMatchCohort(window)) {
                    List<QueueEntry> selected = strategy.selectPlayersForGame(
                            window, remainingNeeded, gameId);
                    for (QueueEntry entry : selected) {
                        removeFromQueue(entry.uuid);
                    }
                    i += selected.size();
                } else {
                    i++;
                }
            }
        }
    }

    /**
     * Gets all local queue entries for a specific game type.
     *
     * @param gameType the game type
     * @return list of queue entries for that game type
     */
    public List<QueueEntry> getQueueByGameType(String gameType) {
        List<QueueEntry> result = new ArrayList<>();
        for (QueueEntry entry : localQueue.values()) {
            if (entry.gameType.equals(gameType)) {
                result.add(entry);
            }
        }
        return result;
    }

    private String buildRedisKey(String gameId, String gameType) {
        if (gameId.equals("*")) {
            return "partygame:match:" + gameType + ":queue";
        }
        return "partygame:match:" + gameId + ":" + gameType + ":queue";
    }

    private String buildStateKey(UUID uuid) {
        return "partygame:match:state:" + uuid.toString();
    }

    public Map<UUID, QueueEntry> getLocalQueue() {
        return Collections.unmodifiableMap(localQueue);
    }

    /**
     * Queue entry holding all information about a player in the matchmaking queue.
     */
    public static class QueueEntry implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public final UUID uuid;
        public final String name;
        public final String gameId;
        public final String gameType;
        public final Map<String, Object> customOptions;
        public final PartyInfo party;
        public final long joinTime;

        public QueueEntry(UUID uuid, String name, String gameId, String gameType,
                          Map<String, Object> customOptions, PartyInfo party, long joinTime) {
            this.uuid = Objects.requireNonNull(uuid);
            this.name = Objects.requireNonNull(name);
            this.gameId = Objects.requireNonNull(gameId);
            this.gameType = Objects.requireNonNull(gameType);
            this.customOptions = customOptions != null ? customOptions : new LinkedHashMap<>();
            this.party = party;
            this.joinTime = joinTime;
        }

        public String getSessionId() {
            String val = redis.hget(buildPlayerStateKey(uuid), "sessionId");
            return val != null ? val : "";
        }

        public UUID getPartyId() {
            return party != null ? party.partyId : null;
        }

        public boolean hasParty() {
            return party != null && party.members.size() > 1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QueueEntry)) return false;
            QueueEntry that = (QueueEntry) o;
            return Objects.equals(uuid, that.uuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid);
        }

        private String buildPlayerStateKey(UUID uuid) {
            return "partygame:match:state:" + uuid.toString();
        }
    }

    /**
     * Represents a party group attempting to queue together.
     */
    public static class PartyInfo implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public final UUID partyId;
        public final List<QueueEntry> members;

        public PartyInfo(UUID partyId, List<QueueEntry> members) {
            this.partyId = Objects.requireNonNull(partyId);
            this.members = List.copyOf(members);
        }

        public int size() {
            return members.size();
        }
    }
}
