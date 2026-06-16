package co.partygame.friend.storage;

import java.util.UUID;

/**
 * Data class for friend records used in GUI rendering and text output.
 *
 * Provides a lightweight, immutable representation of a friend relationship
 * with status and online state information.
 */
public class FriendRecord {

    private final UUID uuid;
    private final String name;
    private final boolean online;
    private final String status;

    /**
     * Creates a new FriendRecord.
     *
     * @param uuid   the player's UUID
     * @param name   the player's display name
     * @param online whether the player is currently online
     * @param status the relationship status (FRIEND, BLOCKED, IGNORED)
     */
    public FriendRecord(UUID uuid, String name, boolean online, String status) {
        this.uuid = uuid;
        this.name = name;
        this.online = online;
        this.status = status != null ? status.toUpperCase() : "FRIEND";
    }

    /**
     * Gets the player's UUID.
     *
     * @return the UUID
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the player's display name.
     *
     * @return the name string
     */
    public String getName() {
        return name;
    }

    /**
     * Checks if the player is online.
     *
     * @return true if online
     */
    public boolean isOnline() {
        return online;
    }

    /**
     * Gets the relationship status.
     *
     * @return status string (FRIEND, BLOCKED, IGNORED)
     */
    public String getStatus() {
        return status;
    }

    /**
     * Creates a friendly display string for this record.
     *
     * @return formatted string: "name [status] [online/offline]"
     */
    @Override
    public String toString() {
        return name + " [" + status + "] " + (online ? "online" : "offline");
    }

    /**
     * Checks equality based on UUID.
     *
     * @param o the object to compare
     * @return true if UUIDs match
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FriendRecord record = (FriendRecord) o;
        return uuid.equals(record.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}