package co.partygame.matchmaking;

import co.partygame.common.McCommonPlugin;
import co.partygame.common.config.ConfigManager;
import co.partygame.common.redis.RedisManager;
import co.partygame.common.mysql.MySQLManager;
import co.partygame.common.auth.PermissionManager;
import co.partygame.common.protocol.packets.MatchRequest;
import co.partygame.common.protocol.packets.MatchAccepted;
import co.partygame.common.protocol.packets.GameNotification;
import co.partygame.common.util.BungeeMessenger;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.*;

public class MatchmakingPlugin extends JavaPlugin implements Listener {
    private static MatchmakingPlugin instance;
    private ConfigManager configManager;
    private RedisManager redisManager;
    private MySQLManager mysqlManager;
    private PermissionManager permissionManager;
    private BungeeMessenger bungeeMessenger;
    private ScheduledExecutorService scheduler;
    private ConcurrentMap<String, MatchQueue> queues = new ConcurrentHashMap<>();
    private ConcurrentMap<String, String> playerSessions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        McCommonPlugin common = (McCommonPlugin) Bukkit.getPluginManager().getPlugin("McCommon");
        if (common == null) {
            getLogger().severe("無法找到 McCommon 插件！禁用 Matchmaking...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        this.configManager = common.getConfigManager();
        this.redisManager = common.getRedisManager();
        this.mysqlManager = common.getMySQLManager();
        this.permissionManager = common.getPermissionManager();
        this.bungeeMessenger = new BungeeMessenger(this);
        
        saveDefaultConfig();
        
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new MatchGUIHandler(), this);
        
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        redisManager.connect();
        mysqlManager.connect();
        
        scheduler.scheduleAtFixedRate(this::healthCheck, 0, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::cleanExpiredQueues, 10, 10, TimeUnit.SECONDS);
        
        getLogger().info("Matchmaking 插件 v1.0.0 已啟用 - 配對系統就緒");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) scheduler.shutdown();
        if (redisManager != null) redisManager.disconnect();
        if (mysqlManager != null) mysqlManager.disconnect();
        queues.clear();
        playerSessions.clear();
        getLogger().info("Matchmaking 插件 v1.0.0 已停用");
    }

    public static MatchmakingPlugin getInstance() {
        return instance;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getLogger().info(player.getName() + " 加入伺服器");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String session = playerSessions.remove(player.getUniqueId().toString());
        if (session != null) {
            getLogger().info(player.getName() + " 的配對會話已取消: " + session);
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("此命令僅供玩家使用");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (cmd.getName().equalsIgnoreCase("match")) {
            if (args.length == 0) {
                player.sendMessage("§6使用方式:");
                player.sendMessage("  §a/match <survival|obby|zombie|...> [選項]");
                player.sendMessage("  §a/match queue - 查看隊列狀態");
                player.sendMessage("  §a/match cancel - 取消配對");
                player.sendMessage("  §a/match gui - 打開 GUI");
                return true;
            }
            
            String subCmd = args[0].toLowerCase();
            
            switch (subCmd) {
                case "queue":
                    showQueueStatus(player);
                    break;
                case "cancel":
                    cancelMatch(player);
                    break;
                case "gui":
                    openMatchGUI(player);
                    break;
                default:
                    startMatch(player, subCmd, args.length > 1 ? args[1] : null);
                    break;
            }
        }
        
        return true;
    }

    private void startMatch(Player player, String gameType, String extraOptions) {
        if (!permissionManager.hasPermission(player, "partygame.match.join")) {
            player.sendMessage("§c你沒有權限進行配對");
            return;
        }
        
        String gamePerm = "partygame.match.game." + gameType;
        String allGamesPerm = "partygame.match.game.*";
        
        if (!permissionManager.hasPermission(player, gamePerm) 
            && !permissionManager.hasPermission(player, allGamesPerm)) {
            player.sendMessage("§c你沒有權限玩 " + gameType + " 遊戲");
            return;
        }
        
        String sessionId = java.util.UUID.randomUUID().toString();
        playerSessions.put(player.getUniqueId().toString(), sessionId);
        
        MatchRequest request = new MatchRequest(
            sessionId,
            gameType,
            new UUID[]{player.getUniqueId()},
            null,
            null,
            "lobby1"
        );
        
        redisManager.publish(request.toJson());
        
        player.sendMessage("§a正在加入配對隊列... §7(會話: " + sessionId.substring(0, 8) + ")");
        getLogger().info(player.getName() + " 加入匹配隊列: " + gameType);
    }

    private void showQueueStatus(Player player) {
        int totalQueued = queues.values().stream()
            .mapToInt(MatchQueue::getSize)
            .sum();
        player.sendMessage("§6=== 配對隊列狀態 ===");
        player.sendMessage("§a線上隊列總數: §f" + totalQueued);
        for (var entry : queues.entrySet()) {
            MatchQueue queue = entry.getValue();
            player.sendMessage("  §7" + entry.getKey() + ": §f" + queue.getSize() + " 人");
        }
    }

    private void cancelMatch(Player player) {
        String session = playerSessions.remove(player.getUniqueId().toString());
        if (session != null) {
            player.sendMessage("§c已取消配對");
            getLogger().info(player.getName() + " 取消配對");
        } else {
            player.sendMessage("§c你當前沒有在配對隊列中");
        }
    }

    private void openMatchGUI(Player player) {
        if (!permissionManager.hasPermission(player, "partygame.gui.match")) {
            player.sendMessage("§c你沒有權限使用配對 GUI");
            return;
        }
        player.sendMessage("§a打開配對 GUI... (實現 GUI 界面)");
    }

    private void healthCheck() {
        try {
            if (redisManager.isConnected()) {
                try (redis.clients.jedis.Jedis jedis = redisManager.getPool().getResource()) {
                    String pong = jedis.ping();
                    getLogger().info("Backend 健康檢查: 正常");
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "健康檢查失敗", e);
        }
    }

    private void cleanExpiredQueues() {
        queues.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                getLogger().info("過期隊列已清理: " + entry.getKey());
                return true;
            }
            return false;
        });
    }
}
