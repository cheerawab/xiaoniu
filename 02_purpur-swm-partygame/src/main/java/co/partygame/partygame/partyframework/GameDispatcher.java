package co.partygame.partygame.partyframework;

import co.partygame.partygame.PartyGamePlugin;
import co.partygame.partygame.config.PartyGameConfig;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Routes game requests to the appropriate game handler based on game type.
 * <p>
 * The GameDispatcher is the central coordinator between incoming requests (from
 * the Lobby via MatchAcceptor) and individual game-specific logic:
 * <ul>
 *   <li>Receives a game_type from a MatchRequest</li>
 *   <li>Finds or creates a game handler for that type</li>
 *   <li>Invokes the handler to create a session, allocate worlds, and start</li>
 * </ul>
 * <p>
 * Currently, the framework provides default handlers for common game types.
 * Specific handlers (BedWarsHandler, SkyWarsHandler, etc.) can be registered
 * via {@link #registerHandler(String, GameHandler)}.
 *
 * @see GameHandler
 * @see GameSession
 * @see MatchRequest
 * @see MatchAcceptor
 */
public class GameDispatcher {

    /**
     * A game handler interface that provides game-specific logic.
     * Each game type (BedWars, SkyWars, etc.) should implement this.
     */
    public interface GameHandler {
        /**
         * Name of this game handler (e.g., "bedwars", "skywars").
         */
        String getGameTypeName();

        /**
         * Prepare a world for this game. Can create instance from template.
         *
         * @param worldPool the SWM framework's WorldPool (use reflection to call methods)
         * @param options custom options from the MatchRequest
         * @return the allocated world name, or null if allocation failed
         */
        String prepareWorld(Object worldPool, Map<String, Object> options);

        /**
         * Perform initial setup for a game session (place blocks, set spawns, etc.).
         * This runs when the world is first loaded.
         *
         * @param session the game session being started
         * @param worldName the allocated world name
         */
        void onWorldInit(GameSession session, String worldName);

        /**
         * Start the game (begin the first round, send messages, etc.).
         *
         * @param session the game session
         * @param players the players in the session
         */
        void onGameStart(GameSession session, List<Player> players);
    }

    private final PartyGamePlugin plugin;
    private final PartyGameConfig config;
    private final Map<String, GameHandler> handlers;
    final List<GameSession> activeSessions;
    private final ConcurrentHashMap<String, GameSession> sessionsById;

    /**
     * Create a new dispatcher backed by the given plugin.
     */
    public GameDispatcher(PartyGamePlugin plugin, PartyGameConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.handlers = new ConcurrentHashMap<>();
        this.activeSessions = Collections.synchronizedList(new ArrayList<>());
        this.sessionsById = new ConcurrentHashMap<>();
    }

    /**
     * Register a game handler for a specific game type.
     *
     * @param gameType the game type identifier (e.g., "bedwars", "skywars")
     * @param handler the handler for that game type
     */
    public void registerHandler(String gameType, GameHandler handler) {
        handlers.put(gameType.toLowerCase(), handler);
        plugin.getLogger().info("Registered game handler for type: " + gameType);
    }

    /**
     * Get a registered game handler by type, or null if not found.
     */
    public GameHandler getHandler(String gameType) {
        return handlers.get(gameType.toLowerCase());
    }

    /**
     * Dispatch a game request — find a handler, create a session, and start the game.
     *
     * @param request the match request from the Lobby
     * @return the response to send back to the Lobby
     */
    public MatchResponse dispatch(MatchRequest request) {
        String gameType = request.getGameType();
        GameHandler handler = handlers.get(gameType.toLowerCase());

        if (handler == null) {
            // Fallback: try a default handler if available
            plugin.getLogger().warning("No specific handler for game type '"
                    + gameType + "'. Trying to create generic session.");
        }

        // Create the session
        String sessionId = UUID.randomUUID().toString();
        GameSession session = new GameSession(sessionId, gameType, config, plugin);

        try {
        // Allocate a world (from template or pre-loaded)
        Map<String, Object> options = request.getCustomOptions();
        Object worldPool = plugin.getWorldPool();
        if (worldPool == null) {
            return new MatchResponse.Builder(request.getRequestId())
                    .denied("WorldPool not initialized on SWM Framework.")
                    .build();
        }

        String worldName = null;
        if (handler != null) {
            try {
                java.lang.reflect.Method prepareWorld = GameHandler.class
                        .getMethod("prepareWorld", Object.class, Map.class);
                @SuppressWarnings("unchecked")
                String result = (String) prepareWorld.invoke(handler, worldPool, options);
                worldName = result;
            } catch (NoSuchMethodException e) {
                // Handler does not override prepareWorld — skip
                worldName = null;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error calling handler.prepareWorld", e);
                worldName = null;
            }
        }

        if (worldName == null) {
            // Try to allocate any available world with matching game type
            try {
                java.lang.reflect.Method allocateWorld = worldPool.getClass()
                        .getMethod("allocateWorld", String.class, int.class);
                @SuppressWarnings("unchecked")
                String result = (String) allocateWorld.invoke(worldPool, gameType, request.getPlayerCount());
                worldName = result;
            } catch (NoSuchMethodException e) {
                // Try alternate signature
                try {
                    java.lang.reflect.Method allocateWorld = worldPool.getClass()
                            .getMethod("allocateWorld", String.class);
                    @SuppressWarnings("unchecked")
                    String result = (String) allocateWorld.invoke(worldPool, gameType);
                    worldName = result;
                } catch (Exception e2) {
                    plugin.getLogger().log(Level.WARNING, "Failed to allocate world via WorldPool", e2);
                    worldName = null;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to allocate world via WorldPool", e);
                worldName = null;
            }
        }

            if (worldName == null) {
                return new MatchResponse.Builder(request.getRequestId())
                        .denied("No world available for game type '" + gameType + "'")
                        .build();
            }

            session.setAllocatedWorld(worldName);

            // Initialize the world (place beds, spawn points, etc.)
            if (handler != null) {
                handler.onWorldInit(session, worldName);
            }

            // Add players to session
            for (String playerName : request.getPlayerNames()) {
                session.addPlayer(playerName);
            }

            // Start the game
            List<Player> players = getPlayerList(request);
            session.start(players);

            if (handler != null) {
                handler.onGameStart(session, players);
            }

            // Track the session
            activeSessions.add(session);
            sessionsById.put(sessionId, session);

            if (config.isLogSessionEvents()) {
                plugin.getLogger().info("Dispatched " + gameType + " session '" + sessionId
                        + "' with " + players.size() + " players on world '" + worldName + "'");
            }

            return new MatchResponse.Builder(request.getRequestId())
                    .accepted(sessionId, worldName)
                    .build();

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to dispatch game request: " + request.getRequestId(), e);
            return new MatchResponse.Builder(request.getRequestId())
                    .denied("Game initialization failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Convert a MatchRequest's player names into actual Player objects.
     */
    private List<Player> getPlayerList(MatchRequest request) {
        return request.getPlayerNames()
                .stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get all active sessions.
     */
    public Collection<GameSession> getActiveSessions() {
        return Collections.unmodifiableCollection(activeSessions);
    }

    /**
     * Get the number of active sessions.
     */
    public int getActiveSessionCount() {
        // Remove ended sessions
        activeSessions.removeIf(s -> s.getState() == GameSession.SessionState.ENDED);
        return activeSessions.size();
    }

    /**
     * Get a session by ID.
     */
    public GameSession getSessionById(String sessionId) {
        return sessionsById.get(sessionId);
    }

    /**
     * End all active sessions.
     */
    public void endAllSessions() {
        List<GameSession> toEnd = new ArrayList<>(activeSessions);
        for (GameSession session : toEnd) {
            if (session.getState() != GameSession.SessionState.ENDED) {
                session.end();
            }
        }
        activeSessions.clear();
        sessionsById.clear();
    }

    /**
     * Remove a session when cleanup is needed.
     */
    public void removeSession(GameSession session) {
        activeSessions.remove(session);
        sessionsById.remove(session.getId());
    }

    /**
     * Get all game types that have registered handlers.
     */
    public List<String> getRegisteredGameTypes() {
        return Collections.unmodifiableList(new ArrayList<>(handlers.keySet()));
    }
}
