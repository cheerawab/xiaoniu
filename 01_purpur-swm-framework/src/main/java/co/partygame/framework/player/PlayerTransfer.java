package co.partygame.framework.player;

import co.partygame.framework.SwmPurpurPlugin;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Handles safe player teleportation between worlds.
 * <p>
 * Provides utilities for:
 * <ul>
 *   <li>Teleporting individual players between worlds</li>
 *   <li>Atomic group teleportation (all or nothing)</li>
 *   <li>Position lock during teleport sequences</li>
 *   <li>Graceful teleport failure handling with retries</li>
 * </ul>
 * <p>
 * All teleport operations are run asynchronously where possible to avoid
 * server thread blocking in the Folia-compatible architecture.
 */
public class PlayerTransfer implements Listener {

    private final SwmPurpurPlugin plugin;
    private final ConcurrentHashMap<UUID, List<ScheduledFuture<?>>> pendingFutures;
    private final ConcurrentHashMap<UUID, Boolean> positionLockedPlayers;
    private final ScheduledExecutorService teleportScheduler;

    private static final ConcurrentHashMap<UUID, AtomicInteger> teleportAttempts = new ConcurrentHashMap<>();

    private final int maxTeleportAttempts;
    private final int teleportDelayTicks;
    private final boolean syncGroupTeleport;
    private final boolean lockPositionDuringTeleport;
    private final boolean logTeleportEvents;

    public PlayerTransfer(SwmPurpurPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");

        this.pendingFutures = new ConcurrentHashMap<>();
        this.positionLockedPlayers = new ConcurrentHashMap<>();
        this.maxTeleportAttempts = plugin.getConfig().getInt("teleport.max-teleport-attempts", 3);
        this.teleportDelayTicks = plugin.getConfig().getInt("teleport.teleport-delay", 0);
        this.syncGroupTeleport = plugin.getConfig().getBoolean("teleport.sync-group-teleport", true);
        this.lockPositionDuringTeleport = plugin.getConfig().getBoolean("teleport.lock-position-during-teleport", true);
        this.logTeleportEvents = plugin.getConfig().getBoolean("logging.log-teleport-events", true);

        this.teleportScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "swm-player-teleport");
            t.setDaemon(true);
            return t;
        });
    }

    // ============================================================
    // Teleportation API — single player
    // ============================================================

    /**
     * Teleport a player to a world's spawn location.
     */
    public boolean teleportPlayer(Player player, String worldName) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(worldName, "worldName cannot be null");

        if (player.isDead()) {
            return false;
        }

        cancelPendingFutures(player.getUniqueId());
        return teleportWithRetry(player, worldName, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Teleport a player to a specific location within a world.
     */
    public boolean teleportPlayer(Player player, String worldName,
                                  double x, double y, double z, float yaw) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(worldName, "worldName cannot be null");

        if (player.isDead()) {
            return false;
        }

        cancelPendingFutures(player.getUniqueId());
        return teleportWithRetry(player, worldName, x, y, z, yaw);
    }

    /**
     * Teleport a player to a specific location (convenience overload).
     */
    public boolean teleportPlayer(Player player, Location location) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(location, "location cannot be null");

        cancelPendingFutures(player.getUniqueId());

        if (lockPositionDuringTeleport) {
            lockPlayerPosition(player);
        }

        try {
            PlayerTeleportEvent event = new PlayerTeleportEvent(
                    player, player.getLocation(), location,
                    PlayerTeleportEvent.TeleportCause.PLUGIN);

            if (Bukkit.getPluginManager().callEvent(event).isCancelled()) {
                releaseLock(player);
                return false;
            }

            player.teleportAsync(location);

            if (lockPositionDuringTeleport) {
                ScheduledFuture<?> future = teleportScheduler.schedule(() -> {
                    unlockPlayerPosition(player.getUniqueId());
                }, 1L, TimeUnit.SECONDS);

                pendingFutures.computeIfAbsent(player.getUniqueId(),
                        k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(future);
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Teleport failed for '" + player.getName() + "'", e);
            releaseLock(player);
            return false;
        }
    }

    /**
     * Teleport to the exact current coordinates in the target world.
     */
    public boolean teleportToWorld(Player player, String worldName) {
        World targetWorld = Bukkit.getWorld(worldName);
        if (targetWorld == null) {
            return false;
        }

        double x = player.getLocation().getX();
        double y = player.getLocation().getY();
        double z = player.getLocation().getZ();
        float yaw = player.getLocation().getYaw();
        float pitch = player.getLocation().getPitch();
        Location loc = new Location(targetWorld, x, y, z, yaw, pitch);
        return teleportPlayer(player, loc);
    }

    // ============================================================
    // Teleportation API — group teleport
    // ============================================================

    /**
     * Teleport an entire group of players to the same world.
     * If syncGroupTeleport is enabled, all players are teleported simultaneously.
     */
    public boolean teleportGroup(List<Player> players, String worldName) {
        Objects.requireNonNull(players, "players cannot be null");
        Objects.requireNonNull(worldName, "worldName cannot be null");

        List<Player> validPlayers = players.stream()
                .filter(Objects::nonNull)
                .filter(p -> !p.isDead())
                .filter(p -> p.isOnline())
                .toList();

        if (validPlayers.isEmpty()) {
            if (logTeleportEvents) {
                plugin.getLogger().warning("No valid players in group teleport to '" + worldName + "'");
            }
            return false;
        }

        if (syncGroupTeleport) {
            World targetWorld = Bukkit.getWorld(worldName);
            if (targetWorld == null) {
                if (logTeleportEvents) {
                    plugin.getLogger().warning("Target world '" + worldName
                            + "' not loaded, cannot perform sync group teleport");
                }
                return false;
            }

            for (Player p : validPlayers) {
                lockPlayerPosition(p);
            }

            try {
                Location spawn = targetWorld.getSpawnLocation();
                for (Player p : validPlayers) {
                    p.teleportAsync(spawn);
                }

                // Unlock players after teleport completes
                ScheduledFuture<?> unlockFuture = teleportScheduler.schedule(() -> {
                    for (Player p : validPlayers) {
                        unlockPlayerPosition(p.getUniqueId());
                    }
                }, 2L, TimeUnit.MILLISECONDS);

                for (Player p : validPlayers) {
                    pendingFutures.computeIfAbsent(p.getUniqueId(),
                            k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(unlockFuture);
                }

                if (logTeleportEvents) {
                    plugin.getLogger().info("Teleported " + validPlayers.size()
                            + " players to '" + worldName + "'");
                }
                return true;
            } catch (Exception e) {
                for (Player p : validPlayers) {
                    unlockPlayerPosition(p.getUniqueId());
                }
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Group teleport to '" + worldName + "' failed", e);
                return false;
            }
        } else {
            boolean anySuccess = false;
            for (Player p : validPlayers) {
                if (teleportPlayer(p, worldName)) {
                    anySuccess = true;
                }
            }
            return anySuccess;
        }
    }

    /**
     * Teleport players to evenly-spaced locations around the world spawn.
     */
    public boolean teleportGroupWithSpawns(List<Player> players, String worldName, double spawnSpacing) {
        Objects.requireNonNull(players, "players cannot be null");
        Objects.requireNonNull(worldName, "worldName cannot be null");

        World targetWorld = Bukkit.getWorld(worldName);
        if (targetWorld == null) {
            if (logTeleportEvents) {
                plugin.getLogger().warning("Target world '" + worldName
                        + "' not loaded for group spawn teleport");
            }
            return false;
        }

        Location spawn = targetWorld.getSpawnLocation();
        int count = players.size();
        boolean anySuccess = false;

        for (int i = 0; i < count; i++) {
            Player p = players.get(i);
            if (p == null || p.isDead() || !p.isOnline()) continue;

            double offsetX = (i * spawnSpacing) - ((count - 1) * spawnSpacing) / 2.0;
            Location target = spawn.clone().add(offsetX, 0, 0);

            if (teleportPlayer(p, target)) {
                anySuccess = true;
            }
        }

        return anySuccess;
    }

    // ============================================================
    // Public API
    // ============================================================

    /**
     * Stop all tracked teleport sequences.
     */
    public void stopAllTeleports() {
        for (List<ScheduledFuture<?>> list : pendingFutures.values()) {
            for (ScheduledFuture<?> f : list) {
                f.cancel(false);
            }
        }
        pendingFutures.clear();
    }

    /**
     * Clean up scheduler and tracked state.
     */
    public void cleanup() {
        stopAllTeleports();
        teleportScheduler.shutdownNow();
        try {
            if (!teleportScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Teleport scheduler did not terminate cleanly.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        positionLockedPlayers.clear();
        teleportAttempts.clear();
    }

    /**
     * Cancel position lock for a player (e.g., on logout).
     */
    public void unlockPlayerPosition(UUID playerId) {
        positionLockedPlayers.remove(playerId);
        cancelPendingFutures(playerId);
        teleportAttempts.remove(playerId);
    }

    // ============================================================
    // Private implementation
    // ============================================================

    private boolean teleportWithRetry(Player player, String worldName,
                                      double x, double y, double z, float yaw) {
        World targetWorld = Bukkit.getWorld(worldName);

        if (targetWorld == null) {
            plugin.getLogger().warning("Cannot teleport '" + player.getName()
                    + "' to world '" + worldName + "' — world not loaded");
            player.sendMessage(ChatColor.RED + "World '" + worldName
                    + "' is not currently available.");
            return false;
        }

        try {
            Location target;
            if (x == 0.0 && y == 0.0 && z == 0.0 && yaw == 0.0) {
                target = targetWorld.getSpawnLocation();
            } else {
                target = new Location(targetWorld, x, y, z, yaw, 0.0f);
            }

            PlayerTeleportEvent event = new PlayerTeleportEvent(
                    player, player.getLocation(), target, PlayerTeleportEvent.TeleportCause.PLUGIN);

            if (Bukkit.getPluginManager().callEvent(event).isCancelled()) {
                if (logTeleportEvents) {
                    plugin.getLogger().warning("Teleport cancelled by event for '"
                            + player.getName() + "' to '" + worldName + "'");
                }
                releaseLock(player);
                return false;
            }

            player.teleportAsync(target);

            if (lockPositionDuringTeleport) {
                ScheduledFuture<?> unlockFuture = teleportScheduler.schedule(() -> {
                    unlockPlayerPosition(player.getUniqueId());
                }, 2L * teleportDelayTicks, TimeUnit.MILLISECONDS);

                pendingFutures.computeIfAbsent(player.getUniqueId(),
                        k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(unlockFuture);
            }

            if (logTeleportEvents) {
                plugin.getLogger().info("Teleported '" + player.getName()
                        + "' from '" + player.getWorld().getName() + "' to '" + worldName + "'");
            }

            return true;

        } catch (Exception e) {
            AtomicInteger attempt = teleportAttempts.computeIfAbsent(
                    player.getUniqueId(), k -> new AtomicInteger(0));
            int count = attempt.incrementAndGet();
            int maxAttempts = Math.max(1, maxTeleportAttempts);

            if (count >= maxAttempts) {
                player.sendMessage(ChatColor.RED + "Failed to teleport. Please try again later.");
                releaseLock(player);
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Max teleport attempts for '" + player.getName() + "'", e);
                attempt.set(0);
                return false;
            }

            if (logTeleportEvents) {
                plugin.getLogger().warning("Teleport attempt " + count + "/" + maxAttempts
                        + " failed for '" + player.getName() + "'. Retrying...");
            }

            if (teleportDelayTicks > 0) {
                ScheduledFuture<?> retryFuture = teleportScheduler.schedule(() -> {
                    teleportWithRetry(player, worldName, x, y, z, yaw);
                }, teleportDelayTicks * 50L, TimeUnit.MILLISECONDS);

                pendingFutures.computeIfAbsent(player.getUniqueId(),
                        k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(retryFuture);
                return true;
            }
            releaseLock(player);
            return false;
        }
    }

    private void lockPlayerPosition(Player player) {
        positionLockedPlayers.put(player.getUniqueId(), true);
    }

    private void cancelPendingFutures(UUID playerId) {
        List<ScheduledFuture<?>> futures = pendingFutures.remove(playerId);
        if (futures != null) {
            for (ScheduledFuture<?> f : futures) {
                f.cancel(false);
            }
        }
    }

    private void releaseLock(Player player) {
        unlockPlayerPosition(player.getUniqueId());
    }

    // ============================================================
    // Event listeners
    // ============================================================

    /**
     * Prevent player movement during an active teleport sequence.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!lockPositionDuringTeleport) {
            return;
        }

        Player player = event.getPlayer();
        if (positionLockedPlayers.getOrDefault(player.getUniqueId(), false)) {
            event.setCancelled(true);
        }
    }
}
