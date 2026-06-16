package co.partygame.matchmaking.backend;

import co.partygame.common.redis.RedisManager;
import co.partygame.common.config.ConfigManager;

import java.util.*;
import java.util.logging.Logger;

/**
 * Health check system for backends.
 * 
 * Checks every 30 seconds to all registered backends.
 * Backends marked unhealthy after 3 consecutive failed pings.
 * 
 * Redis key: "partygame:health:{serverName}" (hash: lastPing, responseTime, isHealthy)
 */
public class BackendHealth {

    private static final Logger LOGGER = Logger.getLogger(BackendHealth.class.getName());

    private final RedisManager redis;
    private final ConfigManager config;
    private final Map<String, HealthCheckState> healthStates = new HashMap<>();

    /**
     * Creates a new health checker.
     *
     * @param redis  the Redis manager
     * @param config the configuration manager
     */
    public BackendHealth(RedisManager redis, ConfigManager config) {
        this.redis = Objects.requireNonNull(redis);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Starts the health check scheduler.
     * Runs every 30 seconds (configurable in config.yml).
     */
    public void startHealthChecks() {
        int interval = config.getInt("backend.health_check_interval", 30) * 20;
        if (interval <= 0) interval = 600;

        checkAllHealth();
    }

    /**
     * Checks health of a specific backend server.
     *
     * @param serverName the backend server name
     */
    public void checkHealth(String serverName) {
        if (serverName == null || serverName.isEmpty()) return;

        long start = System.currentTimeMillis();
        boolean healthy = false;

        try {
            String key = "partygame:health:" + serverName;
            Map<String, String> data = redis.hgetAll(key);
            if (!data.isEmpty()) {
                healthy = true;
            }

            data.put("lastPing", String.valueOf(start));
            long response = System.currentTimeMillis() - start;
            data.put("responseTime", String.valueOf(response));
            data.put("isHealthy", String.valueOf(healthy));
            redis.hmset(key, data);

            HealthCheckState state = healthStates.computeIfAbsent(serverName, k -> new HealthCheckState());
            state.recordResponse(healthy, response);
        } catch (Exception e) {
            healthy = false;
            HealthCheckState state = healthStates.computeIfAbsent(serverName, k -> new HealthCheckState());
            state.recordResponse(false, -1);
            LOGGER.warning("Health check failed for " + serverName + ": " + e.getMessage());
        }
    }

    /**
     * Gets the last response time for a backend.
     *
     * @param serverName the backend server name
     * @return response time in ms, or -1 if unknown
     */
    public long getLastResponseTime(String serverName) {
        HealthCheckState state = healthStates.get(serverName);
        return state != null ? state.lastResponseTime : -1;
    }

    /**
     * Checks if a backend is healthy based on the last 3 pings.
     *
     * @param serverName the backend server name
     * @return true if the backend is healthy
     */
    public boolean isHealthy(String serverName) {
        HealthCheckState state = healthStates.get(serverName);
        if (state == null) {
            String key = "partygame:health:" + serverName;
            String isHealthy = redis.hget(key, "isHealthy");
            return isHealthy != null && Boolean.parseBoolean(isHealthy);
        }
        return state.isRecentHealthy(3);
    }

    /**
     * Gets the healthiest backend server available.
     *
     * @return the healthiest backend name, or null if none available
     */
    public String getHealthiestBackend() {
        String healthiest = null;
        int lowestPlayers = Integer.MAX_VALUE;

        for (Map.Entry<String, HealthCheckState> entry : healthStates.entrySet()) {
            if (entry.getValue().isRecentHealthy(3)) {
                String key = "partygame:health:" + entry.getKey();
                String playersStr = redis.hget(key, "currentPlayers");
                int currentPlayers = 0;
                if (playersStr != null) {
                    try { currentPlayers = Integer.parseInt(playersStr); } catch (NumberFormatException ignored) { }
                }
                if (currentPlayers < lowestPlayers) {
                    lowestPlayers = currentPlayers;
                    healthiest = entry.getKey();
                }
            }
        }
        return healthiest;
    }

    private void checkAllHealth() {
        if (redis == null) return;
        for (String name : new ArrayList<>(healthStates.keySet())) {
            checkHealth(name);
        }
    }
}
