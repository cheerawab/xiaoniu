package co.partygame.friend;

import co.partygame.common.mysql.MySQLManager;
import co.partygame.common.mysql.tables.DatabaseTables;
import co.partygame.friend.command.FriendCommand;
import co.partygame.friend.config.FriendConfig;
import co.partygame.friend.gui.BlockListGUI;
import co.partygame.friend.gui.FriendListGUI;
import co.partygame.friend.gui.FriendRequestGUI;
import co.partygame.friend.partner.Partner;
import co.partygame.friend.storage.FriendStorage;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Friend System Plugin - Lobby side plugin for managing friendships, blocks, ignores, and friend requests.
 *
 * Provides features including:
 * <ul>
 *   <li>Add/remove/manage friends</li>
 *   <li>Block/ignore players</li>
 *   <li>Friend requests with accept/reject</li>
 *   <li>Chat message filtering for blocked/ignored players</li>
 *   <li>Party invites through matchmaking system</li>
 *   <li>Partner system for duo mode minigames</li>
 *   <li>Online status tracking and broadcasting</li>
 * </ul>
 */
public class FriendPlugin extends JavaPlugin {

    private static FriendPlugin instance;
    private final Logger logger = getLogger();

    private MySQLManager mysqlManager;
    private FriendConfig configManager;
    private FriendStorage friendStorage;
    private FriendManager friendManager;
    private FriendListGUI friendListGUI;
    private FriendRequestGUI friendRequestGUI;
    private BlockListGUI blockListGUI;
    private FriendCommand friendCommand;
    private co.partygame.friend.listener.FriendListener friendBukkitListener;
    private ChatMessageManager chatMessageManager;
    private Partner partnerSystem;

    private BukkitTask friendSaveTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        loadConfig();
        initMySQL();
        initStorage();
        initFriendManager();
        initGUIs();
        initChatManager();
        initPartnerSystem();
        registerListeners();
        registerCommands();
        startSaveTask();

        logger.info("Friend plugin enabled v" + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        logger.info("Friend plugin disabling...");

        if (friendSaveTask != null) {
            friendSaveTask.cancel();
        }

        // Save all friend data before shutting down
        for (Player player : Bukkit.getOnlinePlayers()) {
            saveAllPlayerData(player.getUniqueId());
        }

        if (mysqlManager != null) {
            mysqlManager.close();
        }

        instance = null;
        logger.info("Friend plugin disabled");
    }

    private void loadConfig() {
        configManager = new FriendConfig(this);
        configManager.load();
    }

    private void initMySQL() {
        String host = configManager.getString("database.host", "localhost");
        int port = configManager.getInt("database.port", 3306);
        String database = configManager.getString("database.database", "partygame");
        String username = configManager.getString("database.username", "root");
        String password = configManager.getString("database.password", "password");
        int maxPoolSize = configManager.getInt("database.pool.maximumPoolSize", 10);

        mysqlManager = new MySQLManager(host, port, database, username, password, "FriendPlugin");
        mysqlManager.initConnectionPool();
        logger.info("MySQL connected: " + host + ":" + port + "/" + database);
    }

    private void initStorage() {
        friendStorage = new FriendStorage(mysqlManager);
        DatabaseTables.initTables(mysqlManager);
        logger.info("Friend tables initialized");
    }

    private void initFriendManager() {
        friendManager = new FriendManager(friendStorage, configManager);
        friendManager.reloadConfig();

        // Pre-load friend data for online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            friendManager.getAllFriends(player.getUniqueId());
        }
    }

    private void initGUIs() {
        friendListGUI = new FriendListGUI(friendManager, configManager);
        friendRequestGUI = new FriendRequestGUI(friendManager, configManager);
        blockListGUI = new BlockListGUI(friendManager, configManager);
    }

    private void initChatManager() {
        chatMessageManager = new ChatMessageManager(friendManager, configManager);
    }

    private void initPartnerSystem() {
        partnerSystem = new Partner(friendStorage);
    }

    private void registerListeners() {
        friendBukkitListener = new co.partygame.friend.listener.FriendListener(this);
        Bukkit.getPluginManager().registerEvents(friendBukkitListener, this);

        // Chat filtering for blocks/ignores
        Bukkit.getPluginManager().registerEvents(chatMessageManager, this);
    }

    private void registerCommands() {
        friendCommand = new FriendCommand(this);
        getCommand("friend").setExecutor(friendCommand);
        getCommand("friend").setTabCompleter(friendCommand);
        logger.info("/friend command registered");
    }

    private void startSaveTask() {
        int saveInterval = configManager.getInt("autoSaveInterval", 300);
        friendSaveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();
                try {
                    friendManager.saveChanges(playerId);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to save friend data for " + player.getName(), e);
                }
            }
        }, saveInterval * 20L, saveInterval * 20L);
    }

    private void saveAllPlayerData(UUID playerId) {
        try {
            friendManager.saveChanges(playerId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to save friend data on shutdown", e);
        }
    }

    // ─── Getters ─────────────────────────────────────────────────

    public static FriendPlugin getInstance() {
        return instance;
    }

    public FriendConfig getFriendConfig() {
        return configManager;
    }

    public MySQLManager getMySQLManager() {
        return mysqlManager;
    }

    public FriendManager getFriendManager() {
        return friendManager;
    }

    public FriendListGUI getFriendListGUI() {
        return friendListGUI;
    }

    public FriendRequestGUI getFriendRequestGUI() {
        return friendRequestGUI;
    }

    public BlockListGUI getBlockListGUI() {
        return blockListGUI;
    }

    /**
     * Opens the friend requests GUI for the given player.
     *
     * @param player the player to open the GUI for
     */
    public void openFriendRequestsGUI(Player player) {
        friendRequestGUI.openGUI(player);
    }

    public FriendStorage getFriendStorage() {
        return friendStorage;
    }

    public Partner getPartnerSystem() {
        return partnerSystem;
    }

    public Set<UUID> getOnlineFriends(String playerId) {
        try {
            UUID id = UUID.fromString(playerId);
            return friendManager.getAllFriends(id);
        } catch (IllegalArgumentException e) {
            return Set.of();
        }
    }
}
