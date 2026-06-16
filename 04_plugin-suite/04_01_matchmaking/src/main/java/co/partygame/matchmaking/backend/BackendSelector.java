package co.partygame.matchmaking.backend;

import co.partygame.matchmaking.backend.BackendManager.BackendInfo;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Selects which backend to route players to.
 * 
 * Strategies:
 *   LEAST_PLAYERS - default, picks backend with fewest currentPlayers
 *   ROUND_ROBIN   - distributes evenly across backends
 *   RANK_AWARE    - for rank-gated rooms (future enhancement)
 */
public class BackendSelector {

    private static final Logger LOGGER = Logger.getLogger(BackendSelector.class.getName());

    private BackendManager backendManager;
    private Strategy strategy;
    private final AtomicLong roundRobinIndex = new AtomicLong(0);

    /**
     * Selection strategies.
     */
    public enum Strategy {
        LEAST_PLAYERS,
        ROUND_ROBIN,
        RANK_AWARE
    }

    /**
     * Creates a BackendSelector with a default strategy.
     *
     * @param backendManager the backend manager
     * @param strategy       the selection strategy
     */
    public BackendSelector(BackendManager backendManager, Strategy strategy) {
        this.backendManager = Objects.requireNonNull(backendManager);
        this.strategy = strategy;
    }

    /**
     * Selects the best backend from the available list.
     *
     * @param backends the available backend info array
     * @return the selected backend server name, or null if none available
     */
    public String select(BackendInfo... backends) {
        if (backends == null || backends.length == 0) return null;
        String selected = null;

        switch (strategy) {
            case LEAST_PLAYERS:
                selected = selectLeastPlayers(backends);
                break;
            case ROUND_ROBIN:
                selected = selectRoundRobin(backends);
                break;
            case RANK_AWARE:
                selected = selectRankAware(backends);
                break;
            default:
                selected = selectLeastPlayers(backends);
        }
        return selected;
    }

    /**
     * Sets the selection strategy at runtime.
     *
     * @param strategy the new strategy
     */
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Gets the best backend that supports a given game type.
     * Falls back to LEAST_PLAYERS if no specific backend found.
     *
     * @param gameType the game type
     * @return the best backend name, or null
     */
    public String getBackend(String gameType) {
        List<BackendInfo> backends = backendManager.getOnlineBackends();
        if (backends.isEmpty()) return null;
        return select(backends.toArray(new BackendInfo[0]));
    }

    /**
     * Picks the backend with the fewest current players.
     *
     * @param backends the available backends
     * @return the selected backend name, or null
     */
    private String selectLeastPlayers(BackendInfo[] backends) {
        String selected = null;
        int minPlayers = Integer.MAX_VALUE;

        for (BackendInfo b : backends) {
            if (b.isOnline() && b.getCurrentPlayers() < minPlayers) {
                minPlayers = b.getCurrentPlayers();
                selected = b.getName();
            }
        }
        return selected;
    }

    /**
     * Distributes evenly across backends using round robin.
     *
     * @param backends the available backends
     * @return the selected backend name, or null
     */
    private String selectRoundRobin(BackendInfo[] backends) {
        List<BackendInfo> online = new ArrayList<>();
        for (BackendInfo b : backends) {
            if (b.isOnline()) {
                online.add(b);
            }
        }
        if (online.isEmpty()) return null;
        long idx = roundRobinIndex.incrementAndGet() % online.size();
        return online.get((int) idx).getName();
    }

    /**
     * For rank-gated rooms (future enhancement).
     * Selects based on backend capacity and rank.
     *
     * @param backends the available backends
     * @return the selected backend name, or null
     */
    @Deprecated
    private String selectRankAware(BackendInfo[] backends) {
        String selected = null;
        int maxCapacity = -1;

        for (BackendInfo b : backends) {
            if (b.isOnline() && b.getCapacity() > maxCapacity) {
                maxCapacity = b.getCapacity();
                selected = b.getName();
            }
        }
        return selected;
    }
}
