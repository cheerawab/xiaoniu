package co.partygame.matchmaking.party;

import co.partygame.common.redis.RedisManager;
import co.partygame.common.util.JsonUtils;
import co.partygame.matchmaking.MatchQueue;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages party-specific matchmaking queues.
 * When a party joins, all members are queued together as a group.
 * Parties are treated as atomic units: all members route to the same backend.
 * 
 * Redis key: partygame:party:{gameId}:{gameType}:queue (sorted set where score = timestamp)
 */
public class PartyMatchQueue {

    private static final Logger LOGGER = Logger.getLogger(PartyMatchQueue.class.getName());

    private final RedisManager redis;

    public PartyMatchQueue(RedisManager redis) {
        this.redis = Objects.requireNonNull(redis);
    }

    /**
     * Adds an entire party to the matchmaking queue as a single entry.
     * Each member is added as a separate entry with the partyId, but the party
     * is treated as an atomic unit during matching.
     *
     * @param leader        the party leader who queued the party
     * @param members       all party members
     * @param gameId        the game identifier
     * @param gameType      the game type
     * @param customOptions custom game options
     * @return the created party queue info, or null if already in queue
     */
    public PartyQueueInfo addToPartyQueue(Player leader, List<Player> members,
                                           String gameId, String gameType,
                                           Map<String, Object> customOptions) {
        Objects.requireNonNull(leader);
        Objects.requireNonNull(members);
        Objects.requireNonNull(gameId);
        Objects.requireNonNull(gameType);

        if (members.isEmpty() || !members.contains(leader)) {
            return null;
        }

        UUID partyId = UUID.randomUUID();
        long timestamp = System.currentTimeMillis();
        String redisKey = buildPartyRedisKey(gameId, gameType);

        MatchQueue.PartyInfo partyInfo = null;
        List<MatchQueue.QueueEntry> entries = new ArrayList<>();

        for (Player member : members) {
            UUID uuid = member.getUniqueId();

            MatchQueue.QueueEntry entry = new MatchQueue.QueueEntry(
                    uuid,
                    member.getName(),
                    gameId,
                    gameType,
                    new LinkedHashMap<>(customOptions != null ? customOptions : Map.of()),
                    null,
                    timestamp
            );

            String serialized = JsonUtils.toJson(entry);
            redis.zAdd(redisKey, timestamp, serialized);

            Map<String, String> stateData = new HashMap<>();
            stateData.put("sessionId", UUID.randomUUID().toString());
            stateData.put("gameType", gameType);
            stateData.put("timestamp", String.valueOf(timestamp));
            stateData.put("partyId", partyId.toString());
            stateData.put("gameId", gameId);
            stateData.put("playerName", member.getName());
            redis.hmset(buildStateKey(uuid), stateData);
            redis.expire(buildStateKey(uuid), 300);

            entries.add(entry);
        }

        partyInfo = new MatchQueue.PartyInfo(partyId, entries);

        for (MatchQueue.QueueEntry entry : entries) {
            entry.party = partyInfo;
        }

        LOGGER.info("Party " + partyId + " (" + members.size() + " members, leader: " +
                leader.getName() + ") joined party queue: " + gameType);

        return new PartyQueueInfo(partyId, gameId, gameType, members.size(), timestamp);
    }

    /**
     * Removes all party members from the party queue.
     *
     * @param partyId the party's UUID
     * @param members the party members
     */
    public void removeFromPartyQueue(UUID partyId, List<Player> members) {
        if (partyId == null || members == null) return;

        String redisKey = "partygame:party:";
        for (Player member : members) {
            redis.del(buildStateKey(member.getUniqueId()));
        }

        LOGGER.info("Party " + partyId + " removed from party queue (" + members.size() + " members)");
    }

    /**
     * Counts the number of unique parties in the queue for a game type.
     *
     * @param gameId   the game identifier
     * @param gameType the game type
     * @return the number of unique party IDs in the queue
     */
    @Deprecated
    public int getPartyQueueSize(String gameId, String gameType) {
        String key = buildPartyRedisKey(gameId, gameType);
        Set<String> members = redis.zRange(key, 0, -1);
        if (members == null || members.isEmpty()) return 0;

        Set<String> partyIds = new HashSet<>();
        for (String serialized : members) {
            try {
                Map<String, Object> parsed = JsonUtils.parseMap(serialized);
                String partyId = (String) parsed.get("partyId");
                if (partyId != null) {
                    partyIds.add(partyId);
                }
            } catch (Exception ignored) {}
        }

        return partyIds.size();
    }

    /**
     * Gets the next batch of up to N parties for matching.
     *
     * @param maxParties the maximum number of parties to retrieve
     * @param gameId     the game identifier
     * @param gameType   the game type
     * @return a list of party queue entries, or empty if none available
     */
    @Deprecated
    public List<MatchQueue.PartyInfo> getPartyNextBatch(int maxParties, String gameId, String gameType) {
        String key = buildPartyRedisKey(gameId, gameType);
        Set<String> members = redis.zRange(key, 0, maxParties - 1);
        if (members == null || members.isEmpty()) return Collections.emptyList();

        List<MatchQueue.PartyInfo> result = new ArrayList<>();
        Map<String, List<MatchQueue.QueueEntry>> partyGroups = new LinkedHashMap<>();

        for (String serialized : members) {
            try {
                MatchQueue.QueueEntry entry = JsonUtils.fromJson(serialized, MatchQueue.QueueEntry.class);
                if (entry != null && entry.partyId != null) {
                    partyGroups.computeIfAbsent(entry.partyId.toString(), k -> new ArrayList<>()).add(entry);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to deserialize party queue entry", e);
            }
        }

        for (List<MatchQueue.QueueEntry> group : partyGroups.values()) {
            if (!group.isEmpty()) {
                UUID partyId = group.get(0).partyId;
                result.add(new MatchQueue.PartyInfo(partyId, group));
            }
        }

        return result;
    }

    /**
     * Returns whether a party member is currently in any party queue.
     *
     * @param member the party member to check
     * @return true if the member is in a party queue
     */
    public boolean isMemberInPartyQueue(Player member) {
        return redis.hget(buildStateKey(member.getUniqueId()), "partyId") != null;
    }

    /**
     * Gets all members of a specific party from the queue.
     *
     * @param partyId  the party's UUID
     * @param gameId   the game identifier
     * @param gameType the game type
     * @return list of party members in the queue
     */
    public List<MatchQueue.QueueEntry> getPartyMembers(UUID partyId, String gameId, String gameType) {
        String key = buildPartyRedisKey(gameId, gameType);
        Set<String> members = redis.zRange(key, 0, -1);
        if (members == null) return Collections.emptyList();

        List<MatchQueue.QueueEntry> result = new ArrayList<>();
        for (String serialized : members) {
            try {
                Map<String, Object> parsed = JsonUtils.parseMap(serialized);
                String pid = (String) parsed.get("partyId");
                if (pid != null && pid.equals(partyId.toString())) {
                    MatchQueue.QueueEntry entry = JsonUtils.fromJson(serialized, MatchQueue.QueueEntry.class);
                    if (entry != null) {
                        entry.party = new MatchQueue.PartyInfo(partyId, result);
                        result.add(entry);
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private String buildPartyRedisKey(String gameId, String gameType) {
        return "partygame:party:" + gameId + ":" + gameType + ":queue";
    }

    private String buildStateKey(UUID uuid) {
        return "partygame:match:state:" + uuid.toString();
    }

    /**
     * Simple info holder for a party queue registration.
     */
    public static class PartyQueueInfo implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public final UUID partyId;
        public final String gameId;
        public final String gameType;
        public final int memberCount;
        public final long registeredAt;

        public PartyQueueInfo(UUID partyId, String gameId, String gameType,
                              int memberCount, long registeredAt) {
            this.partyId = Objects.requireNonNull(partyId);
            this.gameId = Objects.requireNonNull(gameId);
            this.gameType = Objects.requireNonNull(gameType);
            this.memberCount = memberCount;
            this.registeredAt = registeredAt;
        }
    }
}
