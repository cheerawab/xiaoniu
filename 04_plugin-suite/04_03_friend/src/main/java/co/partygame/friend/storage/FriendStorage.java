package co.partygame.friend.storage;

import co.partygame.common.mysql.MySQLManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles MySQL CRUD operations for the friend system.
 *
 * Provides methods for adding, removing, blocking, and ignoring friends,
 * as well as managing friend invites. All operations use prepared statements
 * to prevent SQL injection. Thread-safe for concurrent async access.
 */
public class FriendStorage {

    private static final Logger LOGGER = Logger.getLogger(FriendStorage.class.getName());

    private final MySQLManager db;
    private static final String TABLE_FRIENDS = "pg_friends";
    private static final String TABLE_INVITES = "pg_friend_invites";

    /**
     * Creates a new FriendStorage instance.
     *
     * @param db the MySQL connection manager
     */
    public FriendStorage(MySQLManager db) {
        this.db = Objects.requireNonNull(db);
    }

    /**
     * Initializes the friend tables if they don't exist.
     * Uses DatabaseTables common-lib for DDL statements.
     */
    public void initTables() {
        createInviteTable();

        // Try to add created_at column if it doesn't exist (migration)
        try {
            db.update("ALTER TABLE `" + TABLE_FRIENDS + "` ADD COLUMN IF NOT EXISTS `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
        } catch (SQLException e) {
            // Column might already exist or MySQL doesn't support IF NOT EXISTS
        }
    }

    private void createInviteTable() {
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS `%s` (" +
                        "`id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, " +
                        "`inviter_uuid` VARCHAR(32) NOT NULL, " +
                        "`inviter_name` VARCHAR(64) NOT NULL, " +
                        "`party_id` VARCHAR(32) DEFAULT NULL, " +
                        "`acceptor_uuid` VARCHAR(32) NOT NULL, " +
                        "`status` VARCHAR(32) NOT NULL DEFAULT 'PENDING', " +
                        "`created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "UNIQUE KEY `unique_invite` (`inviter_uuid`, `acceptor_uuid`), " +
                        "KEY `idx_acceptor` (`acceptor_uuid`) " +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                TABLE_INVITES);
        db.update(sql);
    }

    /**
     * Adds a friend relationship.
     * Uses INSERT ON DUPLICATE KEY to handle re-adding.
     *
     * @param playerId   the player's UUID
     * @param friendId   the friend's UUID
     * @param status     the relationship status (FRIEND, BLOCKED, IGNORED)
     */
    public void addFriend(UUID playerId, UUID friendId, String status) {
        String sql = String.format(
                "INSERT INTO `%s` (`player_uuid`, `friend_uuid`, `status`) " +
                        "VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE `status` = VALUES(`status`) " +
                        "ON DUPLICATE KEY UPDATE status=?",
                TABLE_FRIENDS);
        db.update(sql, playerId.toString(), friendId.toString(), status);
    }

    /**
     * Removes friendship in both directions from the database.
     *
     * @param playerId the initiating player's UUID
     * @param friendId the friend's UUID to remove
     */
    public void removeFriend(UUID playerId, UUID friendId) {
        db.update(
                "DELETE FROM `%s` WHERE (`player_uuid` = ? AND `friend_uuid` = ?) " +
                        "OR (`player_uuid` = ? AND `friend_uuid` = ?)",
                TABLE_FRIENDS, playerId.toString(), friendId.toString(),
                friendId.toString(), playerId.toString());
    }

    /**
     * Marks a player as blocked (BLOCKED status).
     * Blocks all interactions in both directions.
     *
     * @param playerId the player doing the blocking
     * @param targetId the player being blocked
     */
    public void blockPlayer(UUID playerId, UUID targetId) {
        addFriend(playerId, targetId, "BLOCKED");
    }

    /**
     * Removes block relationships in both directions.
     *
     * @param playerId the player removing the block
     * @param targetId the target of the block
     */
    public void unblockPlayer(UUID playerId, UUID targetId) {
        removeFriend(playerId, targetId);
        db.update(
                "UPDATE `%s` SET `status` = 'FRIEND' WHERE `player_uuid` = ? AND `friend_uuid` = ?",
                TABLE_FRIENDS, playerId.toString(), targetId.toString());
        db.update(
                "UPDATE `%s` SET `status` = 'FRIEND' WHERE `player_uuid` = ? AND `friend_uuid` = ?",
                TABLE_FRIENDS, targetId.toString(), playerId.toString());
    }

    /**
     * Sets ignore status on a player.
     * IGNORED status blocks chat messages but allows other interactions.
     *
     * @param playerId the player doing the ignoring
     * @param targetId the player being ignored
     */
    public void ignorePlayer(UUID playerId, UUID targetId) {
        addFriend(playerId, targetId, "IGNORED");
    }

    /**
     * Removes ignore status in both directions.
     *
     * @param playerId the player removing the ignore
     * @param targetId the target of the ignore
     */
    public void unignorePlayer(UUID playerId, UUID targetId) {
        removeFriend(playerId, targetId);
        db.update(
                "UPDATE `%s` SET `status` = 'FRIEND' WHERE `player_uuid` = ? AND `friend_uuid` = ?",
                TABLE_FRIENDS, playerId.toString(), targetId.toString());
        db.update(
                "UPDATE `%s` SET `status` = 'FRIEND' WHERE `player_uuid` = ? AND `friend_uuid` = ?",
                TABLE_FRIENDS, targetId.toString(), playerId.toString());
    }

    /**
     * Retrieves the list of friend UUIDs for a player.
     *
     * @param playerId the player's UUID
     * @return list of friend UUIDs
     */
    public List<UUID> getFriendsList(UUID playerId) {
        List<UUID> friends = new ArrayList<>();

        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT `friend_uuid` FROM `" + TABLE_FRIENDS + "` WHERE `player_uuid` = ? AND `status` = 'FRIEND' " +
                             "ORDER BY `created_at` DESC")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    try {
                        friends.add(UUID.fromString(rs.getString("friend_uuid")));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query friends", e);
        }

        return friends;
    }

    /**
     * Returns ALL friend UUIDs for a player regardless of status.
     * Used by Redis sync and cross-server data loading.
     *
     * @param playerId the player's UUID
     * @return list of friend UUIDs
     */
    public List<UUID> getAllFriends(UUID playerId) {
        List<UUID> results = new ArrayList<>();

        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT `friend_uuid` FROM `" + TABLE_FRIENDS + "` " +
                             "WHERE (`player_uuid` = ? AND `status` = 'FRIEND') " +
                             "OR (`player_uuid` = ? AND `status` = 'FRIEND') " +
                             "ORDER BY `friend_uuid`")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    try {
                        results.add(UUID.fromString(rs.getString("friend_uuid")));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to get all friends for " + playerId, e);
        }

        return results;
    }

    /**
     * Retrieves pending friend invites for a player.
     *
     * @param playerId the player who received invites
     * @return list of InviteInfo objects
     */
    public List<FriendStorage.InviteInfo> getPendingInvites(UUID playerId) {
        List<FriendStorage.InviteInfo> invites = new ArrayList<>();

        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT `inviter_uuid`, `inviter_name`, `party_id` " +
                             "FROM `%s` WHERE `acceptor_uuid` = ? AND `status` = 'PENDING' " +
                             "ORDER BY `created_at` DESC",
                     TABLE_INVITES)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID inviter = UUID.fromString(rs.getString("inviter_uuid"));
                    String inviterName = rs.getString("inviter_name");
                    String partyId = rs.getString("party_id");
                    UUID partyIdUUID = null;
                    if (partyId != null && !partyId.isEmpty()) {
                        try {
                            partyIdUUID = UUID.fromString(partyId);
                        } catch (IllegalArgumentException ignored) {}
                    }
                    invites.add(new FriendStorage.InviteInfo(inviter, inviterName, partyIdUUID));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query invites", e);
        }

        return invites;
    }

    /**
     * Accepts a friend invite by creating friendships and removing the invite.
     *
     * @param inviter         the inviter's UUID
     * @param inviterName     the inviter's name
     * @param inviterPartyId  the party ID (if any)
     * @param acceptor        the accepting player's UUID
     */
    public void acceptInvite(UUID inviter, String inviterName, UUID inviterPartyId, UUID acceptor) {
        // Create friend relationships both ways
        addFriend(acceptor, inviter, "FRIEND");
        // Remove the invite record
        deleteInvite(inviter, acceptor);
    }

    /**
     * Checks if a pending invite exists from inviter to acceptor.
     *
     * @param inviter    the inviter's UUID
     * @param acceptor   the acceptor's UUID
     * @return true if invite exists and is pending
     */
    public boolean hasPendingInvite(UUID inviter, UUID acceptor) {
        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM `%s` WHERE `inviter_uuid` = ? AND `acceptor_uuid` = ? AND `status` = 'PENDING'",
                     TABLE_INVITES)) {
            statement.setString(1, inviter.toString());
            statement.setString(2, acceptor.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to check pending invites", e);
        }
        return false;
    }

    /**
     * Gets all blocked and ignored relationships for a player.
     *
     * @param playerId the player's UUID
     * @return list of FriendRecord with their status
     */
    public List<FriendRecord> getBlocksIgnored(UUID playerId) {
        List<FriendRecord> result = new ArrayList<>();

        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT `friend_uuid`, `status` FROM `%s` WHERE `player_uuid` = ? " +
                             "AND `status` IN ('BLOCKED', 'IGNORED') ORDER BY `status`, `created_at` DESC",
                     TABLE_FRIENDS)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("friend_uuid"));
                    String status = rs.getString("status");
                    Player player = Bukkit.getPlayer(uuid);
                    String name = player != null ? player.getName() : "Unknown";
                    boolean online = player != null && player.isOnline();
                    result.add(new FriendRecord(uuid, name, online, status));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to get blocks/ignores", e);
        }

        return result;
    }

    /**
     * Sends/updates a friend invite to a player.
     *
     * @param inviter    the player sending the request
     * @param inviterName the sender's display name
     * @param partyId    the party ID being invited to (can be null)
     * @param acceptor   the player receiving the invite
     */
    public void addInvite(UUID inviter, String inviterName,
                        UUID partyId, UUID acceptor) {
        String partyIdStr = partyId != null ? partyId.toString() : "";
        Object[] params = new Object[]{
                inviter.toString(),
                inviterName,
                partyIdStr,
                acceptor.toString()
        };

        // On conflict, refresh the timestamp so invites at the top
        db.update(
                "INSERT INTO `%s` (`inviter_uuid`, `inviter_name`, `party_id`, `acceptor_uuid`, `status`) " +
                        "VALUES (?, ?, ?, ?, 'PENDING')" +
                        "ON DUPLICATE KEY UPDATE `created_at` = NOW(), `status` = 'PENDING'",
                TABLE_INVITES, params);
    }

    /**
     * Accepts/removes an invite from the database.
     *
     * @param inviter    the inviter's UUID
     * @param acceptor   the acceptor's UUID
     */
    public void acceptInvite(UUID inviter, UUID acceptor) {
        deleteInvite(inviter, acceptor);
    }

    /**
     * Deletes/removes an invite record from the database.
     *
     * @param inviter    the inviter's UUID
     * @param acceptor   the acceptor's UUID
     */
    public void deleteInvite(UUID inviter, UUID acceptor) {
        db.update(
                "DELETE FROM `%s` WHERE `inviter_uuid` = ? AND `acceptor_uuid` = ?",
                TABLE_INVITES, inviter.toString(), acceptor.toString());
    }

    /**
     * Inner class representing an invite record from the database.
     */
    public static class InviteInfo {
        public final UUID inviter;
        public final String inviterName;
        public final UUID partyId;

        public InviteInfo(UUID inviter, String inviterName, UUID partyId) {
            this.inviter = inviter;
            this.inviterName = inviterName;
            this.partyId = partyId;
        }
    }
}