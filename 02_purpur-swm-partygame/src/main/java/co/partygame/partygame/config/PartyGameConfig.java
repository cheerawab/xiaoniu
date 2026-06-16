package co.partygame.partygame.config;

import co.partygame.partygame.PartyGamePlugin;

import java.util.*;

/**
 * Configuration accessor for the PartyGame Framework.
 * <p>
 * Loads and caches settings from config.yml so they can be accessed
 * type-safely throughout the framework without repeatedly reading from disk.
 */
public class PartyGameConfig {

    private final PartyGamePlugin plugin;

    // General game settings
    private int maxConcurrentSessions;
    private int maxConcurrentGameWorlds;
    private int minPlayersPerGame;
    private int maxPlayersPerGame;

    // Round settings
    private long roundDurationSeconds;
    private List<Integer> countdownWarns;

    // Scoring settings
    private int winPoints;
    private Map<Integer, Integer> placementPoints;

    // BungeeCord settings
    private boolean bungeeCordEnabled;
    private String bungeeChannel;
    private long matchResponseTimeoutMillis;

    // Logging settings
    private boolean loggingEnabled;
    private boolean logSessionEvents;
    private boolean logRoundEvents;
    private boolean logMatchmaking;

    /**
     * Create a new config accessor backed by the given plugin.
     */
    public PartyGameConfig(PartyGamePlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    /**
     * Load all configuration values from the plugin config.yml.
     */
    public void load() {
        var cfg = plugin.getConfig();

        // General game settings
        maxConcurrentSessions = cfg.getInt("game.max-concurrent-sessions", 10);
        maxConcurrentGameWorlds = cfg.getInt("worlds.max-concurrent-game-worlds", 20);
        minPlayersPerGame = cfg.getInt("worlds.min-players-per-game", 1);
        maxPlayersPerGame = cfg.getInt("worlds.max-players-per-game", 32);

        // Round settings
        roundDurationSeconds = cfg.getLong("game.round.duration", 600);
        countdownWarns = cfg.getIntegerList("game.round.countdown-warns");
        if (countdownWarns.isEmpty()) {
            countdownWarns = List.of(60, 30, 10, 5, 3, 1);
        }

        // Scoring settings
        winPoints = cfg.getInt("game.scoring.win-points", 100);
        placementPoints = new LinkedHashMap<>();
        var defaultPlacement = Map.of(1, 100, 2, 75, 3, 50, 4, 25);

        for (int i = 1; i <= 20; i++) {
            if (cfg.contains("game.scoring.placement-points." + i)) {
                placementPoints.put(i, cfg.getInt("game.scoring.placement-points." + i));
            } else {
                placementPoints.put(i, defaultPlacement.getOrDefault(i, 0));
            }
        }

        // BungeeCord settings
        bungeeCordEnabled = cfg.getBoolean("bungeecord.enabled", true);
        bungeeChannel = cfg.getString("bungeecord.channel", "partygame:matchmaking");
        matchResponseTimeoutMillis = cfg.getLong("bungeecord.match-response-timeout", 5000);

        // Logging settings
        loggingEnabled = cfg.getBoolean("logging.enabled", true);
        logSessionEvents = cfg.getBoolean("logging.log-session-events", true);
        logRoundEvents = cfg.getBoolean("logging.log-round-events", true);
        logMatchmaking = cfg.getBoolean("logging.log-matchmaking", true);

        plugin.getLogger().info("PartyGame Config loaded.");
    }

    /**
     * Reload config from disk.
     */
    public void reload() {
        plugin.reloadConfig();
        load();
    }

    // ============================================================
    // Accessors
    // ============================================================

    public int getMaxConcurrentSessions() { return maxConcurrentSessions; }
    public int getMaxConcurrentGameWorlds() { return maxConcurrentGameWorlds; }
    public int getMinPlayersPerGame() { return minPlayersPerGame; }
    public int getMaxPlayersPerGame() { return maxPlayersPerGame; }
    public long getRoundDurationSeconds() { return roundDurationSeconds; }
    public List<Integer> getCountdownWarns() { return countdownWarns; }
    public int getWinPoints() { return winPoints; }
    public Map<Integer, Integer> getPlacementPoints() { return placementPoints; }
    public boolean isBungeeCordEnabled() { return bungeeCordEnabled; }
    public String getBungeeChannel() { return bungeeChannel; }
    public long getMatchResponseTimeoutMillis() { return matchResponseTimeoutMillis; }
    public boolean isLoggingEnabled() { return loggingEnabled; }
    public boolean isLogSessionEvents() { return logSessionEvents; }
    public boolean isLogRoundEvents() { return logRoundEvents; }
    public boolean isLogMatchmaking() { return logMatchmaking; }
}
