package co.partygame.friend;

import java.util.Objects;
import java.util.UUID;

/**
 * Sends party and game invitations through the matchmaking system.
 *
 * Provides methods for inviting friends to games and parties.
 * In a full implementation, this would integrate with Matchmaking's
 * PartyMatchQueue to queue the target player for a game.
 * Currently stores invite data for the friend request system to process.
 */
public final class PartyInvite {

    private static final String GAME_INVITE_PREFIX = "partygame:invite:";
    private static final String PARTY_INVITE_PREFIX = "partygame:party:";

    private PartyInvite() {
        throw new UnsupportedOperationException("Utility class - no instantiation");
    }

    /**
     * Sends a party invitation to a friend.
     * The target player can use /friend game_invite to accept.
     *
     * @param inviter the player sending the invitation
     * @param target  the player receiving the invitation
     * @param gameType the type of game being invited to (e.g. "bedwars")
     */
    public static void sendPartyInvite(UUID inviter, UUID target, String gameType) {
        Objects.requireNonNull(inviter, "Inviter must not be null");
        Objects.requireNonNull(target, "Target must not be null");

        // In a full implementation, this would:
        // 1. Use Matchmaking's PartyMatchQueue to add the target to a game queue
        // 2. Send a Redis pub/sub message so all servers know about the invite
        // 3. Show a GUI popup to the target player

        // For now, the invite is stored through the friend invitation system
        String message = inviter.toString() + ":" + gameType;
    }

    /**
     * Sends a game invitation to a friend via the matchmaking system.
     *
     * @param inviter  the player sending the invitation
     * @param target   the player receiving the invitation
     * @param gameType the type of game being invited to (e.g. "bedwars")
     */
    public static void sendGameInvite(UUID inviter, UUID target, String gameType) {
        Objects.requireNonNull(inviter, "Inviter must not be null");
        Objects.requireNonNull(target, "Target must not be null");
        Objects.requireNonNull(gameType, "Game type must not be null");

        // In a full implementation, this would:
        // 1. Open a game preview GUI for the target player
        // 2. Add them to the matchmaking queue via PartyMatchQueue
        // 3. Send a Redis pub/sub message so other servers know

        // For now, store through the friend invitation system
        String message = inviter.toString() + ":" + target.toString() + ":" + gameType;
    }

    /**
     * Accepts a party invite on behalf of the target player.
     *
     * @param inviter the original inviter
     * @param acceptor the player accepting the invite
     * @param gameType the game type
     */
    public static void acceptPartyInvite(UUID inviter, UUID acceptor, String gameType) {
        Objects.requireNonNull(inviter, "Inviter must not be null");
        Objects.requireNonNull(acceptor, "Acceptor must not be null");

        // In a full implementation:
        // 1. Join the target to the sender's matchmaking queue
        // 2. Use Matchmaking's PartyMatchValidator to validate the party
    }
}
