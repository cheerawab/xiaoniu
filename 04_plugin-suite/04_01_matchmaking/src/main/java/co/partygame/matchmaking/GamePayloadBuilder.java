package co.partygame.matchmaking;

import co.partygame.common.protocol.packets.lobby.MatchRequest;
import co.partygame.common.protocol.packets.lobby.PlayerInfo;
import co.partygame.common.protocol.packets.backend.MatchAccepted;
import co.partygame.common.redis.RedisManager;
import co.partygame.common.util.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Builds the game_payload JSON for MatchRequest sent to backend servers.
 * Completely generic - works with any game configuration.
 */
public class GamePayloadBuilder {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};
    private final Map<String, GameTypeConfig> gameTypeConfigs = new LinkedHashMap<>();

    /**
     * Registers a game type configuration for custom options merging and validation.
     *
     * @param config the game type configuration containing defaults and schema
     */
    public void registerGameType(GameTypeConfig config) {
        gameTypeConfigs.put(config.name, config);
    }

    /**
     * Builds the game_payload JSON for a match request.
     *
     * @param gameId        the game identifier (e.g., "survival_001")
     * @param gameType      the game type (e.g., "partygame")
     * @param customOptions user-provided custom options
     * @param worldConfig   world configuration for the game
     * @return JSON string representing the game payload
     */
    public String buildPayload(String gameId, String gameType, Map<String, Object> customOptions,
                               String worldConfig) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", UUID.randomUUID().toString());
        payload.put("game_id", gameId);
        payload.put("game_type", gameType);

        Map<String, Object> settings = mergeCustomOptions(
                gameTypeConfigs.get(gameType), customOptions);
        payload.put("settings", settings);

        payload.put("world_config", JsonUtils.parseMap(worldConfig != null ? worldConfig : "{}"));

        try {
            return JsonUtils.toJson(payload);
        } catch (Exception e) {
            return "{\"error\":\"failed_to_build_payload\"}";
        }
    }

    /**
     * Builds a MatchRequest packet for sending to a backend server.
     *
     * @param sessionId       the unique session identifier
     * @param players         list of players participating
     * @param gameId          the game identifier
     * @param gameType        the game type
     * @param gamePayloadJson the JSON game payload
     * @param partyId         the party UUID (if matching as a party)
     * @param sourceServer    the source lobby server name
     * @return a new MatchRequest object
     */
    public MatchRequest buildMatchRequest(UUID sessionId, List<PlayerInfo> players,
                                           String gameId, String gameType,
                                           String gamePayloadJson, UUID partyId,
                                           String sourceServer) {
        Map<String, Object> customOptions = new HashMap<>();

        try {
            Map<String, Object> gamePayload = JsonUtils.parseMap(gamePayloadJson);
            if (gamePayload.containsKey("settings")) {
                Object settings = gamePayload.get("settings");
                if (settings instanceof Map) {
                    customOptions.putAll((Map<String, Object>) settings);
                }
            }
        } catch (Exception ignored) {}

        return new MatchRequest(
                sessionId.toString(),
                gameId,
                gameType,
                players,
                customOptions.isEmpty() ? null : customOptions,
                partyId,
                sourceServer
        );
    }

    /**
     * Merges game type defaults with user-provided options.
     * User options override defaults.
     *
     * @param config        the game type config (may be null)
     * @param customOptions user-provided options
     * @return merged configuration map
     */
    public Map<String, Object> mergeCustomOptions(GameTypeConfig config, Map<String, Object> customOptions) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (config != null && config.defaults != null) {
            merged.putAll(config.defaults);
        }
        if (customOptions != null) {
            merged.putAll(customOptions);
        }
        return merged;
    }

    /**
     * Gets the registered game type configuration by name.
     *
     * @param name the game type name
     * @return the config, or null if not registered
     */
    public GameTypeConfig getGameTypeConfig(String name) {
        return gameTypeConfigs.get(name);
    }

    /**
     * Parses a MatchAccepted JSON string (from Redis pub/sub) into a MatchAccepted object.
     *
     * @param json the JSON string
     * @return a MatchAccepted object, or null if parsing fails
     */
    public MatchAccepted parseMatchAccepted(String json) {
        try {
            Map<String, Object> map = JsonUtils.parseMap(json);
            String sessionId = (String) map.get("session_id");
            String gameType = (String) map.get("game_type");
            String world = (String) map.get("world");
            String server = (String) map.get("server");
            String payload = (String) map.get("game_payload");

            if (sessionId == null) return null;

            List<PlayerInfo> players = new ArrayList<>();
            Object playersObj = map.get("players");
            if (playersObj instanceof List) {
                for (Object p : (List<?>) playersObj) {
                    if (p instanceof Map) {
                        Map<String, Object> pm = (Map<String, Object>) p;
                        String uuidStr = (String) pm.get("uuid");
                        String name = (String) pm.get("name");
                        if (uuidStr != null) {
                            players.add(new PlayerInfo(UUID.fromString(uuidStr), name != null ? name : "Unknown"));
                        }
                    }
                }
            }

            return new MatchAccepted(sessionId, gameType, world, server, players, payload);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Configuration for a game type's default settings and schema.
     */
    public static class GameTypeConfig implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public final String name;
        public final Map<String, Object> defaults;
        public final Map<String, Object> schema;
        public final int defaultSlot;

        public GameTypeConfig(String name, Map<String, Object> defaults,
                              Map<String, Object> schema, int defaultSlot) {
            this.name = Objects.requireNonNull(name);
            this.defaults = defaults != null ? defaults : new LinkedHashMap<>();
            this.schema = schema != null ? schema : new LinkedHashMap<>();
            this.defaultSlot = defaultSlot;
        }

        /**
         * Validates a custom option value against the schema.
         *
         * @param key   the option key
         * @param value the value to validate
         * @return true if the value is valid
         */
        @SuppressWarnings("unchecked")
        public boolean validateOption(String key, Object value) {
            Map<String, Object> field = schema.get(key) instanceof Map
                    ? (Map<String, Object>) schema.get(key) : null;
            if (field == null) return true;

            String type = (String) field.get("type");
            if (type == null) return true;

            if ("int".equals(type) || "number".equals(type)) {
                if (!(value instanceof Number)) return false;
                double v = ((Number) value).doubleValue();
                double min = field.containsKey("min") ? ((Number) field.get("min")).doubleValue() : Double.MIN_VALUE;
                double max = field.containsKey("max") ? ((Number) field.get("max")).doubleValue() : Double.MAX_VALUE;
                return v >= min && v <= max;
            }
            if ("boolean".equals(type)) {
                return value instanceof Boolean;
            }
            if ("enum".equals(type)) {
                List<String> values = (List<String>) field.get("enum");
                return values != null && values.contains(value.toString());
            }
            if ("string".equals(type)) {
                return value instanceof String;
            }
            return true;
        }
    }
}
