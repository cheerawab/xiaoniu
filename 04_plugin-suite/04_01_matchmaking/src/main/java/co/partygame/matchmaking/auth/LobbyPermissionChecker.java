package co.partygame.matchmaking.auth;

import co.partygame.common.auth.PermissionManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Performs permission checks BEFORE matchmaking (in lobby).
 * Uses PermissionManager (lazy lookup via Bukkit ServicesManager) for external
 * auth system integration such as LuckPerms.
 * No hardcoded ranks - all based on LP configuration.
 */
public class LobbyPermissionChecker {

    private static final Logger LOGGER = Logger.getLogger(LobbyPermissionChecker.class.getName());

    private final PermissionManager permissionManager;

    public LobbyPermissionChecker(PermissionManager permissionManager) {
        this.permissionManager = Objects.requireNonNull(permissionManager);
    }

    /**
     * Checks if a player can join a matchmaking queue for a specific game.
     * Requires both partygame.match.join AND partygame.game.{gameId}.
     *
     * @param player the player to check
     * @param gameId the game identifier
     * @return true if the player can join the match
     */
    public boolean canJoinMatch(Player player, String gameId) {
        if (player == null || gameId == null) return false;
        try {
            return permissionManager.hasPermission(player, "partygame.match.join")
                    && permissionManager.hasPermission(player, "partygame.game." + gameId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Permission check failed for " + player.getName() + ": " + gameId, e);
            return false;
        }
    }

    /**
     * Checks if a player has a specific permission.
     * Delegated directly to PermissionManager.
     *
     * @param player      the player to check
     * @param permission  the permission to check
     * @return true if the player has the permission
     * @throws IllegalArgumentException if arguments are null
     */
    public boolean hasPermission(Player player, String permission) {
        if (player == null || permission == null) return false;
        return permissionManager.hasPermission(player, permission);
    }

    /**
     * Checks if a player has ANY of the given permissions.
     *
     * @param player      the player to check
     * @param permissions the permissions to check
     * @return true if the player has at least one of the permissions
     * @throws IllegalArgumentException if arguments are null
     */
    public boolean hasAnyPermission(Player player, String... permissions) {
        if (player == null || permissions == null || permissions.length == 0) return false;
        return permissionManager.hasAnyPermission(player, permissions);
    }

    /**
     * Checks if a player has ALL of the given permissions.
     *
     * @param player      the player to check
     * @param permissions the permissions to check
     * @return true if the player has every permission listed
     * @throws IllegalArgumentException if arguments are null
     */
    public boolean hasAllPermissions(Player player, String... permissions) {
        if (player == null || permissions == null || permissions.length == 0) return false;
        return permissionManager.hasAllPermissions(player, permissions);
    }

    /**
     * Checks if a player can create a custom room.
     * Requires both partygame.match.custom_room AND partygame.custom_room.create.
     *
     * @param player the player to check
     * @return true if the player can create custom rooms
     */
    public boolean canCreateCustomRoom(Player player) {
        if (player == null) return false;
        return permissionManager.hasAllPermissions(
                player, "partygame.match.custom_room", "partygame.custom_room.create");
    }

    /**
     * Checks if a player can join a custom room.
     * Requires partygame.match.custom_room_join.
     *
     * @param player the player to check
     * @return true if the player can join custom rooms
     */
    public boolean canJoinCustomRoom(Player player) {
        if (player == null) return false;
        return permissionManager.hasPermission(player, "partygame.match.custom_room_join");
    }

    /**
     * Checks if a party leader can matchmaking with their party for a specific game.
     * Leader must have party.match and all members must have the game permission.
     *
     * @param leader    the party leader
     * @param members   the list of party members (including the leader)
     * @param gameId    the game identifier
     * @return true if the party can matchmaking together
     */
    public boolean canPartyMatch(Player leader, List<Player> members, String gameId) {
        if (leader == null || members == null || gameId == null) return false;

        if (!hasPermission(leader, "partygame.match")) return false;

        for (Player member : members) {
            if (!canJoinMatch(member, gameId)) return false;
        }

        return true;
    }

    /**
     * Gets the effective rank of a player.
     * Returns the primary group name from LuckPerms or null if not available.
     *
     * @param player the player to query
     * @return the primary group name (rank), or null if not available
     */
    public String getEffectiveRank(Player player) {
        if (player == null) return null;
        try {
            String rank = permissionManager.getPrimaryGroup(player);
            return rank != null && !rank.isEmpty() ? rank : null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get effective rank for " + player.getName(), e);
            return null;
        }
    }

    /**
     * Gets the primary group of a player without null checks.
     */
    String getPrimaryGroup(OfflinePlayer player) {
        try {
            return permissionManager.getPrimaryGroup(player);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the primary group of a player.
     */
    String getPrimaryGroup(Player player) {
        try {
            return permissionManager.getPrimaryGroup(player);
        } catch (Exception e) {
            return null;
        }
    }
}
