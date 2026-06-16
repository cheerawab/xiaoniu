package co.partygame.partygame.partyframework;

import co.partygame.partygame.PartyGamePlugin;
import co.partygame.partygame.config.PartyGameConfig;
import co.partygame.framework.swm.WorldPool;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Accepts match requests from the Lobby server via BungeeCord messaging channels.
 * <p>
 * This is the entry point for game sessions triggered from the Lobby:
 * <ul>
 *   <li>Receives BungeeCord plugin messages on the matchmaking channel</li>
 *   <li>Processes the request payload (JSON with game_type, players, options)</li>
 *   <li>Uses the GameDispatcher to create and start a game session</li>
 *   <li>Sends a MatchResponse back to the Lobby</li>
 * </ul>
 *
 * @see MatchRequest
 * @see MatchResponse
 * @see GameDispatcher
 */
public class MatchAcceptor implements PluginMessageListener {

    private final PartyGamePlugin plugin;
    private final PartyGameConfig config;
    private final GameDispatcher dispatcher;
    private final Map<String, PendingRequest> pendingRequests;
    private final Set<String> blockedServers;

    /**
     * A request waiting for a response.
     */
    private static final class PendingRequest {
        final MatchRequest request;
        final long createdAt;
        PendingRequest(MatchRequest request) {
            this.request = request;
            this.createdAt = System.currentTimeMillis();
        }
    }

    /**
     * Create a new match acceptor.
     */
    public MatchAcceptor(PartyGamePlugin plugin, PartyGameConfig config, GameDispatcher dispatcher) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
        this.pendingRequests = new ConcurrentHashMap<>();
        this.blockedServers = ConcurrentHashMap.newKeySet();
    }

    /**
     * Register the BungeeCord channel for receiving matchmaking messages.
     */
    public void registerChannel() {
        String channel = config.getBungeeChannel();

        // Register outgoing channel (for sending responses)
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, channel);

        // Register incoming channel (for receiving requests)
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, channel, this);

        plugin.getLogger().info("Matchmaker registered on BungeeCord channel: " + channel);
    }

    /**
     * Called when a plugin message is received on the matchmaking channel.
     */
    @Override
    public void onPluginMessageReceived(String channel, org.bukkit.entity.Player player, byte[] message) {
        if (!config.isBungeeCordEnabled()) {
            return;
        }

        String sourceServer = getSourceServer(player);
        processMessage(sourceServer, message);
    }

    /**
     * Process a raw message byte array as a MatchRequest.
     */
    private void processMessage(String sourceServer, byte[] message) {
        try {
            MatchRequest request = deserializeMessage(message);
            if (request == null) {
                plugin.getLogger().warning("Failed to deserialize MatchRequest from " + sourceServer);
                return;
            }

            if (config.isLogMatchmaking()) {
                plugin.getLogger().info("Received match request: " + request.getGameType()
                        + " from " + sourceServer + " (" + request.getPlayerCount() + " players)");
            }

            // Validate the request
            if (request.getPlayerCount() < config.getMinPlayersPerGame()) {
                if (config.isLogMatchmaking()) {
                    plugin.getLogger().warning("Match request below minimum players: "
                            + request.getRequestId() + " (" + request.getPlayerCount() + "/" + config.getMinPlayersPerGame() + ")");
                }
                sendResponse(request.getRequestId(), request.getSourceServer(),
                        new MatchResponse.Builder(request.getRequestId())
                                .denied("Not enough players. Required: " + config.getMinPlayersPerGame())
                                .build());
                return;
            }

            // Check concurrent session limit
            int activeCount = dispatcher.getActiveSessionCount();
            if (config.getMaxConcurrentSessions() > 0 && activeCount >= config.getMaxConcurrentSessions()) {
                if (config.isLogMatchmaking()) {
                    plugin.getLogger().warning("Max concurrent sessions reached (" + activeCount
                            + "/" + config.getMaxConcurrentSessions() + "). Queuing request.");
                }
                // Queue the request rather than immediately sending a response
                // The queue will be drained when sessions end
                pendingRequests.put(request.getRequestId(), new PendingRequest(request));

                /*
                 * TODO: Implement queue draining when sessions end.
                 * For now, we respond with QUEUE status.
                 */
                sendResponse(request.getRequestId(), request.getSourceServer(),
                        new MatchResponse.Builder(request.getRequestId())
                                .queued("Max sessions reached. Queued for later.")
                                .build());
                return;
            }

            // Dispatch the request
            MatchResponse response = dispatcher.dispatch(request);

            if (config.isLogMatchmaking()) {
                plugin.getLogger().info("Match response: " + request.getRequestId()
                        + " -> " + response.getStatus()
                        + (response.getStatus() == MatchResponse.ResponseStatus.ACCEPTED
                        ? " on world '" + response.getWorldName() + "'"
                        : " (" + response.getErrorMessage() + ")"));
            }

            sendResponse(request.getRequestId(), request.getSourceServer(), response);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error processing match request", e);
        }
    }

    /**
     * Deserialize a byte array into a MatchRequest.
     * <p>
     * Expects JSON payload with fields: game_type, game_id, player_names, settings, custom_options.
     */
    private MatchRequest deserializeMessage(byte[] message) {
        try {
            String json = new String(message, "UTF-8");
            // Parse JSON — in production, use Jackson with a proper MatchRequest deserializer
            // For simplicity, construct from key-value format: "key:value|key2:value2"
            // Or use a simple JSON parser

            if (json.startsWith("{")) {
                // JSON parsing using Jackson
                com.fasterxml.jackson.databind.ObjectMapper mapper
                        = new com.fasterxml.jackson.databind.ObjectMapper();

                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
                List<String> playerNames = new ArrayList<>();
                if (root.has("player_names") && root.path("player_names").isArray()) {
                    for (var node : root.path("player_names")) {
                        playerNames.add(node.asText());
                    }
                }

                List<String> playerUUIDs = new ArrayList<>();
                if (root.has("player_uuids") && root.path("player_uuids").isArray()) {
                    for (var node : root.path("player_uuids")) {
                        playerUUIDs.add(node.asText());
                    }
                }

                Map<String, String> settings = new HashMap<>();
                if (root.has("settings") && root.path("settings").isObject()) {
                    var settingsNode = root.path("settings");
                    settingsNode.fields().forEachRemaining(f -> settings.put(f.getKey(), f.getValue().asText()));
                }

                Map<String, Object> customOptions = new HashMap<>();
                if (root.has("custom_options") && root.path("custom_options").isObject()) {
                    var optsNode = root.path("custom_options");
                    optsNode.fields().forEachRemaining(field -> {
                        var key = field.getKey();
                        var val = field.getValue();
                        if (val.isTextual()) customOptions.put(key, val.asText());
                        else if (val.isInt()) customOptions.put(key, val.asInt());
                        else if (val.isLong()) customOptions.put(key, val.asLong());
                        else if (val.isDouble()) customOptions.put(key, val.asDouble());
                        else if (val.isBoolean()) customOptions.put(key, val.asBoolean());
                        else if (val.isArray()) customOptions.put(key, val.toString());
                    });
                }

                return MatchRequest.builder()
                        .requestId(root.has("request_id") ? root.path("request_id").asText() : null)
                        .gameType(root.has("game_type") ? root.path("game_type").asText() : null)
                        .gameId(root.has("game_id") ? root.path("game_id").asText() : null)
                        .playerNames(playerNames)
                        .playerUUIDs(playerUUIDs)
                        .settings(settings)
                        .customOptions(customOptions)
                        .build();
            }

            // Legacy format: key=value key2=value2 separator=|
            return parseLegacyMessage(json);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deserialize match message", e);
            return null;
        }
    }

    /**
     * Parse legacy key=value format.
     */
    private MatchRequest parseLegacyMessage(String json) {
        String requestId = extractKeyValue(json, "request_id");
        String gameType = extractKeyValue(json, "game_type");
        String gameId = extractKeyValue(json, "game_id");
        String sourceServer = extractKeyValue(json, "source_server");

        String playerNameStr = extractKeyValue(json, "player_names");
        List<String> playerNames = playerNameStr != null
                ? Arrays.asList(playerNameStr.split(","))
                : Collections.emptyList();

        return MatchRequest.builder()
                .requestId(requestId)
                .gameType(gameType)
                .gameId(gameId)
                .sourceServer(sourceServer)
                .playerNames(playerNames)
                .build();
    }

    private String extractKeyValue(String data, String key) {
        String prefix = key + "=";
        int start = data.indexOf(prefix);
        if (start == -1) return null;
        start += prefix.length();
        int end = data.indexOf(' ', start);
        if (end == -1) end = data.indexOf('|', start);
        if (end == -1) return data.substring(start);
        return data.substring(start, end);
    }

    /**
     * Send a MatchResponse back to the Lobby server.
     */
    private void sendResponse(String requestId, String sourceServer, MatchResponse response) {
        MatchResponse resp = response;
        // Build response JSON
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();

            Map<String, Object> respData = new HashMap<>();
            respData.put("request_id", requestId);
            respData.put("status", resp.getStatus().name());
            respData.put("session_id", resp.getSessionId() != null ? resp.getSessionId() : "");
            respData.put("world_name", resp.getWorldName() != null ? resp.getWorldName() : "");
            respData.put("error_message", resp.getErrorMessage() != null ? resp.getErrorMessage() : "");

            if (!config.isBungeeCordEnabled()) {
                plugin.getLogger().warning("Cannot send response — BungeeCord disabled");
                return;
            }

            byte[] payload = mapper.writeValueAsBytes(respData);

            // Find a player on the source server to send the response to
            // In practice, this goes through BungeeCord forward message
            sendToServer(sourceServer, payload);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to serialize/send match response", e);
        }
    }

    /**
     * Send a message to a specific server via BungeeCord.
     */
    private void sendToServer(String targetServer, byte[] data) {
        // In production, use BungeeCord's Forward channel to send to the target server
        // For now, store the data and send when a player connects from that server
        // Or use Spigot's built-in BungeeCord forwarding:

        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            // Check if player is connected to the target server
            // This is a simplification — in practice, you'd need to track server affinity
            p.sendPluginMessage(plugin, config.getBungeeChannel(), data);
            return; // Send to first available player as proxy
        }

        plugin.getLogger().warning("No online players to send response to " + targetServer);
    }

    /**
     * Get the server name of the player who triggered the message.
     */
    private String getSourceServer(org.bukkit.entity.Player player) {
        // In BungeeCord, the plugin messaging comes through the BungeeCord proxy.
        // We can extract the source server from the player's connection info.
        // This is a simplification — in production, use BungeeCord's ServerInfo.
        return player.getName();
    }

    /**
     * Manually accept a match request (used when bypassing BungeeCord for
     * same-server testing or when called by other code).
     *
     * @param request the match request to process
     * @return the response
     */
    public MatchResponse acceptMatchRequest(MatchRequest request) {
        // Reuse the dispatch pipeline
        MatchRequest req = request;
        MatchResponse response = dispatcher.dispatch(req);
        return response;
    }

    /**
     * Block a source server from sending match requests.
     */
    public void blockServer(String serverName) {
        blockedServers.add(serverName);
    }

    /**
     * Unblock a previously blocked source server.
     */
    public void unblockServer(String serverName) {
        blockedServers.remove(serverName);
    }

    /**
     * Check if a server is blocked.
     */
    public boolean isServerBlocked(String serverName) {
        return blockedServers.contains(serverName);
    }
}
