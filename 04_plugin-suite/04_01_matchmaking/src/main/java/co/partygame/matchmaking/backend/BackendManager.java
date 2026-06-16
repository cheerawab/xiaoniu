package co.partygame.matchmaking.backend;

import co.partygame.common.redis.RedisManager;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages backend server list from Redis.
 * Uses Redis key "partygame:backend:list" (SET) for the list of server names.
 * Listens to Redis Pub/Sub for health updates.
 */
public class BackendManager {

    private static final Logger LOGGER = Logger.getLogger(BackendManager.class.getName());
    private static final String BACKEND_LIST_KEY = "partygame:backend:list";

    private final RedisManager redis;
    private final Map<String, BackendInfo> backends = new LinkedHashMap<>();
    private volatile List<String> registeredNames = new ArrayList<>();

    public BackendManager(RedisManager redis) {
        this.redis = Objects.requireNonNull(redis);
    }

    /**
     * Loads the backend list from Redis SET.
     *
     * @return the list of backend server names
     */
    public List<String> loadBackendList() {
        Set<String> members = redis.smember(BACKEND_LIST_KEY);
        registeredNames = members != null ? new ArrayList<>(members) : new ArrayList<>();
        LOGGER.info("Loaded " + registeredNames.size() + " backends from Redis");
        return registeredNames;
    }

    /**
     * Registers a backend server to Redis SET.
     * Notifies all backends when a new one joins.
     *
     * @param name the backend server name
     */
    public void registerBackend(String name) {
        Objects.requireNonNull(name);
        redis.sadd(BACKEND_LIST_KEY, name);
        if (!registeredNames.contains(name)) {
            registeredNames.add(name);
            backends.putIfAbsent(name, new BackendInfo(name));
        }
        publishEvent("BACKEND_REGISTERED:" + name);
        LOGGER.info("Backend registered: " + name);
    }

    /**
     * Unregisters a backend from Redis SET.
     *
     * @param name the backend server name
     */
    public void unregisterBackend(String name) {
        Objects.requireNonNull(name);
        redis.del(BACKEND_LIST_KEY);
        Set<String> members = redis.smember(BACKEND_LIST_KEY);
        if (members != null) {
            registeredNames = new ArrayList<>(members);
        }
        backends.remove(name);
        publishEvent("BACKEND_UNREGISTERED:" + name);
        LOGGER.info("Backend unregistered: " + name);
    }

    /**
     * Updates the health status of a backend server in Redis.
     *
     * @param name    the backend server name
     * @param online  whether the server is online
     */
    public void pingBackend(String name, boolean online) {
        Objects.requireNonNull(name);
        String key = "partygame:health:" + name;
        Map<String, String> health = new HashMap<>();
        health.put("lastPing", String.valueOf(System.currentTimeMillis()));
        health.put("isHealthy", String.valueOf(online));
        health.put("status", online ? "online" : "offline");
        redis.hmset(key, health);

        BackendInfo info = backends.get(name != null);
        if (info != null) info.setOnline(online);
    }

    /**
     * Gets the list of currently online backend servers.
     *
     * @return list of backend info objects
     */
    public List<BackendInfo> getOnlineBackends() {
        List<BackendInfo> result = new ArrayList<>();
        for (BackendInfo info : backends.values()) {
            if (info.isOnline()) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * Gets detailed info for a specific backend server.
     *
     * @param name the backend server name
     * @return the backend info, or null if not found
     */
    public BackendInfo getBackendInfo(String name) {
        if (name == null) return null;
        BackendInfo info = backends.get(name);
        if (info != null) {
            String key = "partygame:health:" + name;
            String playersStr = redis.hget(key, "currentPlayers");
            String capacityStr = redis.hget(key, "capacity");
            if (playersStr != null) info.setCurrentPlayers(Integer.parseInt(playersStr));
            if (capacityStr != null) info.setCapacity(Integer.parseInt(capacityStr));
        }
        return info;
    }

    /**
     * Gets the name of a backend server by index.
     *
     * @param index the index
     * @return the server name, or null if out of bounds
     */
    public String getBackendName(int index) {
        if (index < 0 || index >= registeredNames.size()) return null;
        return registeredNames.get(index);
    }

    /**
     * Updates backend statistics (currentPlayers, capacity).
     *
     * @param name       the backend server name
     * @param currentPlayers the current number of players
     * @param capacity   the maximum capacity
     */
    public void updateBackendStats(String name, int currentPlayers, int capacity) {
        Objects.requireNonNull(name);
        BackendInfo info = backends.computeIfAbsent(name, BackendInfo::new);
        info.setCurrentPlayers(currentPlayers);
        info.setCapacity(capacity);

        String key = "partygame:health:" + name;
        Map<String, String> stats = new HashMap<>();
        stats.put("currentPlayers", String.valueOf(currentPlayers));
        stats.put("capacity", String.valueOf(capacity));
        stats.put("lastPing", String.valueOf(System.currentTimeMillis()));
        redis.hmset(key, stats);
    }

    private void publishEvent(String message) {
        try {
            String channel = "partygame:matchmaking";
            redis.publish(channel, message);
        } catch (Exception ignored) {}
    }
}
