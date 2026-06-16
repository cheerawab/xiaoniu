package co.partygame.friend.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base interface for all friend-related events.
 *
 * Events in this package are fired when friend actions occur, allowing
 * other plugins to listen and react to friendship changes.
 *
 * All events extend Event and use HandlerList for Bukkit's event bus.
 */
public class FriendEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    /**
     * Gets the handler list for friend events.
     *
     * @return the handler list
     */
    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    /**
     * Inner class: fired when a player is added as a friend.
     */
    public static final class FriendAddEvent extends FriendEvent {
        private final UUID player;
        private final UUID newFriend;
        private final boolean wasExisting;

        /**
         * Creates a new FriendAddEvent.
         *
         * @param player the player adding a friend
         * @param newFriend the friend that was added
         * @param wasExisting true if they were already friends
         */
        public FriendAddEvent(UUID player, UUID newFriend, boolean wasExisting) {
            super(true); // async
            this.player = Objects.requireNonNull(player);
            this.newFriend = Objects.requireNonNull(newFriend);
            this.wasExisting = wasExisting;
        }

        /**
         * Gets the UUID of the player who added the friend.
         *
         * @return the player's UUID
         */
        public UUID getPlayer() {
            return player;
        }

        /**
         * Gets the UUID of the new friend.
         *
         * @return the friend's UUID
         */
        public UUID getNewFriend() {
            return newFriend;
        }

        /**
         * Checks if the friendship already existed before this event.
         *
         * @return true if they were already friends
         */
        public boolean wasExisting() {
            return wasExisting;
        }
    }

    /**
     * Inner class: fired when a friendship is removed.
     */
    public static final class FriendRemoveEvent extends FriendEvent {
        private final UUID player;
        private final UUID removedFriend;

        /**
         * Creates a new FriendRemoveEvent.
         *
         * @param player the player initiating removal
         * @param removedFriend the friend that was removed
         */
        public FriendRemoveEvent(UUID player, UUID removedFriend) {
            super(false);
            this.player = Objects.requireNonNull(player);
            this.removedFriend = Objects.requireNonNull(removedFriend);
        }

        /**
         * Gets the UUID of the player who removed the friend.
         *
         * @return the player's UUID
         */
        public UUID getPlayer() {
            return player;
        }

        /**
         * Gets the UUID of the removed friend.
         *
         * @return the removed friend's UUID
         */
        public UUID getRemovedFriend() {
            return removedFriend;
        }
    }

    /**
     * Inner class: fired when a player blocks another.
     */
    public static final class FriendBlockEvent extends FriendEvent {
        private final UUID blocker;
        private final UUID blocked;

        /**
         * Creates a new FriendBlockEvent.
         *
         * @param blocker the player doing the blocking
         * @param blocked the player being blocked
         */
        public FriendBlockEvent(UUID blocker, UUID blocked) {
            super(false);
            this.blocker = Objects.requireNonNull(blocker);
            this.blocked = Objects.requireNonNull(blocked);
        }

        /**
         * Gets the UUID of the player doing the blocking.
         *
         * @return the blocker's UUID
         */
        public UUID getBlocker() {
            return blocker;
        }

        /**
         * Gets the UUID of the player being blocked.
         *
         * @return the blocked player's UUID
         */
        public UUID getBlocked() {
            return blocked;
        }
    }

    /**
     * Inner class: fired when a player ignores another.
     */
    public static class FriendIgnoreEvent extends FriendEvent {
        private final UUID ignorer;
        private final UUID ignored;

        /**
         * Creates a new FriendIgnoreEvent.
         *
         * @param ignorer the player doing the ignoring
         * @param ignored the player being ignored
         */
        public FriendIgnoreEvent(UUID ignorer, UUID ignored) {
            super(false);
            this.ignorer = Objects.requireNonNull(ignorer);
            this.ignored = Objects.requireNonNull(ignored);
        }

        /**
         * Gets the UUID of the player doing the ignoring.
         *
         * @return the ignorer's UUID
         */
        public UUID getIgnorer() {
            return ignorer;
        }

        /**
         * Gets the UUID of the player being ignored.
         *
         * @return the ignored player's UUID
         */
        public UUID getIgnored() {
            return ignored;
        }
    }

    /**
     * Inner class: fired when a player sends an invite.
     */
    public static class FriendInviteEvent extends FriendEvent {
        private final UUID inviter;
        private final UUID invitee;
        private final String gameType;

        /**
         * Creates a new FriendInviteEvent.
         *
         * @param inviter   the player sending the invite
         * @param invitee   the player receiving the invite
         * @param gameType  the type of game being invited to (e.g. "bedwars")
         */
        public FriendInviteEvent(UUID inviter, UUID invitee, String gameType) {
            super(false);
            this.inviter = Objects.requireNonNull(inviter);
            this.invitee = Objects.requireNonNull(invitee);
            this.gameType = gameType;
        }

        /**
         * Gets the UUID of the player sending the invite.
         *
         * @return the inviter's UUID
         */
        public UUID getInviter() {
            return inviter;
        }

        /**
         * Gets the UUID of the player receiving the invite.
         *
         * @return the invitee's UUID
         */
        public UUID getInvitee() {
            return invitee;
        }

        /**
         * Gets the type of game being invited to.
         *
         * @return the game type string
         */
        public String getGameType() {
            return gameType;
        }
    }
}