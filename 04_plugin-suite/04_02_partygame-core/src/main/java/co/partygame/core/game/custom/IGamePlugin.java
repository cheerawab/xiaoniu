package co.partygame.core.game.custom;

import co.partygame.common.protocol.packets.backend.GameResult;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core interface for all game plugins.
 *
 * Implement this interface to create a new game type that can be
 * dynamically loaded by PartyGameCore's game registry. Game plugins
 * manage the full lifecycle of a game session including start,
 * rounds, end scoring, and cancellation.
 *
 * <pre>{@code
 * public class SurvivalGamePlugin implements IGamePlugin {
 *     @Override
 *     public String getId() { return "survival"; }
 *     // ... implement other methods
 * }
 * }</pre>
 *
 * <p>All lifecycle methods receive a list of {@link Player} objects directly,
 * allowing game plugins to interact with Bukkit APIs without needing
 * additional lookups.</p>
 */
public interface IGamePlugin {

    Logger LOGGER = Logger.getLogger(IGamePlugin.class.getName());

    // ─── Identity ──────────────────────────────────────────────────

    /**
     * Returns the unique identifier for this game type.
     *
     * @return unique game identifier (e.g., "survival", "king-of-hill")
     */
    String getId();

    /**
     * Returns the human-readable display name for this game type.
     *
     * @return display name (e.g., "Survival Game")
     */
    String getName();

    /**
     * Returns the minimum number of players required to start this game.
     *
     * @return minimum player count
     */
    int getMinPlayers();

    /**
     * Returns the maximum number of players this game supports.
     *
     * @return maximum player count
     */
    int getMaxPlayers();

    /**
     * Returns the name prefix of the default .slimeworld file used for this game.
     *
     * @return world template filename prefix (e.g., "survival_template")
     */
    String getDefaultWorldTemplate();

    /**
     * Returns the default custom options for this game type.
     * These defaults are applied when no user options are provided.
     *
     * @return map of option key to default value
     */
    Map<String, Object> getDefaults();

    // ─── Validation ────────────────────────────────────────────────

    /**
     * Validates custom options provided by the lobby before game start.
     *
     * @param options the custom options to validate
     * @throws IllegalArgumentException if any option is invalid
     */
    default void validateOptions(Map<String, Object> options) {
        if (options == null) {
            throw new IllegalArgumentException("Custom options must not be null");
        }
    }

    // ─── Lifecycle Callbacks ───────────────────────────────────────

    /**
     * Called when a game session starts (players ready, world created).
     * Game plugins should use this to teleport players to spawn points,
     * give starting items, and prepare the game world.
     *
     * @param players              the list of players in this session
     * @param customOptions        the configured custom options for this game
     */
    void onGameStart(List<Player> players, Map<String, Object> customOptions);

    /**
     * Called at the beginning of each game round.
     *
     * @param players     the list of active players
     * @param round       the current round number (1-based)
     * @param customOptions the configured custom options for this game
     */
    default void onRoundStart(List<Player> players, int round, Map<String, Object> customOptions) {
        LOGGER.fine(() -> getId() + " round " + round + " started with " + players.size() + " players");
    }

    /**
     * Called at the end of each game round.
     *
     * @param players     the list of active players
     * @param round       the round that just ended (1-based)
     * @param customOptions the configured custom options for this game
     */
    default void onRoundEnd(List<Player> players, int round, Map<String, Object> customOptions) {
        LOGGER.fine(() -> getId() + " round " + round + " ended");
    }

    /**
     * Called when a game session has ended (all rounds completed or forced end).
     * Implementations should record final results and perform cleanup.
     *
     * @param players              the list of players in this session
     * @param customOptions        the configured custom options for this game
     */
    void onGameEnd(List<Player> players, Map<String, Object> customOptions);

    /**
     * Calculates scores for all players in a session.
     *
     * @param players     the list of game players
     * @param customOptions the configured custom options for this game
     * @return list of game results (one per player)
     */
    List<GameResult> calculateResults(List<Player> players, Map<String, Object> customOptions);

    /**
     * Called when a game session is cancelled (before or during play).
     * Implementations should clean up world state and restore players.
     *
     * @param players the list of players to handle
     * @param customOptions the configured custom options for this game
     */
    default void onCancel(List<Player> players, Map<String, Object> customOptions) {
        LOGGER.fine(() -> getId() + " game cancelled with " + players.size() + " players");
    }
}
