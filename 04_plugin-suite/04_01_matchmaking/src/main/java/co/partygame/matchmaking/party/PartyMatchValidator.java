package co.partygame.matchmaking.party;

import co.partygame.common.auth.PermissionManager;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Validates party matchmaking requirements.
 * Ensures all party members have the necessary permissions before matching.
 * Uses PermissionManager for checking permissions (lazy lookup via Bukkit ServicesManager).
 */
public class PartyMatchValidator {

    private final PermissionManager permissionManager;

    public PartyMatchValidator(PermissionManager permissionManager) {
        this.permissionManager = Objects.requireNonNull(permissionManager);
    }

    /**
     * Validates that all party members can join a specific game.
     * Checks that every member has partygame.match.join AND partygame.game.{gameId}.
     *
     * @param members the list of party members
     * @param gameId  the game identifier
     * @return true if all members can participate in the game
     */
    public boolean validateForMatch(List<Player> members, String gameId) {
        if (members == null || members.isEmpty()) return false;

        for (Player member : members) {
            if (!hasValidPermissions(member, gameId)) return false;
        }
        return true;
    }

    /**
     * Validates the party size based on the leader's rank and permissions.
     * Some game types restrict the maximum party size based on player rank.
     *
     * @param size    the party size
     * @param leader  the party leader
     * @return true if the party size is valid
     */
    public boolean validatePartySize(int size, Player leader) {
        if (size <= 0) return false;
        if (leader == null) return false;

        int maxPartySize = getMaxPartySize(null);
        if (permissionManager.hasPermission(leader, "partygame.party.size.large")) {
            maxPartySize = 8;
        } else if (permissionManager.hasPermission(leader, "partygame.party.size.medium")) {
            maxPartySize = 4;
        }

        return size <= maxPartySize;
    }

    /**
     * Validates that the party leader can select a specific game.
     *
     * @param leader the party leader
     * @param gameId the game identifier to validate
     * @return true if the leader can select this game
     */
    public boolean validateGameSelection(Player leader, String gameId) {
        if (leader == null || gameId == null) return false;

        return permissionManager.hasPermission(leader, "partygame.game." + gameId);
    }

    /**
     * Checks if the party members can play together for a given game.
     * All members must have matching permissions regardless of their rank.
     * Uses PermissionManager.hasPermission with context for server-specific checks.
     *
     * @param members the list of party members
     * @param gameId  the game identifier
     * @return true if the party can play this game together
     */
    public boolean canPlayTogether(List<Player> members, String gameId) {
        if (members == null || members.isEmpty()) return false;

        String contextKey = "partygame_server";
        String contextValue = "lobby";

        for (Player member : members) {
            if (!permissionManager.hasPermission(member, "partygame.match.join")) return false;
            if (!permissionManager.hasPermission(member, "partygame.game." + gameId,
                    contextKey, contextValue)) return false;
        }
        return true;
    }

    /**
     * Gets the maximum party size for a given context.
     * Falls back to default value if no specific config exists.
     *
     * @param context the context for size lookup (may be null)
     * @return the maximum party size
     */
    public int getMaxPartySize(String context) {
        if (context != null) {
            int configured = 0;
            try {
                configured = Integer.parseInt(context);
            } catch (NumberFormatException ignored) {}
            return configured > 0 ? configured : 4;
        }
        return 4;
    }

    private boolean hasValidPermissions(Player player, String gameId) {
        if (player == null || gameId == null) return false;
        return permissionManager.hasPermission(player, "partygame.match.join")
                && permissionManager.hasPermission(player, "partygame.game." + gameId);
    }
}
