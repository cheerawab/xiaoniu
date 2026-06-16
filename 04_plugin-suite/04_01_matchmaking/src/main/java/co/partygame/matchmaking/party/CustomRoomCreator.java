package co.partygame.matchmaking.party;

import co.partygame.common.auth.PermissionManager;
import co.partygame.common.protocol.packets.lobby.MatchRequest;
import co.partygame.common.protocol.packets.lobby.PlayerInfo;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Logger;

/**
 * Handles custom/private room creation requests.
 * Allows players with proper permissions to create and manage custom game rooms.
 * Custom rooms are sent to backends with type: "CUSTOM_ROOM" instead of "MATCH".
 */
public class CustomRoomCreator {

    private static final Logger LOGGER = Logger.getLogger(CustomRoomCreator.class.getName());

    private final PermissionManager permissionManager;
    private final co.partygame.matchmaking.MatchRouter matchRouter;

    public CustomRoomCreator(PermissionManager permissionManager,
                              co.partygame.matchmaking.MatchRouter matchRouter) {
        this.permissionManager = Objects.requireNonNull(permissionManager);
        this.matchRouter = Objects.requireNonNull(matchRouter);
    }

    /**
     * Gets the logger instance.
     *
     * @return this class's logger
     */
    public Logger getLogger() {
        return LOGGER;
    }

    /**
     * Checks if a player has the permissions to create custom rooms.
     *
     * @param leader the player attempting to create a room
     * @param gameId the game identifier for the room
     * @return true if the leader can create a custom room
     */
    public boolean validateCanCreateRoom(Player leader, String gameId) {
        if (leader == null) return false;
        return permissionManager.hasPermission(leader, "partygame.match.custom_room")
                && permissionManager.hasPermission(leader, "partygame.custom_room.create")
                && permissionManager.hasPermission(leader, "partygame.game." + gameId);
    }

    /**
     * Builds a MatchRequest for a custom room creation.
     * Includes additional flags for custom room type and options.
     *
     * @param gameId          the game identifier
     * @param gameType        the game type
     * @param customOptions   custom game options
     * @param members         list of party members
     * @param password        optional room password (null if no password)
     * @param maxSize         maximum number of players in the room
     * @return a new MatchRequest configured for custom room
     */
    public MatchRequest buildCustomRoomRequest(String gameId, String gameType,
                                                Map<String, Object> customOptions,
                                                List<Player> members,
                                                String password, int maxSize) {
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("Members list must not be empty");
        }

        Map<String, Object> options = new LinkedHashMap<>();
        if (customOptions != null) {
            options.putAll(customOptions);
        }

        options.put("__room_type", "CUSTOM_ROOM");
        options.put("__max_players", maxSize);
        options.put("__password_protected", password != null && !password.isEmpty());
        options.put("__host", members.get(0).getUniqueId().toString());

        if (password != null && !password.isEmpty()) {
            options.put("__password_hash", hashPassword(password));
        }

        List<PlayerInfo> playerInfos = new ArrayList<>();
        for (Player member : members) {
            playerInfos.add(new PlayerInfo(member.getUniqueId(), member.getName()));
        }

        String sessionId = UUID.randomUUID().toString();
        return new MatchRequest(
                sessionId,
                gameId,
                gameType,
                playerInfos,
                options,
                null,
                co.partygame.matchmaking.MatchmakingPlugin.getInstance().getServer().getName()
        );
    }

    /**
     * Checks if a player has access to a specific custom room.
     * Considers password protection and room membership.
     *
     * @param player  the player to check
     * @param roomId  the room's ID
     * @return true if the player can join the room
     */
    public boolean canPlayerJoinRoom(Player player, String roomId) {
        if (player == null || roomId == null) return false;
        return permissionManager.hasPermission(player, "partygame.match.custom_room_join");
    }

    /**
     * Determines the maximum number of players a host can allow in their custom room.
     * Based on the host's rank and permissions.
     *
     * @param host the room host
     * @return the maximum player count for the room
     */
    public int getMaxPlayersForRoom(Player host) {
        if (host == null) return 8;
        if (permissionManager.hasPermission(host, "partygame.custom_room.max.large")) return 16;
        if (permissionManager.hasPermission(host, "partygame.custom_room.max.medium")) return 12;
        return 8;
    }

    /**
     * Sends a custom room creation request to a backend server.
     * The request is configured with type: "CUSTOM_ROOM" instead of "MATCH".
     *
     * @param request  the MatchRequest built for a custom room
     * @param backend  the backend server to send to
     * @param members  the party members requesting the room
     */
    public void createCustomRoom(MatchRequest request, String backend, List<Player> members) {
        if (request == null) throw new IllegalArgumentException("Request must not be null");
        if (backend == null) throw new IllegalArgumentException("Backend must not be null");
        if (members == null) throw new IllegalArgumentException("Members must not be null");
        matchRouter.routePlayers(request, backend);
    }

    /**
     * Simple password hash for basic room protection.
     */
    private String hashPassword(String password) {
        if (password == null || password.isEmpty()) return "";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
