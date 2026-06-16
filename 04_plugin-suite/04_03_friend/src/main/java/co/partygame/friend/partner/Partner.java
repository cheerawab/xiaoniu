package co.partygame.friend.partner;

import co.partygame.friend.storage.FriendStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the "Partner" feature for 2-player duo mode minigames.
 *
 * Partners are a special relationship type used for games like duels,
 * where two players need to form a duo partnership. This system is
 * separate from the regular friend list but uses the same storage layer.
 */
public class Partner {

    private final FriendStorage storage;

    /**
     * Cache of active partnerships (player1 -> player2), bidirectionally stored.
     */
    private final Map<String, String> activePartners = new ConcurrentHashMap<>();

    /**
     * Creates a new Partner system.
     *
     * @param storage the storage layer
     */
    public Partner(FriendStorage storage) {
        this.storage = Objects.requireNonNull(storage);
    }

    /**
     * Creates a partner relationship between two players.
     * The first player to create is considered the "primary" partner.
     *
     * @param player1 the first player's UUID
     * @param player2 the second player's UUID
     * @return true if the partnership was created successfully
     *         false if either player already has a partner
     */
    public boolean createPartner(UUID player1, UUID player2) {
        // Check if either player already has a partner
        if (hasPartner(player1, null)) return false;
        if (hasPartner(player2, null)) return false;

        // Store bidirectionally
        activePartners.put(player1.toString(), player2.toString());
        activePartners.put(player2.toString(), player1.toString());

        // Persist to MySQL
        try {
            storage.addFriend(player1, player2, "PARTNER");
        } catch (Exception e) {
            // If storage fails, remove from cache and return false
            activePartners.remove(player1.toString());
            activePartners.remove(player2.toString());
            return false;
        }

        return true;
    }

    /**
     * Removes a partner relationship.
     *
     * @param player  either of the paired players
     * @return true if a partnership existed and was removed
     */
    public boolean removePartner(UUID player) {
        String partnerKey = activePartners.get(player.toString());
        if (partnerKey == null) return false;

        UUID partnerUUID;
        try {
            partnerUUID = UUID.fromString(partnerKey);
        } catch (IllegalArgumentException e) {
            return false;
        }

        activePartners.remove(player.toString());
        activePartners.remove(partnerKey);

        try {
            storage.removeFriend(player, partnerUUID);
        } catch (Exception e) {
            // Storage failure, but cache is already cleared
            return false;
        }

        return true;
    }

    /**
     * Gets the active partner of a player.
     *
     * @param player the player whose partner to find
     * @return the partner's UUID, or null if no partner
     */
    public UUID getPartner(UUID player) {
        String partnerKey = activePartners.get(player.toString());
        if (partnerKey == null) return null;

        try {
            return UUID.fromString(partnerKey);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Checks if a player has an active partner.
     *
     * @param player  the player to check
     * @param excludePlayer players to exclude (for checking bidirectional cases)
     * @return true if the player has a partner
     */
    public boolean hasPartner(UUID player, UUID... excludePlayers) {
        String partnerKey = activePartners.get(player.toString());
        if (partnerKey == null) return false;

        if (excludePlayers != null) {
            for (UUID exclude : excludePlayers) {
                if (Objects.equals(partnerKey, exclude.toString())) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Gets all active partnerships.
     *
     * @return map of player1 UUID to player2 UUID
     */
    public Map<UUID, UUID> getActivePartners() {
        Map<UUID, UUID> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : activePartners.entrySet()) {
            try {
                UUID player = UUID.fromString(entry.getKey());
                UUID partner = UUID.fromString(entry.getValue());
                // Only include the "primary" direction (smaller UUID first)
                if (player.compareTo(partner) < 0) {
                    result.put(player, partner);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }
}
