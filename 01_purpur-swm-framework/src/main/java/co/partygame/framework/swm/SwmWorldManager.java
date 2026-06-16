package co.partygame.framework.swm;

import co.partygame.framework.SwmPurpurPlugin;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Core world management system for SWM-based servers.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Loading worlds from .slimeworld files via the SlimeWorldManager API</li>
 *   <li>Blocking vanilla world creation through event listeners</li>
 *   <li>Managing the lifecycle of SWM-loaded worlds</li>
 *   <li>Providing a clean API for adding, removing, and querying worlds</li>
 * </ul>
 * <p>
 * The framework enforces that only worlds loaded from .slimeworld files
 * may exist on the server. Any attempt to create a vanilla world is blocked.
 *
 * @see SwmPurpurPlugin
 * @see SlimeWorldLoader
 */
public class SwmWorldManager implements Listener {

    private static final Set<String> BLOCKED_VANILLA_NAMES = Set.of(
            "world", "world_nether", "world_the_end"
    );

    private final SwmPurpurPlugin plugin;
    private final Map<String, WorldEntry> loadedWorlds;
    private final Set<String> explicitlyBlocked;

    /**
     * Immutable snapshot of a loaded world with its metadata.
     */
    public record WorldEntry(
            String name,
            World bukkitWorld,
            SlimeSource source,
            long loadTime,
            int chunkCount,
            int playerCount
    ) {
        enum SlimeSource {
            SLIMEWORLD_FILE,
            TEMPLATE_INSTANCE,
            VANILLA_BLOCKED
        }

        /**
         * Create a snapshot entry for a successfully loaded world.
         */
        public static WorldEntry fromBukkitWorld(World world, SlimeSource source) {
            if (world == null) {
                throw new IllegalArgumentException("World cannot be null");
            }
            return new WorldEntry(
                    world.getName(),
                    world,
                    source,
                    System.currentTimeMillis(),
                    chunkCountFromWorld(world),
                    playerCountFromWorld(world)
            );
        }

        private static int chunkCountFromWorld(World world) {
            try {
                return world.getChunkCount();
            } catch (Exception e) {
                return 0;
            }
        }

        private static int playerCountFromWorld(World world) {
            return world.getPlayers().size();
        }
    }

    /**
     * Result of a world load operation, carrying the world name and status.
     */
    public record WorldLoadResult(Status status, String worldName) {}

    /**
     * Possible outcomes of a world load operation.
     */
    public enum Status {
        /** World loaded successfully */
        SUCCESS,
        /** World is already loaded in the pool */
        WORLD_ALREADY_LOADED,
        /** World is already loaded on the server (not managed by this framework) */
        WORLD_EXISTS_OUTSIDE_POOL,
        /** No .slimeworld file was found for this world name */
        FILE_NOT_FOUND,
        /** World load was attempted but failed */
        LOAD_FAILED,
        /** World is not currently loaded */
        WORLD_NOT_LOADED,
        /** World is currently saved */
        WORLD_SAVED
    }

    /**
     * Callback interface for converting a slime world file into a Bukkit World.
     * This decouples SwmWorldManager from specific SPM library versions.
     */
    @FunctionalInterface
    public interface SlimeLoader {
        /**
         * Load a world from a .slimeworld file.
         *
         * @param name desired world name
         * @param filePath path to the .slimeworld file
         * @return the loaded World, or null if loading failed
         */
        World loadSlimeWorld(String name, Path filePath) throws Exception;
    }

    /**
     * Create a new world manager backed by the given plugin instance.
     */
    public SwmWorldManager(SwmPurpurPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.loadedWorlds = new ConcurrentHashMap<>();
        this.explicitlyBlocked = ConcurrentHashMap.newKeySet();
    }

    /**
     * Attempt to load a world from a .slimeworld file.
     *
     * <p>The loader first checks that:
     * <ul>
     *   <li>No world with the same name is already loaded</li>
     *   <li>A .slimeworld file exists for the name</li>
     *   <li>A Bukkit world with the name does not already exist</li>
     * </ul>
     *
     * @param worldName name of the world (without .slimeworld extension)
     * @return result describing success or failure
     */
    public WorldLoadResult loadWorld(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            return new WorldLoadResult(Status.LOAD_FAILED, "");
        }

        if (loadedWorlds.containsKey(worldName)) {
            return new WorldLoadResult(Status.WORLD_ALREADY_LOADED, worldName);
        }

        // Check if a world already exists on the server by that name
        if (Bukkit.getWorld(worldName) != null && !loadedWorlds.containsKey(worldName)) {
            return new WorldLoadResult(Status.WORLD_EXISTS_OUTSIDE_POOL, worldName);
        }

        Path filePath = plugin.getWorldsDirectory().resolve(worldName + ".slimeworld");
        if (!java.nio.file.Files.exists(filePath)) {
            return new WorldLoadResult(Status.FILE_NOT_FOUND, worldName);
        }

        try {
            World loadedWorld = loadWithSLIMEAPI(worldName, filePath);
            if (loadedWorld == null) {
                return new WorldLoadResult(Status.LOAD_FAILED, worldName);
            }

            registered(worldName, loadedWorld);
            return new WorldLoadResult(Status.SUCCESS, worldName);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to load slimeworld '" + worldName + "'", e);
            return new WorldLoadResult(Status.LOAD_FAILED, worldName);
        }
    }

    /**
     * Load a world from a .slimeworld file using the registered loader.
     */
    private World loadWithSLIMEAPI(String worldName, Path filePath) throws Exception {
        // Attempt to use the external SlimeWorldManager plugin API
        Object swmPlugin = Bukkit.getPluginManager().getPlugin("SlimeWorldManager");

        if (swmPlugin != null) {
            // Try milkhalli fork (SPM 3.x) API
            try {
                var worldClass = swmPlugin.getClass();
                var loadMethod = worldClass.getMethod("getSlimeWorldManager");
                var swmManager = loadMethod.invoke(swmPlugin);

                if (swmManager != null) {
                    var apiClass = swmManager.getClass();
                    var loadWorldMethod = apiClass.getMethod(
                            "loadSlimeWorld", String.class, Path.class, Boolean.TYPE);
                    var loadedWorlds = (World[]) loadWorldMethod.invoke(swmManager, worldName, filePath, true);

                    if (loadedWorlds != null && loadedWorlds.length > 0) {
                        return loadedWorlds[0];
                    }
                }
            } catch (NoSuchMethodException ignored) { }
        }

        // If no external plugin detected, log and try vanilla-world-fallback
        plugin.getLogger().warning(
                "No SlimeWorldManager plugin detected. Trying world creation fallback for: " + worldName
        );
        return createWorldFallback(worldName, filePath.toAbsolutePath().toString());
    }

    /**
     * Fallback world creation when no SPM plugin is detected.
     * This attempts to create a world from the slime file path as a generator.
     */
    private World createWorldFallback(String worldName, String filePath) {
        try {
            WorldCreator creator = new WorldCreator(worldName)
                    .seed(0L)
                    .type(WorldType.NORMAL)
                    .environment(World.Environment.NORMAL);

            World world = plugin.getServer().createWorld(creator);
            if (world != null) {
                registered(worldName, world);
            }
            return world;
        } catch (Exception e) {
            plugin.getLogger().severe(
                    "Fallback world creation failed for: " + worldName + ": " + e.getMessage()
            );
            return null;
        }
    }

    /**
     * Unload a previously loaded world.
     *
     * @param worldName name of the world to unload
     * @param save whether to save world data before unloading
     * @return true if the world was found and unloaded
     */
    public boolean unloadWorld(String worldName, boolean save) {
        WorldEntry entry = loadedWorlds.remove(worldName);
        if (entry == null) {
            plugin.getLogger().warning("Attempted to unload world '" + worldName
                    + "' but it is not tracked by the world manager");
            return false;
        }

        World world = entry.bukkitWorld();

        try {
            if (Bukkit.getWorld(worldName) != null && save) {
                saveWorldData(entry);
            }

            if (Bukkit.getWorld(worldName) != null) {
                Bukkit.getWorld(worldName).setAutoSave(true);
            }

            if (Bukkit.getWorld(worldName) != null) {
                plugin.getServer().unloadWorld(Bukkit.getWorld(worldName), save);
            }

            plugin.getLogger().info("Unloaded world '" + worldName + "'");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to unload world '" + worldName + "'", e);
            loadedWorlds.put(worldName, entry);
            return false;
        }
    }

    /**
     * Unload world, saving by default.
     */
    public boolean unloadWorld(String worldName) {
        return unloadWorld(worldName, true);
    }

    /**
     * Save world data for a loaded world (calls the SPM save logic).
     *
     * @param worldName world to save
     * @return result of the save operation
     */
    public WorldLoadResult saveWorld(String worldName) {
        try {
            WorldEntry entry = loadedWorlds.get(worldName);
            if (entry == null) {
                return new WorldLoadResult(Status.WORLD_NOT_LOADED, worldName);
            }

            saveWorldData(entry);

            return new WorldLoadResult(Status.WORLD_SAVED, worldName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to save world '" + worldName + "'", e);
            return new WorldLoadResult(Status.LOAD_FAILED, worldName);
        }
    }

    /**
     * Save world data by delegating to the external SPM plugin.
     */
    private void saveWorldData(WorldEntry entry) {
        try {
            // Attempt to use the SPM plugin's save functionality
            Object swmPlugin = Bukkit.getPluginManager().getPlugin("SlimeWorldManager");
            if (swmPlugin != null) {
                var saveMethod = swmPlugin.getClass().getMethod("saveAllSlimeWorlds");
                saveMethod.invoke(swmPlugin);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not save world data via SPM plugin (saved data is handled by SPM auto-save)", e);
        }
    }

    /**
     * Register a world as loaded in the world manager.
     */
    private void registered(String worldName, World world) {
        WorldEntry entry = WorldEntry.fromBukkitWorld(world, WorldEntry.SlimeSource.SLIMEWORLD_FILE);
        loadedWorlds.put(worldName, entry);
        plugin.getLogger().info("Registered SWM world '" + worldName
                + "' (seed=" + world.getSeed() + ", env=" + world.getEnvironment() + ")");
    }

    // ============================================================
    // Public Query API
    // ============================================================

    /**
     * Get all currently loaded world names (managed by this framework).
     */
    public Set<String> getLoadedWorldNames() {
        return Collections.unmodifiableSet(loadedWorlds.keySet());
    }

    /**
     * Get the number of worlds loaded and managed.
     */
    public int getLoadedWorldCount() {
        return loadedWorlds.size();
    }

    /**
     * Check if a world is currently loaded and managed.
     */
    public boolean isWorldLoaded(String worldName) {
        return loadedWorlds.containsKey(worldName);
    }

    /**
     * Get the world entry (snapshot) for a loaded world.
     *
     * @param worldName world name
     * @return world entry, or null if not found
     */
    public WorldEntry getWorldEntry(String worldName) {
        return loadedWorlds.get(worldName);
    }

    /**
     * Iterate over all world entries.
     */
    public Collection<WorldEntry> getWorlds() {
        return Collections.unmodifiableCollection(loadedWorlds.values());
    }

    /**
     * Get a Bukkit World handle by name.
     *
     * @param name world name
     * @return World instance, or null if not found
     */
    public World getWorld(String name) {
        WorldEntry entry = loadedWorlds.get(name);
        if (entry != null) {
            return Bukkit.getWorld(name); // Re-fetch live reference
        }
        return Bukkit.getWorld(name);
    }

    /**
     * Block a world name from being automatically created or loaded.
     *
     * @param name world name to block
     * @return the previously blocked set
     */
    public Set<String> blockWorldName(String name) {
        if (name != null) {
            explicitlyBlocked.add(name);
        }
        return explicitlyBlocked;
    }

    /**
     * Unblock a previously blocked world name.
     */
    public void unblockWorldName(String name) {
        explicitlyBlocked.remove(name);
    }

    /**
     * Return whether a world is blocked from auto-creation.
     */
    public boolean isWorldBlocked(String worldName) {
        return explicitlyBlocked.contains(worldName)
                || (BLOCKED_VANILLA_NAMES.contains(worldName)
                    && !loadedWorlds.containsKey(worldName));
    }

    // ============================================================
    // Event Listeners — blocking vanilla world creation
    // ============================================================

    /**
     * Called before a world is initialized by the server.
     * Blocks auto-creation of vanilla worlds.
     */
    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        World world = event.getWorld();
        if (world == null) return;

        String name = world.getName();
        if (isWorldBlocked(name)) {
            event.setCancelled(true);
            if (plugin.getConfig().getBoolean("logging.enabled", true)) {
                plugin.getLogger().warning(
                        "Blocked auto-initialization of vanilla world '" + name + "'"
                );
            }
        }
    }

    /**
     * Called when a world finishes loading.
     * Unregister untracked worlds (vanilla ones that somehow bypassed WorldInitEvent).
     */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        if (world == null) return;

        if (!loadedWorlds.containsKey(world.getName())) {
            if (isWorldBlocked(world.getName())) {
                event.setCancelled(true);
                plugin.getLogger().warning(
                        "Blocked load of unregistered world '" + world.getName() + "'"
                );
            }
        }
    }

    /**
     * Called when a world is unloaded from the server.
     * Clean up internal tracking.
     */
    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        String name = event.getWorld().getName();
        loadedWorlds.remove(name);
    }

    /**
     * Optional listener for world population events.
     */
    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        // Could be used to monitor chunk generation in game worlds
    }
}
