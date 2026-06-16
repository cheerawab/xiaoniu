package co.partygame.core.game;

import co.partygame.core.game.custom.IGamePlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages all registered game plugins ({@link IGamePlugin} implementations).
 *
 * <p>The GameRegistry holds a map of all available game types and their
 * plugin implementations. Games can be registered programmatically or
 * discovered automatically from the {@code games/*.json} config files
 * and the {@code plugins/*.jar} directory for hot-reloadable game JARs.</p>
 *
 * <p>This class is thread-safe. All operations use synchronization
 * on the internal map to allow concurrent registration and lookup.</p>
 */
public class GameRegistry {

    private static final Logger LOGGER = Logger.getLogger(GameRegistry.class.getName());

    private final Map<String, IGamePlugin> gamePlugins;
    private final Map<String, GameConfig> gameConfigs;

    /**
     * Configuration for a game loaded from its JSON config file.
     */
    public static class GameConfig {
        private final String id;
        private final String name;
        private final int minPlayers;
        private final int maxPlayers;
        private final String defaultWorld;
        private final Map<String, Object> customOptions;

        public GameConfig(String id, String name, int minPlayers, int maxPlayers,
                          String defaultWorld, Map<String, Object> customOptions) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            this.name = Objects.requireNonNull(name, "name must not be null");
            this.minPlayers = Math.max(1, minPlayers);
            this.maxPlayers = Math.max(this.minPlayers, maxPlayers);
            this.defaultWorld = defaultWorld;
            this.customOptions = customOptions != null ? Collections.unmodifiableMap(customOptions) : Collections.emptyMap();
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public int getMinPlayers() { return minPlayers; }
        public int getMaxPlayers() { return maxPlayers; }
        public String getDefaultWorld() { return defaultWorld; }
        public Map<String, Object> getCustomOptions() { return customOptions; }
    }

    private static GameRegistry instance;

    /**
     * Returns the singleton instance of the GameRegistry.
     *
     * @return the singleton GameRegistry
     */
    public static synchronized GameRegistry getInstance() {
        if (instance == null) {
            instance = new GameRegistry();
        }
        return instance;
    }

    /**
     * Private constructor. Use {@link #getInstance()} to access.
     */
    private GameRegistry() {
        this.gamePlugins = new ConcurrentHashMap<>();
        this.gameConfigs = new ConcurrentHashMap<>();
    }

    /**
     * Registers a game plugin with the registry.
     *
     * @param plugin the game plugin to register
     * @throws IllegalStateException if a plugin with the same ID already exists
     */
    public synchronized void register(IGamePlugin plugin) {
        Objects.requireNonNull(plugin, "Game plugin must not be null");
        String id = Objects.requireNonNull(plugin.getId(), "Plugin id must not be null");
        if (gamePlugins.containsKey(id)) {
            throw new IllegalStateException("Game already registered: " + id);
        }
        gamePlugins.put(id, plugin);
        LOGGER.info("Registered game plugin: " + id + " (" + plugin.getName() + ")");
    }

    /**
     * Returns a game plugin by its ID.
     *
     * @param id the unique game identifier
     * @return the game plugin, or null if not found
     */
    public IGamePlugin get(String id) {
        if (id == null) return null;
        return gamePlugins.get(id);
    }

    /**
     * Returns a game config by its ID.
     *
     * @param id the unique game identifier
     * @return the game configuration, or null if not found
     */
    public GameConfig getConfig(String id) {
        if (id == null) return null;
        return gameConfigs.get(id);
    }

    /**
     * Returns a list of all registered game plugins.
     *
     * @return unmodifiable list of all game plugins
     */
    public List<IGamePlugin> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(gamePlugins.values()));
    }

    /**
     * Returns a map of all game configurations.
     *
     * @return unmodifiable map of game ID to config
     */
    public Map<String, GameConfig> getAllConfigs() {
        return Collections.unmodifiableMap(gameConfigs);
    }

    /**
     * Returns the number of registered game plugins.
     *
     * @return plugin count
     */
    public int size() {
        return gamePlugins.size();
    }

    /**
     * Checks if a game plugin is registered for the given ID.
     *
     * @param id the game identifier
     * @return true if a plugin is registered for the ID
     */
    public boolean hasGame(String id) {
        return id != null && gamePlugins.containsKey(id);
    }

    /**
     * Registers a game config (loaded from JSON).
     *
     * @param config the game configuration
     */
    public synchronized void registerConfig(GameConfig config) {
        Objects.requireNonNull(config, "Game config must not be null");
        gameConfigs.put(config.getId(), config);
        LOGGER.fine("Registered game config: " + config.getId() + " (" + config.getName() + ")");
    }

    /**
     * Reloads all game configs. Called by {@link co.partygame.core.hotreload.HotReloadManager}
     * when game config files change.
     */
    public synchronized void reloadGameConfigs(Map<String, GameConfig> newConfigs) {
        if (newConfigs == null || newConfigs.isEmpty()) return;
        gameConfigs.putAll(newConfigs);
        LOGGER.info("Reloaded " + newConfigs.size() + " game configs");
    }

    /**
     * Removes a game plugin by ID.
     *
     * @param id the game identifier
     * @return the removed plugin, or null if not found
     */
    public synchronized IGamePlugin unregister(String id) {
        if (id == null) return null;
        IGamePlugin removed = gamePlugins.remove(id);
        if (removed != null) {
            gameConfigs.remove(id);
            LOGGER.info("Unregistered game plugin: " + id);
        }
        return removed;
    }

    /**
     * Checks if there are any registered game plugins.
     *
     * @return true if at least one plugin is registered
     */
    public boolean isEmpty() {
        return gamePlugins.isEmpty();
    }
}
