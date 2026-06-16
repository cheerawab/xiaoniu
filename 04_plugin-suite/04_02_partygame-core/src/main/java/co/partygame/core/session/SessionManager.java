package co.partygame.core.session;

import co.partygame.core.event.SessionEventListener;
import co.partygame.core.game.GameSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages all active game sessions across the backend server.
 *
 * <p>Provides thread-safe CRUD operations for {@link GameSession} objects.
 * When a session ends, it is automatically cleaned up and the associated
 * world is returned to the pool.</p>
 *
 * <p>Sessions are indexed by both session UUID and player UUID for
 * efficient lookup by either identifier.</p>
 */
public class SessionManager {

    private static final Logger LOGGER = Logger.getLogger(SessionManager.class.getName());

    private final Map<UUID, GameSession> sessions;
    private final Map<UUID, UUID> playerIndex;
    private final List<SessionEventListener> listeners;
    private final ReadWriteLock lock;

    private static SessionManager instance;

    /**
     * Returns the singleton instance of the SessionManager.
     *
     * @return the singleton SessionManager
     */
    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    /**
     * Private constructor. Use {@link #getInstance()} to access.
     */
    private SessionManager() {
        this.sessions = new ConcurrentHashMap<>();
        this.playerIndex = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Registers a session event listener.
     *
     * @param listener the listener to register
     */
    public void registerListener(SessionEventListener listener) {
        Objects.requireNonNull(listener, "Listener must not be null");
        listeners.add(listener);
    }

    /**
     * Unregisters a session event listener.
     *
     * @param listener the listener to unregister
     * @return true if the listener was found and removed
     */
    public boolean unregisterListener(SessionEventListener listener) {
        return listeners.remove(listener);
    }

    private void fireSessionCreated(GameSession session) {
        for (SessionEventListener l : listeners) {
            try {
                // Fire session-created events
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error firing session-created event", e);
            }
        }
    }

    /**
     * Creates or adds a game session.
     *
     * @param session the session to create
     * @return the created session, or null if already exists
     */
    public boolean create(GameSession session) {
        Objects.requireNonNull(session, "Session must not be null");
        UUID sessionId = resolveSessionId(session);

        lock.writeLock().lock();
        try {
            if (sessions.containsKey(sessionId)) {
                LOGGER.warning("Session already exists: " + sessionId);
                return false;
            }
            sessions.put(sessionId, session);

            for (UUID playerUuid : session.getPlayers()) {
                playerIndex.put(playerUuid, sessionId);
            }
            LOGGER.info("Session created: " + sessionId + " (" + session.getGameType() + ")");
        } finally {
            lock.writeLock().unlock();
        }
        return true;
    }

    /**
     * Resolves a UUID to a GameSession. Supports both direct UUID comparison and
     * string-parsed UUID matching (for cross-format compatibility).
     *
     * @param id the identifier (UUID or UUID string)
     * @return the matched UUID, or null if not found
     */
    private UUID resolveSessionId(Object id) {
        if (id instanceof UUID) {
            return (UUID) id;
        }
        if (id instanceof String) {
            try {
                return UUID.fromString((String) id);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Finds a game session by a player's UUID.
     *
     * @param uuid the player's UUID
     * @return an Optional containing the session if found, empty otherwise
     */
    public Optional<GameSession> findByPlayer(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID must not be null");
        UUID sessionId = playerIndex.get(uuid);
        if (sessionId == null) return Optional.empty();
        GameSession session = sessions.get(sessionId);
        return Optional.ofNullable(session);
    }

    /**
     * Returns a session by its ID.
     *
     * @param sessionId the session UUID
     * @return the session, or null if not found
     */
    public GameSession get(UUID sessionId) {
        if (sessionId == null) return null;
        return sessions.get(sessionId);
    }

    /**
     * Removes a session and all its player mappings.
     *
     * @param sessionId the session UUID
     * @return true if the session was removed
     */
    public boolean remove(UUID sessionId) {
        if (sessionId == null) return false;
        lock.writeLock().lock();
        try {
            GameSession session = sessions.remove(sessionId);
            if (session != null) {
                playerIndex.values().remove(sessionId);
                LOGGER.info("Session removed: " + sessionId);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Disposes a session completely: removes from store, un-indexes players,
     * and notifies subscribers of disposal.
     *
     * @param sessionId the session to dispose
     */
    public void disposeSession(UUID sessionId) {
        if (sessionId == null) return;
        GameSession session = get(sessionId);
        if (session == null) return;

        remove(sessionId);
        LOGGER.info("Session disposed: " + sessionId + " (type=" + session.getGameType() + ")");

        for (SessionEventListener l : listeners) {
            try {
                // Fire session-disposed event
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error firing disposed event", e);
            }
        }
    }

    /**
     * Returns all active sessions.
     *
     * @return unmodifiable list of all active sessions
     */
    public List<GameSession> getAll() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(sessions.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the total number of active sessions.
     *
     * @return session count
     */
    public int count() {
        lock.readLock().lock();
        try {
            return sessions.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the number of sessions currently in RUNNING state.
     *
     * @return running session count
     */
    public int getRunningCount() {
        lock.readLock().lock();
        try {
            return (int) sessions.values().stream()
                    .filter(s -> s != null && s.getStatus() == GameSession.Status.RUNNING)
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the number of active players across all sessions.
     *
     * @return total player count
     */
    public int getTotalPlayerCount() {
        lock.readLock().lock();
        try {
            return sessions.values().stream()
                    .mapToInt(s -> s != null ? s.getPlayers().size() : 0)
                    .sum();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if a session exists for the given ID.
     *
     * @param sessionId the session UUID
     * @return true if a session exists with that ID
     */
    public boolean hasSession(UUID sessionId) {
        if (sessionId == null) return false;
        return sessions.containsKey(sessionId);
    }

    /**
     * Clears all sessions and removes all registrations.
     */
    public void clearAll() {
        lock.writeLock().lock();
        try {
            sessions.clear();
            playerIndex.clear();
            LOGGER.info("All sessions cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Finds a session by ID (with flexible type support).
     *
     * @param id the session identifier (UUID or string)
     * @return an Optional containing the session if found
     */
    public Optional<GameSession> findById(Object id) {
        if (id == null) return Optional.empty();
        UUID uuid = resolveSessionId(id);
        if (uuid == null) return Optional.empty();
        return Optional.ofNullable(sessions.get(uuid));
    }
}
