package co.partygame.friend;

import co.partygame.common.util.TimeUtils;
import co.partygame.friend.config.FriendConfig;
import co.partygame.friend.storage.FriendRecord;
import co.partygame.friend.storage.FriendStorage;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages friend lists, friend requests, blocks, and ignores for all players.
 *
 * Provides an in-memory cache layer on top of FriendStorage, with automatic
 * and explicit persistence to MySQL. All operations are thread-safe using
 * ConcurrentHashMap and synchronized blocks where needed.
 */
public class FriendManager {

    private static final Logger LOGGER = Logger.getLogger(FriendManager.class.getName());

    private final FriendStorage storage;
    private final FriendConfig config;
    private final Map<UUID, Set<UUID>> friendCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> blockCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> ignoreCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, InviteInfo>> inviteCache = new ConcurrentHashMap<>();

    public FriendManager(FriendStorage storage, FriendConfig config) {
        this.storage = Objects.requireNonNull(storage);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Adds a friend relationship between two players.
     * Removes any pending friend request between them.
     * Both directions are set (A is friend of B, B is friend of A).
     *
     * @param playerId     the player adding the friend
     * @param friendId     the friend to add
     * @param friendName   the friend's display name
     */
    public void addFriend(UUID playerId, UUID friendId, String friendName) {
        // Add both directions
        addRelationship(playerId, friendId, "FRIEND");
        addRelationship(friendId, playerId, "FRIEND");
        // Remove any pending invites between them
        clearInvite(playerId, friendId);
        clearInvite(friendId, playerId);
    }

    private void addRelationship(UUID from, UUID to, String status) {
        storage.addFriend(from, to, status);
        friendCache.computeIfAbsent(from, k -> ConcurrentHashMap.newKeySet()).add(to);
        blockCache.computeIfPresent(from, (k, set) -> {
            set.remove(to);
            return set;
        });
        ignoreCache.computeIfPresent(from, (k, set) -> {
            set.remove(to);
            return set;
        });
    }

    /**
     * Removes a friendship relationship both ways.
     *
     * @param playerId the player initiating removal
     * @param friendId the friend to remove
     */
    public void removeFriend(UUID playerId, UUID friendId) {
        storage.removeFriend(playerId, friendId);
        friendCache.computeIfPresent(playerId, (k, set) -> {
            set.remove(friendId);
            if (set.isEmpty()) return null;
            return set;
        });
        friendCache.computeIfPresent(friendId, (k, set) -> {
            set.remove(playerId);
            if (set.isEmpty()) return null;
            return set;
        });
    }

    /**
     * Blocks a player. Creates a BLOCKED relationship both ways,
     * and removes any existing friendship.
     *
     * @param playerId      the player doing the blocking
     * @param targetId      the player being blocked
     * @param targetName    the target's display name
     */
    public void blockPlayer(UUID playerId, UUID targetId, String targetName) {
        addFriend(playerId, targetId, targetName);
        storage.blockPlayer(playerId, targetId);
        addBlock(playerId, targetId);
        addBlock(targetId, playerId);
    }

    private void addBlock(UUID from, UUID to) {
        storage.blockPlayer(from, to);
        blockCache.computeIfAbsent(from, k -> ConcurrentHashMap.newKeySet()).add(to);
        friendCache.computeIfPresent(from, (k, set) -> {
            set.remove(to);
            return set;
        });
    }

    /**
     * Removes a block relationship both ways.
     *
     * @param playerId the player removing the block
     * @param targetId the target of the block
     */
    public void unblockPlayer(UUID playerId, UUID targetId) {
        storage.unblockPlayer(playerId, targetId);
        blockCache.computeIfPresent(playerId, (k, set) -> {
            set.remove(targetId);
            return set;
        });
        blockCache.computeIfPresent(targetId, (k, set) -> {
            set.remove(playerId);
            return set;
        });
    }

    /**
     * Ignores a player. Creates an IGNORED relationship (blocks chat messages from them).
     *
     * @param playerId the player doing the ignoring
     * @param targetId the player being ignored
     */
    public void ignorePlayer(UUID playerId, UUID targetId) {
        storage.ignorePlayer(playerId, targetId);
        addIgnore(playerId, targetId);
    }

    private void addIgnore(UUID from, UUID to) {
        ignoreCache.computeIfAbsent(from, k -> ConcurrentHashMap.newKeySet()).add(to);
        friendCache.computeIfPresent(from, (k, set) -> {
            set.remove(to);
            return set;
        });
    }

    /**
     * Stops ignoring a previously ignored player.
     *
     * @param playerId the player removing the ignore
     * @param targetId the target of the ignore
     */
    public void unignorePlayer(UUID playerId, UUID targetId) {
        storage.unignorePlayer(playerId, targetId);
        ignoreCache.computeIfPresent(playerId, (k, set) -> {
            set.remove(targetId);
            return set;
        });
    }

    /**
     * Returns the set of all friends for a player.
     *
     * @param playerId the player's UUID
     * @return set of friend UUIDs
     */
    public Set<UUID> getFriends(UUID playerId) {
        Set<UUID> friends = friendCache.get(playerId);
        if (friends != null) return Collections.unmodifiableSet(friends);

        Set<UUID> loaded = new HashSet<>();
        loadFriendsFromCacheOrStorage(playerId, loaded);
        // Merge cache - load any missing data first
        loadFriendsFromCacheOrStorage(playerId, loaded);
        return Collections.unmodifiableSet(friendsCache.getOrDefault(playerId, Set.of()));
    }

    private void loadFriendsFromCacheOrStorage(UUID playerId, Set<UUID> loaded) {
        friendCache.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        for (UUID friendId : storage.getFriendsList(playerId)) {
            if (friendId != null) {
                friendCache.get(playerId).add(friendId);
                loaded.add(friendId);
            }
        }
    }

    /**
     * Checks if two players are friends.
     *
     * @param playerId    the first player
     * @param targetId    the second player
     * @return true if they are friends
     */
    public boolean isFriend(UUID playerId, UUID targetId) {
        Set<UUID> friends = friendCache.get(playerId);
        if (friends != null && friends.contains(targetId)) return true;
        // Also check if target has this player as a friend (bidirectional)
        Set<UUID> targetFriends = friendCache.get(targetId);
        return targetFriends != null && targetFriends.contains(playerId);
    }

    /**
     * Returns map of pending invites a player has received.
     *
     * @param playerId the player receiving invites
     * @return map from inviter UUID to InviteInfo
     */
    public Map<UUID, InviteInfo> getPendingInvites(UUID playerId) {
        Map<UUID, InviteInfo> invites = inviteCache.get(playerId);
        if (invites != null) return new HashMap<>(invites);

        Map<UUID, InviteInfo> loaded = new HashMap<>();
        for (InviteInfo info : storage.getPendingInvites(playerId)) {
            loaded.put(info.inviter, info);
        }
        if (!loaded.isEmpty()) {
            inviteCache.put(playerId, new ConcurrentHashMap<>(loaded));
        }
        return loaded;
    }

    /**
     * Accepts a friend request from the inviter.
     *
     * @param inviter         the player who sent the request
     * @param inviterName     the inviter's display name
     * @param inviterPartyId  the party ID (if any) the inviter was in
     * @param acceptor        the player accepting the request
     */
    public void acceptInvite(UUID inviter, String inviterName, UUID inviterPartyId, UUID acceptor) {
        addFriend(acceptor, inviter, inviterName);
        storage.acceptInvite(inviter, acceptor);
        removeInvite(inviter, acceptor);
        removeInvite(acceptor, inviter);
    }

    /**
     * Rejects a friend request.
     *
     * @param inviter the player whose request was rejected
     * @param rejector the player rejecting
     */
    public void rejectInvite(UUID inviter, UUID rejector) {
        storage.acceptInvite(inviter, rejector);
        removeInvite(inviter, rejector);
        removeInvite(rejector, inviter);
    }

    /**
     * Checks if a pending invite exists from the inviter to the acceptor.
     *
     * @param inviter  the inviter's UUID
     * @param acceptor the acceptor's UUID
     * @return true if there is a pending invite
     */
    public boolean hasPendingInvite(UUID inviter, UUID acceptor) {
        return storage.hasPendingInvite(inviter, acceptor);
    }

    /**
     * Checks if a player is on another player's block list.
     *
     * @param blocker  the player who might have blocked someone
     * @param blocked  the player who might be blocked
     * @return true if blocked
     */
    public boolean isOnBlockList(UUID blocker, UUID blocked) {
        Set<UUID> blocks = blockCache.get(blocker);
        if (blocks != null && blocks.contains(blocked)) return true;
        // Also check if the blocked has blocked the blocker (reciprocal block prevents interaction)
        Set<UUID> targetBlocks = blockCache.get(blocked);
        return targetBlocks != null && targetBlocks.contains(blocker);
    }

    /**
     * Checks if a player is on another's ignore list.
     *
     * @param ignoreSubject the player who might be ignored
     * @param ignoreTarget  the player who might be doing the ignoring
     * @return true if the message sender is on the recipient's ignore list
     */
    public boolean isOnIgnoreList(UUID ignoreSubject, UUID ignoreTarget) {
        Set<UUID> ignores = ignoreCache.get(ignoreTarget);
        if (ignores != null && ignores.contains(ignoreSubject)) return true;
        // Also check if the ignoreTarget is on ignoreSubject's ignore list
        Set<UUID> subjectIgnores = ignoreCache.get(ignoreSubject);
        return subjectIgnores != null && subjectIgnores.contains(ignoreTarget);
    }

    /**
     * Checks whether a sender can message a receiver based on block and ignore rules.
     * Message is blocked if either side has the other in block/ignore list.
     *
     * @param sender the message sender
     * @param receiver the message receiver
     * @return true if messaging is allowed
     */
    public boolean canMessage(UUID sender, UUID receiver) {
        if (!isOnBlockList(sender, receiver)) return true;
        if (isOnBlockList(sender, receiver)) return false;
        if (isOnIgnoreList(sender, receiver)) return false;
        return true;
    }

    /**
     * Checks whether a player can join a party with another player.
     * Returns false if either has the other blocked or ignored.
     *
     * @param inviter the party inviter
     * @param target  the target of the invite
     * @return true if party operations are allowed between them
     */
    public boolean canJoinParty(UUID inviter, UUID target) {
        if (isOnBlockList(inviter, target)) return false;
        if (isOnIgnoreList(inviter, target)) return false;
        return true;
    }

    /**
     * Gets the number of a player's friends who are currently online.
     *
     * @param playerId the player whose friends to check
     * @return count of online friends
     */
    public int getOnlineCount(UUID playerId) {
        Set<UUID> friends = friendCache.get(playerId);
        if (friends == null || friends.isEmpty()) return 0;
        int count = 0;
        for (UUID friendId : friends) {
            if (Bukkit.getPlayer(friendId) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Checks if a specific friend UUID is online from the perspective of a player.
     *
     * @param playerId the player checking
     * @param uuid     the friend's UUID to check
     * @return true if the friend is online
     */
    public boolean getOnlineStatus(UUID playerId, UUID uuid) {
        if (!friendCache.getOrDefault(playerId, Collections.emptySet()).contains(uuid)) return false;
        return Bukkit.getPlayer(uuid) != null;
    }

    /**
     * Returns a full list of a player's friends with online status.
     *
     * @param playerId the player's UUID
     * @return list of FriendInfo objects
     */
    public List<FriendInfo> getPlayerFriendList(UUID playerId) {
        loadFriendsFromCacheOrStorage(playerId, new HashSet<>());
        List<FriendInfo> result = new ArrayList<>();
        Set<UUID> friends = friendCache.getOrDefault(playerId, Collections.emptySet());

        for (UUID friendId : friends) {
            Player friendPlayer = Bukkit.getPlayer(friendId);
            String name = friendPlayer != null ? friendPlayer.getName() : "Unknown";
            long lastSeen = 0;
            if (friendPlayer == null) {
                lastSeen = TimeUtils.now();
            }
            result.add(new FriendInfo(friendId, name, friendPlayer != null, lastSeen));
        }
        return result;
    }

    /**
     * Persists the in-memory friend data for a player to MySQL.
     *
     * @param playerId the player's UUID
     */
    public void saveChanges(UUID playerId) {
        Set<UUID> friends = friendCache.get(playerId);
        if (friends != null && !friends.isEmpty()) {
            Set<UUID> blocks = blockCache.get(playerId);
            for (UUID friendId : friends) {
                if (blocks != null && blocks.contains(friendId)) {
                    // Already saved as blocked, skip
                } else {
                    storage.addFriend(playerId, friendId, "FRIEND");
                }
            }
        }

        if (inviteCache.containsKey(playerId)) {
            // Invites are handled separately via storage
        }
    }

    /**
     * Loads all friend UUIDs for a player from storage if not in memory cache.
     * Used for Redis sync and cross-server data loading.
     *
     * @param playerId the player's UUID (as string for Redis compatibility)
     * @return set of all friend UUIDs
     */
    public Set<UUID> getAllFriends(String playerId) {
        return getAllFriends(UUID.fromString(playerId));
    }

    /**
     * Loads all friend UUIDs for a player.
     *
     * @param playerId the player's UUID
     * @return set of all friend UUIDs
     */
    public Set<UUID> getAllFriends(UUID playerId) {
        Set<UUID> friends = friendCache.get(playerId);
        if (friends != null) {
            return Collections.unmodifiableSet(friends);
        }

        Set<UUID> loaded = new HashSet<>();
        for (UUID f : storage.getFriendsList(playerId)) {
            if (f != null) {
                friendCache.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(f);
                loaded.add(f);
            }
        }

        if (!loaded.isEmpty()) {
            return Collections.unmodifiableSet(friendCache.get(playerId));
        }
        return Set.of();
    }

    /**
     * Reloads configuration values used by the friend manager.
     * This should be called when the config file is reloaded.
     */
    public void reloadConfig() {
        LOGGER.info("Friend configuration reloaded");
    }

    /**
     * Gets the count of blocked players for a specific player.
     *
     * @param playerId the player whose blocks to count
     * @return count of blocked players
     */
    public int getPlayerBlockedCount(UUID playerId) {
        Set<UUID> blocks = blockCache.get(playerId);
        return blocks != null ? blocks.size() : 0;
    }

    /**
     * Gets the count of ignored players for a specific player.
     *
     * @param playerId the player whose ignores to count
     * @return count of ignored players
     */
    public int getPlayerIgnoreCount(UUID playerId) {
        Set<UUID> ignores = ignoreCache.get(playerId);
        return ignores != null ? ignores.size() : 0;
    }

    /**
     * Gets the full block list (cached).
     *
     * @param playerId the player's UUID
     * @return set of blocked UUIDs
     */
    public Set<UUID> getBlockCache() {
        return blockCache;
    }

    /**
     * Creates a list of blocked players with names.
     *
     * @param playerId the player's UUID
     * @return list of FriendRecords for blocked players
     */
    public List<FriendRecord> getBlockList(UUID playerId) {
        List<FriendRecord> result = new ArrayList<>();
        storage.getBlocksIgnored(playerId).forEach(r -> {
            if ("BLOCKED".equals(r.getStatus())) {
                result.add(r);
            }
        });
        return result;
    }

    /**
     * Creates a list of ignored players with names.
     *
     * @param playerId the player's UUID
     * @return list of FriendRecords for ignored players
     */
    public List<FriendRecord> getIgnoreList(UUID playerId) {
        List<FriendRecord> result = new ArrayList<>();
        storage.getBlocksIgnored(playerId).forEach(r -> {
            if ("IGNORED".equals(r.getStatus())) {
                result.add(r);
            }
        });
        return result;
    }

    // ─── Invite Data ─────────────────────────────────────────────────

    /**
     * Sends a friend request to a player.
     *
     * @param inviter      the player sending the request
     * @param receiver     the player receiving the request
     * @param inviterName  the inviter's display name
     * @param partyId      the party ID (if any)
     */
    public void sendInvite(UUID inviter, UUID receiver, String inviterName, UUID partyId) {
        storage.addInvite(inviter, inviterName, partyId, receiver);

        inviteCache.computeIfAbsent(receiver, k -> new ConcurrentHashMap<>())
                .put(inviter, new InviteInfo(inviter, inviterName, partyId));
    }

    /**
     * Removes a pending invite from mem cache.
     *
     * @param from the sender of invite
     * @param to   the receiver of invite
     */
    private void removeInvite(UUID from, UUID to) {
        Map<UUID, InviteInfo> invites = inviteCache.get(to);
        if (invites != null) {
            invites.remove(from);
            if (invites.isEmpty()) {
                inviteCache.remove(to);
            }
        }
    }

    /**
     * Inner class representing a pending friend invite.
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

    /**
     * Inner class representing a friend with status info.
     */
    public static class FriendInfo {
        public final UUID id;
        public final String name;
        public final boolean online;
        public final long lastSeen;

        public FriendInfo(UUID id, String name, boolean online, long lastSeen) {
            this.id = id;
            this.name = name;
            this.online = online;
            this.lastSeen = lastSeen;
        }
    }
}