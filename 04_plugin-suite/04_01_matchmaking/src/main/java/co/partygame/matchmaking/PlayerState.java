package co.partygame.matchmaking;

import co.partygame.common.redis.RedisManager;
import co.partygame.common.util.JsonUtils;
import co.partygame.matchmaking.MatchmakingPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks each player's matchmaking state across the lobby.
 * Syncs state with Redis for cross-lobby consistency.
 */
public class PlayerState {

    private static final long COOLDOWN_MS = 5000L;

    private final Map<UUID, PlayerStateEntry> states = new ConcurrentHashMap<>();

    /**
     * Enum representing a player's matchmaking state.
     */
    public enum State {
        IDLE,
        WAITING,
        MATCHED,
        PLAYING,
        BACKINLOBBY
    }

    /**
     * Internal entry representing a player's full state.
     */
    public static class PlayerStateEntry {
        public final UUID uuid;
        public State state;
        public String currentGameId;
        public String currentBackend;
        public long joinTime;
        public String sessionId;
        public UUID partyId;
        public long lastMatchTime;

        public PlayerStateEntry(UUID uuid, State state) {
            this.uuid = Objects.requireNonNull(uuid);
            this.state = state;
            this.state = State.IDLE;
            this.joinTime = 0;
            this.sessionId = null;
            this.partyId = null;
            this.lastMatchTime = 0;
        }

        public State getState() {
            return state;
        }

        public long getJoinTime() {
            return joinTime;
        }

        public long getCooldown() {
            long elapsed = System.currentTimeMillis() - lastMatchTime;
            return Math.max(0, COOLDOWN_MS - elapsed);
        }

        public boolean isInCooldown() {
            return System.currentTimeMillis() - lastMatchTime < COOLDOWN_MS;
        }

        public void setState(State state) {
            this.state = state;
            if (state == State.MATCHED || state == State.PLAYING) {
                this.lastMatchTime = System.currentTimeMillis();
            }
            if (state == State.IDLE) {
                this.joinTime = 0;
                this.sessionId = null;
                this.currentGameId = null;
                this.currentBackend = null;
            }
        }

        Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();
            map.put("uuid", uuid.toString());
            map.put("state", state.name());
            map.put("currentGameId", currentGameId != null ? currentGameId : "");
            map.put("currentBackend", currentBackend != null ? currentBackend : "");
            map.put("joinTime", String.valueOf(joinTime));
            map.put("sessionId", sessionId != null ? sessionId : "");
            map.put("partyId", partyId != null ? partyId.toString() : "");
            map.put("lastMatchTime", String.valueOf(lastMatchTime));
            return map;
        }

        static PlayerStateEntry fromMap(Map<String, String> map) {
            if (map == null) return null;
            String uuidStr = map.get("uuid");
            if (uuidStr == null) return null;
            UUID uuid = UUID.fromString(uuidStr);
            PlayerStateEntry entry = new PlayerStateEntry(uuid, State.IDLE);

            try {
                State s = State.valueOf(map.getOrDefault("state", "IDLE"));
                entry.state = s;
            } catch (IllegalArgumentException e) {
                entry.state = State.IDLE;
            }

            String gameId = map.get("currentGameId");
            entry.currentGameId = gameId.isEmpty() ? null : gameId;
            String backend = map.get("currentBackend");
            entry.currentBackend = backend.isEmpty() ? null : backend;
            String jjt = map.get("joinTime");
            try { entry.joinTime = Long.parseLong(jjt); } catch (NumberFormatException e) {}
            String sid = map.get("sessionId");
            entry.sessionId = sid.isEmpty() ? null : sid;
            String pid = map.get("partyId");
            entry.partyId = pid.isEmpty() ? null : UUID.fromString(pid);
            String lmt = map.get("lastMatchTime");
            try { entry.lastMatchTime = Long.parseLong(lmt); } catch (NumberFormatException e) {}

            return entry;
        }
    }

    /**
     * Sets a player's matchmaking state.
     *
     * @param uuid   the player's UUID
     * @param state  the new state
     */
    public void setState(UUID uuid, State state) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(state);
        states.compute(uuid, (u, existing) -> {
            if (existing == null) {
                existing = new PlayerStateEntry(uuid, state);
            }
            existing.setState(state);
            return existing;
        });
        saveToRedis(uuid);
    }

    /**
     * Gets a player's current state.
     *
     * @param uuid the player's UUID
     * @return the player's state, or State.IDLE if not found
     */
    public State getState(UUID uuid) {
        PlayerStateEntry entry = states.get(uuid);
        return entry != null ? entry.state : State.IDLE;
    }

    /**
     * Gets a player's join timestamp.
     *
     * @param uuid the player's UUID
     * @return the join time in milliseconds, or 0 if no entry
     */
    public long getJoinTime(UUID uuid) {
        PlayerStateEntry entry = states.get(uuid);
        return entry != null ? entry.joinTime : 0;
    }

    /**
     * Returns the remaining cooldown time for a player.
     *
     * @param uuid the player's UUID
     * @return cooldown remaining in milliseconds, or 0 if no cooldown
     */
    public long getCooldown(UUID uuid) {
        PlayerStateEntry entry = states.get(uuid);
        return entry != null ? entry.getCooldown() : 0;
    }

    /**
     * Checks if a player is in a cooldown period between matches.
     *
     * @param uuid the player's UUID
     * @return true if the player is in cooldown
     */
    public boolean isInCooldown(UUID uuid) {
        PlayerStateEntry entry = states.get(uuid);
        return entry != null && entry.isInCooldown();
    }

    /**
     * Sets the current game ID for a player.
     *
     * @param uuid       the player's UUID
     * @param gameId     the game identifier
     * @param backend    the backend server name
     * @param sessionId  the matchmaking session ID
     */
    public void setGameSession(UUID uuid, String gameId, String backend, String sessionId) {
        states.compute(uuid, (u, existing) -> {
            if (existing == null) {
                existing = new PlayerStateEntry(uuid, State.IDLE);
            }
            existing.currentGameId = gameId;
            existing.currentBackend = backend;
            existing.sessionId = sessionId;
            existing.joinTime = System.currentTimeMillis();
            return existing;
        });
        saveToRedis(uuid);
    }

    /**
     * Sets the party ID for a player.
     *
     * @param uuid    the player's UUID
     * @param partyId the party's UUID
     * @param state   the current state to set
     */
    public void setParty(UUID uuid, UUID partyId, State state) {
        states.compute(uuid, (u, existing) -> {
            if (existing == null) {
                existing = new PlayerStateEntry(uuid, State.IDLE);
            }
            existing.partyId = partyId;
            existing.setState(state);
            return existing;
        });
        saveToRedis(uuid);
    }

    /**
     * Saves a player's state to Redis.
     *
     * @param uuid the player's UUID
     */
    public void saveToRedis(UUID uuid) {
        PlayerStateEntry entry = states.get(uuid);
        if (entry == null) return;

        RedisManager redis = MatchmakingPlugin.getInstance().getRedisManager();
        if (redis == null) return;

        String key = buildRedisKey(uuid);
        Map<String, String> map = entry.toMap();
        redis.hmset(key, map);
        redis.expire(key, 600);
    }

    /**
     * Loads a player's state from Redis.
     *
     * @param uuid the player's UUID
     * @return the loaded state, or null if not found in Redis
     */
    public PlayerStateEntry loadFromRedis(UUID uuid) {
        RedisManager redis = MatchmakingPlugin.getInstance().getRedisManager();
        if (redis == null) return null;

        String key = buildRedisKey(uuid);
        Map<String, String> map = redis.hgetAll(key);
        if (map.isEmpty()) return null;

        return PlayerStateEntry.fromMap(map);
    }

    /**
     * Syncs all local states with Redis.
     */
    public void syncWithRedis() {
        for (Map.Entry<UUID, PlayerStateEntry> entry : states.entrySet()) {
            saveToRedis(entry.getKey());
        }
    }

    /**
     * Gets all player states.
     *
     * @return unmodifiable map of UUID to PlayerStateEntry
     */
    public Map<UUID, PlayerStateEntry> getAllStates() {
        return Collections.unmodifiableMap(states);
    }

    /**
     * Removes a player's state.
     *
     * @param uuid the player's UUID
     */
    public void remove(UUID uuid) {
        states.remove(uuid);
        saveToRedis(uuid);
        RedisManager redis = MatchmakingPlugin.getInstance().getRedisManager();
        if (redis != null) {
            redis.del(buildRedisKey(uuid));
        }
    }

    /**
     * Gets all players in a specific state.
     *
     * @param state the state to filter by
     * @return list of UUIDs for players in that state
     */
    public List<UUID> getPlayersByState(State state) {
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, PlayerStateEntry> entry : states.entrySet()) {
            if (entry.getValue().state == state) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private String buildRedisKey(UUID uuid) {
        return "partygame:player:state:" + uuid.toString();
    }
}
