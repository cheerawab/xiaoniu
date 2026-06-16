package co.partygame.framework.swm;

import co.partygame.framework.SwmPurpurPlugin;
import org.bukkit.Bukkit;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages a pool of world instances for game sessions.
 * <p>
 * Worlds in the pool are divided into two categories:
 * <ul>
 *   <li><b>Available</b> — loaded from .slimeworld files, no active players</li>
 *   <li><b>Allocated</b> — worlds currently hosting game sessions with players</li>
 * </ul>
 */
public final class WorldPool {

    private final SwmPurpurPlugin plugin;
    private final SwmWorldManager worldManager;
    private final SlimeWorldLoader worldLoader;

    private final ConcurrentHashMap<String, WorldInfo> availablePool;
    private final ConcurrentHashMap<String, WorldInfo> allocatedWorlds;
    private volatile boolean shutdown;

    /**
     * Metadata for a world in the pool.
     */
    public record WorldInfo(
            String worldName,
            long lastUsed,
            int playerCount,
            boolean fromTemplate,
            WorldTemplate template
    ) {
        static WorldInfo of(String name, boolean fromTemplate) {
            return new WorldInfo(name, System.currentTimeMillis(), 0, fromTemplate, null);
        }

        static WorldInfo of(String name, boolean fromTemplate, WorldTemplate template) {
            return new WorldInfo(name, System.currentTimeMillis(), 0, fromTemplate, template);
        }

        WorldInfo withPlayerCount(int count) {
            return new WorldInfo(worldName, lastUsed, count, fromTemplate, template);
        }

        WorldInfo touch() {
            return new WorldInfo(worldName, System.currentTimeMillis(), playerCount, fromTemplate, template);
        }
    }

    /**
     * Create a new WorldPool backed by the given plugin instance.
     */
    public WorldPool(SwmPurpurPlugin plugin, SwmWorldManager worldManager, SlimeWorldLoader worldLoader) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.worldManager = Objects.requireNonNull(worldManager, "worldManager cannot be null");
        this.worldLoader = Objects.requireNonNull(worldLoader, "worldLoader cannot be null");
        this.availablePool = new ConcurrentHashMap<>();
        this.allocatedWorlds = new ConcurrentHashMap<>();
        this.shutdown = false;
    }

    /**
     * Load a world by name and add it to the available pool.
     */
    public WorldLoadResult loadWorld(String worldName) {
        if (shutdown) return new WorldLoadResult(WorldLoadResult.Status.LOAD_FAILED, worldName);
        if (worldName == null || worldName.isEmpty()) {
            return new WorldLoadResult(WorldLoadResult.Status.LOAD_FAILED, "");
        }

        if (availablePool.containsKey(worldName) || allocatedWorlds.containsKey(worldName)) {
            return new WorldLoadResult(WorldLoadResult.Status.WORLD_ALREADY_LOADED, worldName);
        }

        SlimeWorldLoader loader = worldLoader;
        Path file = loader.getWorldFilePath(worldName);
        if (!Files.exists(file)) {
            return new WorldLoadResult(WorldLoadResult.Status.FILE_NOT_FOUND, worldName);
        }

        // Delegate to WorldManager to perform the actual world loading
        WorldLoadResult result = worldManager.loadWorld(worldName);
        if (result.status() == WorldLoadResult.Status.SUCCESS) {
            availablePool.put(worldName, WorldInfo.of(worldName, false));
            plugin.getLogger().info("World '" + worldName + "' added to available pool.");
        }

        return result;
    }

    /**
     * Load a world from a template, creating a unique instance.
     */
    public String loadWorldFromTemplate(String templateName, String desiredName) {
        WorldTemplate template = plugin.getTemplates().get(templateName);
        if (template == null) {
            return null;
        }

        String worldName = findAvailableName(desiredName);
        if (worldName == null) {
            return null;
        }

        WorldLoadResult result = worldManager.loadWorld(worldName);
        if (result.status() != WorldLoadResult.Status.SUCCESS) {
            return null;
        }

        availablePool.put(worldName, WorldInfo.of(worldName, true, template));
        plugin.getLogger().info("World '" + worldName + "' created from template '" + templateName + "'.");
        return worldName;
    }

    /**
     * Allocate a world for a game. Searches for a matching loaded world first,
     * then finds any available world, then creates a new instance.
     *
     * @param desiredName optional preferred world name (can be game type suffix)
     * @param playerCount number of players that will use this world
     * @return the world name, or null if no world is available
     */
    public String allocateWorld(String desiredName, int playerCount) {
        if (shutdown) return null;

        if (desiredName != null && !desiredName.isEmpty()) {
            // Check if already allocated (reuse)
            WorldInfo allocated = allocatedWorlds.get(desiredName);
            if (allocated != null) {
                allocatedWorlds.put(desiredName, allocated.withPlayerCount(
                        allocated.playerCount() + playerCount));
                return desiredName;
            }

            // Check if available
            WorldInfo available = availablePool.get(desiredName);
            if (available != null) {
                availablePool.remove(desiredName);
                allocatedWorlds.put(desiredName, available.withPlayerCount(playerCount).touch());
                return desiredName;
            }
        }

        // Find any available world with sufficient capacity
        for (Map.Entry<String, WorldInfo> entry : availablePool.entrySet()) {
            String wn = entry.getKey();
            WorldInfo info = entry.getValue();
            availablePool.remove(wn);
            allocatedWorlds.put(wn, info.withPlayerCount(playerCount).touch());
            plugin.getLogger().info("Allocated world '" + wn + "' for " + playerCount + " players.");
            return wn;
        }

        // Pool is empty or full — try to create from template
        return createFreshWorld();
    }

    /**
     * Release players from a world and move it back to available pool.
     */
    public String releasePlayers(String worldName, int playerCount) {
        WorldInfo allocated = allocatedWorlds.get(worldName);
        if (allocated == null) return null;

        int remaining = Math.max(0, allocated.playerCount() - playerCount);
        if (remaining == 0) {
            allocatedWorlds.remove(worldName);
            availablePool.put(worldName, allocated.withPlayerCount(0).touch());
            plugin.getLogger().info("World '" + worldName + "' fully deallocated.");
            idleCheck(worldName);
            return worldName;
        }

        allocatedWorlds.put(worldName, allocated.withPlayerCount(remaining));
        return worldName;
    }

    /**
     * Remove a world from the pool entirely (but don't unload from server).
     */
    public boolean removeWorld(String worldName) {
        boolean success = availablePool.remove(worldName) != null;
        allocatedWorlds.remove(worldName);
        return success;
    }

    /**
     * Save all worlds in the pool.
     */
    public List<WorldLoadResult> saveAllWorlds() {
        List<WorldLoadResult> results = new ArrayList<>();
        for (String name : worldManager.getLoadedWorldNames()) {
            results.add(worldManager.saveWorld(name));
        }
        return results;
    }

    public Set<String> getAvailableWorldNames() {
        return Collections.unmodifiableSet(availablePool.keySet());
    }

    public Set<String> getAllocatedWorldNames() {
        return Collections.unmodifiableSet(allocatedWorlds.keySet());
    }

    /**
     * Shutdown the pool, saving all worlds.
     */
    public void shutdown() {
        shutdown = true;
        saveAllWorlds();
        availablePool.clear();
        allocatedWorlds.clear();
        plugin.getLogger().info("WorldPool shut down.");
    }

    private String createFreshWorld() {
        if (!plugin.getTemplates().isEmpty()) {
            String templateName = plugin.getTemplates().keySet().iterator().next();
            return loadWorldFromTemplate(templateName, "game_world");
        }
        return null;
    }

    private String findAvailableName(String desired) {
        if (desired == null) desired = "game_world";
        for (int i = 0; i < 100; i++) {
            String name = (i == 0) ? desired : desired + "_" + i;
            if (Bukkit.getWorld(name) == null && !availablePool.containsKey(name)
                    && !allocatedWorlds.containsKey(name)) {
                return name;
            }
        }
        return null;
    }

    private void idleCheck(String worldName) {
        WorldInfo info = availablePool.get(worldName);
        if (info == null) return;

        long idleTimeout = plugin.getConfig().getLong("swm.world-pool.idle-eviction-seconds", 300);
        if (idleTimeout <= 0) return;

        long idleMs = System.currentTimeMillis() - info.lastUsed();
        if (idleMs >= idleTimeout * 1000L) {
            plugin.getLogger().info("Evicting idle world '" + worldName
                    + "' (no players for " + (idleMs / 1000) + "s)");
            removeWorld(worldName);
            worldManager.unloadWorld(worldName, true);
        }
    }
}
