package co.partygame.matchmaking;

import co.partygame.common.auth.PermissionManager;
import co.partygame.common.config.ConfigManager;
import co.partygame.common.mysql.MySQLManager;
import co.partygame.common.protocol.packets.backend.MatchAccepted;
import co.partygame.common.redis.RedisManager;
import co.partygame.common.redis.RedisPubSub;
import co.partygame.common.util.BungeeMessenger;
import co.partygame.common.util.ChatUtils;
import co.partygame.matchmaking.auth.LobbyPermissionChecker;
import co.partygame.matchmaking.backend.BackendHealth;
import co.partygame.matchmaking.backend.BackendManager;
import co.partygame.matchmaking.backend.BackendSelector;
import co.partygame.matchmaking.gui.CustomOptionsGUI;
import co.partygame.matchmaking.gui.MatchGUI;
import co.partygame.matchmaking.gui.StatsGUI;
import co.partygame.matchmaking.gui.WaitingGUI;
import co.partygame.matchmaking.party.CustomRoomCreator;
import co.partygame.matchmaking.party.PartyMatchQueue;
import co.partygame.matchmaking.party.PartyMatchValidator;
import co.partygame.matchmaking.storage.MatchRecord;
import co.partygame.matchmaking.storage.QueueStats;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Matchmaking plugin main class.
 * Orchestrates matchmaking queues, backend routing, GUI interaction, and permission checks
 * on the lobby side of a PartyGame server cluster.
 */
public class MatchmakingPlugin extends JavaPlugin {

    private static MatchmakingPlugin instance;
    private final Logger logger = getLogger();

    private RedisManager redisManager;
    private MySQLManager mysqlManager;
    private PermissionManager permissionManager;
    private BungeeMessenger bungeeMessenger;

    private MatchQueue matchQueue;
    private BackendManager backendManager;
    private LobbyPermissionChecker permissionChecker;
    private GamePayloadBuilder payloadBuilder;
    private MatchRouter matchRouter;
    private MatchGUI matchGUI;
    private WaitingGUI waitingGUI;
    private StatsGUI statsGUI;
    private PartyMatchValidator partyMatchValidator;
    private PartyMatchQueue partyMatchQueue;
    private CustomRoomCreator customRoomCreator;
    private MatchStrategy matchStrategy;
    private PlayerState playerStateManager;
    private MatchRecord matchRecord;
    private QueueStats queueStats;
    private CustomOptionsGUI customOptionsGUI;
    private BackendHealth backendHealth;
    private BackendSelector backendSelector;

    private BukkitTask healthCheckTask;
    private BukkitTask queueProcessTask;
    private BukkitTask waitingGuiRefreshTask;

    private final RedisPubSub pubSubHandler = new MatchPubSubHandler();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        loadDependencies();
        initCoreComponents();
        registerListeners();
        registerCommands();
        registerBungeeChannels();
        startScheduledTasks();
        registerPermissions();

        logger.info("Matchmaking plugin enabled v" + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        logger.info("Matchmaking plugin disabling...");

        if (healthCheckTask != null) healthCheckTask.cancel();
        if (queueProcessTask != null) queueProcessTask.cancel();
        if (waitingGuiRefreshTask != null) waitingGuiRefreshTask.cancel();

        if (redisManager != null) {
            redisManager.unsubscribe(pubSubHandler);
            redisManager.close();
        }

        if (mysqlManager != null) {
            mysqlManager.close();
        }

        instance = null;
        logger.info("Matchmaking plugin disabled");
    }

    private void loadDependencies() {
        config().load();

        String redisHost = config().getString("redis.host", "localhost");
        int redisPort = config().getInt("redis.port", 6379);
        String redisPass = config().getString("redis.password", "");
        int redisDb = config().getInt("redis.database", 0);
        int redisMaxTotal = config().getInt("redis.pool.maxTotal", 20);
        int redisMaxIdle = config().getInt("redis.pool.maxIdle", 10);

        redisManager = new RedisManager(redisHost, redisPort, redisPass, redisDb,
                redisMaxTotal, redisMaxIdle, 5, 2000, 2000);
        redisManager.init();

        String dbHost = config().getString("database.host", "localhost");
        int dbPort = config().getInt("database.port", 3306);
        String dbName = config().getString("database.database", "partygame");
        String dbUser = config().getString("database.username", "root");
        String dbPass = config().getString("database.password", "password");
        int maxPoolSize = config().getInt("database.pool.maximumPoolSize", 10);

        mysqlManager = new MySQLManager(dbHost, dbPort, dbName, dbUser, dbPass, maxPoolSize);
        mysqlManager.init();
    }

    private void initCoreComponents() {
        bungeeMessenger = new BungeeMessenger(this);
        permissionManager = new PermissionManager(this);

        String redisChannel = config().getString("redis.channel", "partygame:matchmaking");
        redisManager.subscribe(pubSubHandler, redisChannel);

        backendManager = new BackendManager(redisManager);
        backendManager.loadBackendList();

        matchStrategy = new MatchStrategy();
        matchQueue = new MatchQueue(redisManager, matchStrategy, config());

        playerStateManager = new PlayerState();
        matchRecord = new MatchRecord(mysqlManager);
        queueStats = new QueueStats(redisManager);

        backendHealth = new BackendHealth(redisManager, config());

        BackendSelector.Strategy strategy = BackendSelector.Strategy.LEAST_PLAYERS;
        try {
            strategy = BackendSelector.Strategy.valueOf(
                    config().getString("backend.selection_strategy", "least_players").toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid backend selection strategy, using default: least_players");
        }

        backendSelector = new BackendSelector(backendManager, strategy);
        backendSelector.setStrategy(strategy);

        payloadBuilder = new GamePayloadBuilder();
        matchRouter = new MatchRouter(bungeeMessenger, backendManager, backendSelector, this);

        permissionChecker = new LobbyPermissionChecker(permissionManager);
        matchGUI = new MatchGUI(this, matchQueue, waitingGUI, permissionChecker, matchStrategy, config());
        waitingGUI = new WaitingGUI(this, matchQueue, matchRouter);
        statsGUI = new StatsGUI(this, matchRecord);
        customOptionsGUI = new CustomOptionsGUI(this, matchQueue);

        partyMatchValidator = new PartyMatchValidator(permissionManager);
        partyMatchQueue = new PartyMatchQueue(redisManager);

        customRoomCreator = new CustomRoomCreator(permissionManager, matchRouter);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new MatchClickHandler(this), this);
        Bukkit.getPluginManager().registerEvents(new MatchQueueListener(this), this);
    }

    private void registerCommands() {
        getCommand("match").setExecutor(new MatchCommandExecutor(this));
        getCommand("match").setTabCompleter(new MatchTabCompleter(this));
    }

    private void registerBungeeChannels() {
        bungeeMessenger.registerListener(this);
    }

    private void startScheduledTasks() {
        int healthInterval = config().getInt("backend.health_check_interval", 30) * 20;

        healthCheckTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            backendHealth.startHealthChecks();
        }, healthInterval, healthInterval);

        queueProcessTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            matchQueue.processQueues();
        }, 200L, 200L);

        int refreshInterval = config().getInt("gui.refresh_interval", 10);
        waitingGuiRefreshTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            waitingGUI.refreshAllPlayers();
        }, refreshInterval * 20L, refreshInterval * 20L);
    }

    private void registerPermissions() {
        String[] perms = {
                "partygame.match.*",
                "partygame.match.use",
                "partygame.match.join",
                "partygame.match.cancel",
                "partygame.match.queue",
                "partygame.match.stats",
                "partygame.match.custom_room",
                "partygame.match.custom_room_join",
                "partygame.match.custom_room.create",
                "partygame.match.admin",
                "partygame.game.*"
        };

        for (String perm : perms) {
            getServer().getPluginManager().createPermission(perm, org.bukkit.plugin.Permission.DEFAULT);
        }
    }

    public static MatchmakingPlugin getInstance() {
        return instance;
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public MySQLManager getMySQLManager() {
        return mysqlManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public BungeeMessenger getBungeeMessenger() {
        return bungeeMessenger;
    }

    public MatchQueue getMatchQueue() {
        return matchQueue;
    }

    public BackendManager getBackendManager() {
        return backendManager;
    }

    public LobbyPermissionChecker getPermissionChecker() {
        return permissionChecker;
    }

    public GamePayloadBuilder getPayloadBuilder() {
        return payloadBuilder;
    }

    public MatchRouter getMatchRouter() {
        return matchRouter;
    }

    public MatchGUI getMatchGUI() {
        return matchGUI;
    }

    public WaitingGUI getWaitingGUI() {
        return waitingGUI;
    }

    public StatsGUI getStatsGUI() {
        return statsGUI;
    }

    public CustomOptionsGUI getCustomOptionsGUI() {
        return customOptionsGUI;
    }

    public PartyMatchValidator getPartyMatchValidator() {
        return partyMatchValidator;
    }

    public PartyMatchQueue getPartyMatchQueue() {
        return partyMatchQueue;
    }

    public CustomRoomCreator getCustomRoomCreator() {
        return customRoomCreator;
    }

    public MatchStrategy getMatchStrategy() {
        return matchStrategy;
    }

    public PlayerState getPlayerStateManager() {
        return playerStateManager;
    }

    public MatchRecord getMatchRecord() {
        return matchRecord;
    }

    public QueueStats getQueueStats() {
        return queueStats;
    }

    public BackendHealth getBackendHealth() {
        return backendHealth;
    }

    public BackendSelector getBackendSelector() {
        return backendSelector;
    }

    public ConfigManager config() {
        return ConfigManager.getInstance();
    }

    public void handleMatchAccepted(MatchAccepted accepted) {
        if (matchRouter != null) {
            matchRouter.handleMatchAccepted(accepted);
        }
    }

    private class MatchPubSubHandler extends RedisPubSub {
        @Override
        public void onMessage(String channel, String message) {
            try {
                logger.fine("Received pub/sub message on channel: " + channel + " -> " + message);

                if (channel.equals("partygame:matchmaking") || channel.contains("partygame:")) {
                    if (message.startsWith("BACKEND_REGISTERED:")) {
                        String backendName = message.substring("BACKEND_REGISTERED:".length());
                        backendManager.registerBackend(backendName);
                    } else if (message.startsWith("BACKEND_UNREGISTERED:")) {
                        String backendName = message.substring("BACKEND_UNREGISTERED:".length());
                        backendManager.unregisterBackend(backendName);
                    } else if (message.startsWith("BACKEND_HEALTH:")) {
                        String[] parts = message.substring("BACKEND_HEALTH:".length()).split(":");
                        if (parts.length >= 2) {
                            backendManager.pingBackend(parts[0], Boolean.parseBoolean(parts[1]));
                        }
                    } else if (message.startsWith("BACKEND_STATS:")) {
                        String[] parts = message.substring("BACKEND_STATS:".length()).split(":");
                        if (parts.length >= 3) {
                            backendManager.updateBackendStats(parts[0],
                                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                        }
                    }

                    MatchAccepted accepted = payloadBuilder.parseMatchAccepted(message);
                    if (accepted != null) {
                        handleMatchAccepted(accepted);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error handling pub/sub message", e);
            }
        }

        @Override
        public void onSubscribe(String channel, int subscribedChannels) {
            logger.info("Subscribed to Redis channel: " + channel);
        }

        @Override
        public void onUnsubscribe(String channel, int subscribedChannels) {
            logger.info("Unsubscribed from Redis channel: " + channel);
        }
    }
}
