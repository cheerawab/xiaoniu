package co.partygame.core.session;

import java.util.UUID;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Represents a room of 4-8 players playing together.
 *
 * <p>A PlayerRoom provides thread-safe management of player memberships
 * for a single game session. It is used by the SessionManager to track
 * which players belong to which game instance.</p>
 *
 * <p>This class uses a ReadWriteLock to allow concurrent reads while
 * ensuring exclusive access during writes.</p>
 */
public class PlayerRoom {

    private final UUID roomId;
    private final Set<UUID> members;
    private final ReadWriteLock lock;

    /**
     * Information about a player room, safe for serialization across network boundaries.
     */
    public static class RoomInfo {
        private final UUID roomId;
        private final java.util.List<String> members;
        private final int size;
        private final String status;

        public RoomInfo(UUID roomId, java.util.List<String> members, int size, String status) {
            this.roomId = roomId;
            this.members = Collections.unmodifiableList(members);
            this.size = size;
            this.status = status;
        }

        public UUID getRoomId() { return roomId; }
        public java.util.List<String> getMembers() { return members; }
        public int getSize() { return size; }
        public String getStatus() { return status; }

        @Override
        public String toString() {
            return "RoomInfo{roomId=" + roomId + ", size=" + size + ", status='" + status + "'}";
        }
    }

    /**
     * Creates a new PlayerRoom with a unique room ID.
     */
    public PlayerRoom() {
        this.roomId = UUID.randomUUID();
        this.members = ConcurrentHashMap.newKeySet();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Creates a new PlayerRoom with a specified room ID.
     *
     * @param roomId the room identifier
     */
    public PlayerRoom(UUID roomId) {
        this.roomId = Objects.requireNonNull(roomId, "roomId must not be null");
        this.members = ConcurrentHashMap.newKeySet();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Returns the room's unique ID.
     *
     * @return room UUID
     */
    public UUID getRoomId() {
        return roomId;
    }

    /**
     * Adds multiple members to the room.
     *
     * @param memberSet the set of player UUIDs to add
     * @return the actual number of members added (excluding duplicates)
     */
    public int addMembers(Set<UUID> memberSet) {
        Objects.requireNonNull(memberSet, "memberSet must not be null");
        lock.writeLock().lock();
        try {
            int added = 0;
            for (UUID member : memberSet) {
                if (members.add(member)) {
                    added++;
                }
            }
            return added;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns all members in this room.
     *
     * @return set of player UUIDs
     */
    public Set<UUID> getMembers() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(members);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if the room has reached maximum capacity (8 players).
     *
     * @return true if the room is full
     */
    public boolean isFull() {
        lock.readLock().lock();
        try {
            return members.size() >= 8;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if a specific player is a member of this room.
     *
     * @param uuid the player's UUID
     * @return true if the player is a member
     */
    public boolean containsPlayer(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID must not be null");
        lock.readLock().lock();
        try {
            return members.contains(uuid);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Retrieves a specific member by their UUID (returns the same UUID for verification).
     *
     * @param uuid the player's UUID
     * @return the UUID if the player is a member, null otherwise
     */
    public UUID getMember(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID must not be null");
        lock.readLock().lock();
        try {
            return members.contains(uuid) ? uuid : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Adds a single member to the room.
     *
     * @param member the player's UUID
     * @return true if the member was added (false if already present)
     */
    public boolean addMember(UUID member) {
        Objects.requireNonNull(member, "Member UUID must not be null");
        lock.writeLock().lock();
        try {
            return members.add(member);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a member from the room.
     *
     * @param uuid the player's UUID
     * @return true if the member was removed, false if not found
     */
    public boolean removeMember(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID must not be null");
        lock.writeLock().lock();
        try {
            return members.remove(uuid);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns an immutable set of all member UUIDs.
     *
     * @return unmodifiable set of player UUIDs
     */
    public Set<UUID> getAllMembers() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new java.util.HashSet<>(members));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the current number of members in the room.
     *
     * @return member count
     */
    public int size() {
        lock.readLock().lock();
        try {
            return members.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clears all members from the room.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            members.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Creates a snapshot of this room's current state.
     *
     * @return RoomInfo with current room data
     */
    public RoomInfo getInfo() {
        lock.readLock().lock();
        try {
            java.util.List<String> memberNames = members.stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());
            String status = isFull() ? "full" : (members.size() >= 4 ? "active" : "waiting");
            return new RoomInfo(roomId, memberNames, members.size(), status);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        return "PlayerRoom{roomId=" + roomId + ", members=" + size() + "}";
    }
}
