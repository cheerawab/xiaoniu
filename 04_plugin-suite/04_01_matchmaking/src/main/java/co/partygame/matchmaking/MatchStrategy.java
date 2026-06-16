package co.partygame.matchmaking;

import co.partygame.common.auth.PermissionManager;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Defines matchmaking strategies for selecting and grouping players.
 * Supports FAST_MATCH, RANK_MATCH (by rank), and PARTY_FIRST approaches.
 */
public class MatchStrategy {

    /**
     * Checks if a list of entries can be matched together.
     * Validates cohort size and game type compatibility.
     *
     * @param entries the list of queue entries to check
     * @return true if these players can form a match group
     */
    public boolean canMatch(List<MatchQueue.QueueEntry> entries) {
        if (entries == null || entries.isEmpty()) return false;
        if (entries.size() < 1) return false;

        String gameType = entries.get(0).gameType;
        for (MatchQueue.QueueEntry entry : entries) {
            if (entry.hasParty()) {
                return canPlayTogether(entry);
            }
        }
        return true;
    }

    /**
     * Checks if a cohort of entries can form a valid match group.
     *
     * @param cohort the list of entries to evaluate
     * @return true if the cohort is valid for matching
     */
    public boolean canMatchCohort(List<MatchQueue.QueueEntry> cohort) {
        if (cohort == null || cohort.isEmpty()) return false;

        Set<String> gameTypes = new HashSet<>();
        for (MatchQueue.QueueEntry entry : cohort) {
            gameTypes.add(entry.gameType);
            if (gameTypes.size() > 1) return false;
        }

        Set<String> gameIds = new HashSet<>();
        if (cohort.size() > 1) {
            for (MatchQueue.QueueEntry entry : cohort) {
                gameIds.add(entry.gameId);
                if (gameIds.size() > 1) return false;
            }
        }

        return true;
    }

    /**
     * Groups queue entries by their game type.
     *
     * @param entries the list of all queue entries
     * @return map of gameType to list of entries
     */
    public Map<String, List<MatchQueue.QueueEntry>> groupByGameType(List<MatchQueue.QueueEntry> entries) {
        Map<String, List<MatchQueue.QueueEntry>> groups = new LinkedHashMap<>();
        if (entries == null || entries.isEmpty()) return groups;

        for (MatchQueue.QueueEntry entry : entries) {
            groups.computeIfAbsent(entry.gameType, k -> new ArrayList<>()).add(entry);
        }
        return groups;
    }

    /**
     * Selects players for a game session with party priority.
     * Parties are kept together, and solo players fill remaining slots.
     *
     * @param entries  the list of entries to select from
     * @param needed   the number of players needed
     * @param gameId   the game identifier
     * @return list of selected queue entries
     */
    public List<MatchQueue.QueueEntry> selectPlayersForGame(List<MatchQueue.QueueEntry> entries,
                                                            int needed, String gameId) {
        if (entries == null || entries.isEmpty() || needed <= 0) return Collections.emptyList();

        List<MatchQueue.QueueEntry> selected = new ArrayList<>();
        Set<UUID> selectedUuids = new HashSet<>();

        for (MatchQueue.QueueEntry entry : entries) {
            if (selected.size() >= needed) break;
            if (selectedUuids.contains(entry.uuid)) continue;

            if (entry.hasParty()) {
                UUID partyId = entry.getPartyId();
                if (partyId != null) {
                    for (MatchQueue.QueueEntry candidate : entries) {
                        if (selectedUuids.contains(candidate.uuid) || selected.size() >= needed) break;
                        if (candidate.getPartyId() != null
                                && candidate.getPartyId().equals(partyId)) {
                            if (!selectedUuids.contains(candidate.uuid)) {
                                selected.add(candidate);
                                selectedUuids.add(candidate.uuid);
                            }
                        }
                    }
                    continue;
                }
            }

            selected.add(entry);
            selectedUuids.add(entry.uuid);
        }

        return selected;
    }

    /**
     * Checks if all players have compatible permissions for a game.
     *
     * @param players the list of players
     * @param gameId  the game identifier
     * @return true if all players have the necessary permissions
     */
    public boolean hasCompatiblePermissions(List<Player> players, String gameId) {
        if (players == null || players.isEmpty()) return false;

        PermissionManager permManager = co.partygame.matchmaking.MatchmakingPlugin
                .getInstance().getPermissionManager();

        for (Player player : players) {
            if (!permManager.hasPermission(player, "partygame.match.join")
                    || !permManager.hasPermission(player, "partygame.game." + gameId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds match groups that can be sent together to backends.
     *
     * @param groups the map of gameType to entries
     * @return list of group IDs that form valid matches
     */
    public List<String> buildMatchGroups(Map<String, List<MatchQueue.QueueEntry>> groups) {
        List<String> matchGroupIds = new ArrayList<>();

        for (Map.Entry<String, List<MatchQueue.QueueEntry>> group : groups.entrySet()) {
            List<MatchQueue.QueueEntry> entries = group.getValue();
            for (int i = 0; i < entries.size(); ) {
                int maxSlots = 8;
                int windowEnd = Math.min(i + maxSlots, entries.size());
                List<MatchQueue.QueueEntry> window = entries.subList(i, windowEnd);

                if (canMatchCohort(window)) {
                    String groupId = UUID.randomUUID().toString();
                    matchGroupIds.add(groupId);
                }

                i += window.size();
            }
        }

        return matchGroupIds;
    }

    /**
     * Checks if a single queue entry can play (non-party players always can).
     */
    private boolean canPlayTogether(MatchQueue.QueueEntry entry) {
        return entry.hasParty() || entry.customOptions.isEmpty();
    }
}
