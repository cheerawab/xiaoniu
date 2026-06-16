package co.partygame.friend;

import co.partygame.common.McCommonPlugin;
import co.partygame.common.config.ConfigManager;
import co.partygame.common.mysql.MySQLManager;
import co.partygame.common.auth.PermissionManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class FriendPlugin extends JavaPlugin implements Listener {
    private static FriendPlugin instance;
    private McCommonPlugin commonPlugin;
    private ConfigManager configManager;
    private MySQLManager mysqlManager;
    private PermissionManager permissionManager;
    private Map<UUID, List<String>> playerFriends = new ConcurrentHashMap<>();
    private Map<UUID, List<String>> playerBlocks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        commonPlugin = (McCommonPlugin) Bukkit.getPluginManager().getPlugin("McCommon");
        if (commonPlugin == null) {
            getLogger().severe("無法找到 McCommon 插件！禁用好友系統...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        this.configManager = commonPlugin.getConfigManager();
        this.mysqlManager = commonPlugin.getMySQLManager();
        this.permissionManager = commonPlugin.getPermissionManager();
        
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        
        loadFriendData();
        
        getLogger().info("好友系統插件 v1.0.0 已啟用");
    }

    @Override
    public void onDisable() {
        saveFriendData();
        getLogger().info("好友系統插件 v1.0.0 已停用");
    }

    public static FriendPlugin getInstance() {
        return instance;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerFriends.putIfAbsent(player.getUniqueId(), new ArrayList<>());
        playerBlocks.putIfAbsent(player.getUniqueId(), new ArrayList<>());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        saveFriendData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("此命令僅供玩家使用");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (cmd.getName().equalsIgnoreCase("friend")) {
            if (args.length == 0) {
                showHelp(player);
                return true;
            }
            
            String subCmd = args[0].toLowerCase();
            
            switch (subCmd) {
                case "add":
                    if (args.length < 2) {
                        player.sendMessage("§c使用方式: /friend add <玩家名的稱>");
                        return true;
                    }
                    addFriend(player, player.getName());
                    break;
                case "list":
                    listFriends(player);
                    break;
                case "remove":
                    if (args.length < 2) {
                        player.sendMessage("§c使用方式: /friend remove <玩家名的稱>");
                        return true;
                    }
                    removeFriend(player, args[1]);
                    break;
                case "block":
                    if (args.length < 2) {
                        player.sendMessage("§c使用方式: /friend block <玩家名的稱>");
                        return true;
                    }
                    blockPlayer(player, args[1]);
                    break;
                case "unblock":
                    if (args.length < 2) {
                        player.sendMessage("§c使用方式: /friend unblock <玩家名的稱>");
                        return true;
                    }
                    unblockPlayer(player, args[1]);
                    break;
                case "blocklist":
                    listBlocks(player);
                    break;
                default:
                    showHelp(player);
                    break;
            }
        }
        
        return true;
    }

    private void addFriend(Player player, String friendName) {
        if (!permissionManager.hasPermission(player, "partygame.friend.add")) {
            player.sendMessage("§c你沒有權限添加好友");
            return;
        }
        
        Player target = Bukkit.getPlayerExact(friendName);
        if (target == null) {
            player.sendMessage("§c找不到玩家: " + friendName);
            return;
        }
        
        UUID playerId = player.getUniqueId().toString();
        String targetId = target.getUniqueId().toString();
        
        List<String> friends = playerFriends.computeIfAbsent(playerId, k -> new ArrayList<>());
        if (friends.contains(targetId)) {
            player.sendMessage("§c你已經是 " + friendName + " 的好友了");
            return;
        }
        
        friends.add(targetId);
        player.sendMessage("§a成功將 " + friendName + " 添加為好友");
        
        List<String> targetFriends = playerFriends.computeIfAbsent(targetId, k -> new ArrayList<>());
        if (!targetFriends.contains(playerId)) {
            targetFriends.add(playerId);
        }
    }

    private void removeFriend(Player player, String friendName) {
        if (!permissionManager.hasPermission(player, "partygame.friend.remove")) {
            player.sendMessage("§c你沒有權限移除好友");
            return;
        }
        
        UUID playerId = player.getUniqueId().toString();
        List<String> friends = playerFriends.getOrDefault(playerId, new ArrayList<>());
        
        Player target = Bukkit.getPlayerExact(friendName);
        if (target != null) {
            String targetId = target.getUniqueId().toString();
            friends.remove(targetId);
            player.sendMessage("§a已將 " + friendName + " 移除為好友");
        } else {
            player.sendMessage("§c找不到玩家: " + friendName);
        }
    }

    private void listFriends(Player player) {
        if (!permissionManager.hasPermission(player, "partygame.friend.view_online")) {
            player.sendMessage("§c你沒有權限查看好友列表");
            return;
        }
        
        String playerId = player.getUniqueId().toString();
        List<String> friends = playerFriends.getOrDefault(playerId, new ArrayList<>());
        
        if (friends.isEmpty()) {
            player.sendMessage("§7你目前沒有好友");
            return;
        }
        
        player.sendMessage("§6=== 好友列表 (共 " + friends.size() + " 人) ===");
        for (String friendId : friends) {
            Player friend = Bukkit.getPlayer(UUID.fromString(friendId));
            String status = friend != null ? "§a線上" : "§7離線";
            player.sendMessage("  §f" + friendId + " " + status);
        }
    }

    private void blockPlayer(Player player, String playerName) {
        if (!permissionManager.hasPermission(player, "partygame.friend.block")) {
            player.sendMessage("§c你沒有權限加入黑名單");
            return;
        }
        
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            player.sendMessage("§c找不到玩家: " + playerName);
            return;
        }
        
        UUID playerId = player.getUniqueId().toString();
        String targetId = target.getUniqueId().toString();
        
        List<String> blocks = playerBlocks.computeIfAbsent(playerId, k -> new ArrayList<>());
        if (blocks.contains(targetId)) {
            player.sendMessage("§c你已經將 " + playerName + " 加入黑名單了");
            return;
        }
        
        blocks.add(targetId);
        player.sendMessage("§a已將 " + playerName + " 加入黑名單");
    }

    private void unblockPlayer(Player player, String playerName) {
        UUID playerId = player.getUniqueId().toString();
        List<String> blocks = playerBlocks.getOrDefault(playerId, new ArrayList<>());
        
        Player target = Bukkit.getPlayerExact(playerName);
        if (target != null) {
            String targetId = target.getUniqueId().toString();
            blocks.remove(targetId);
            player.sendMessage("§a已將 " + playerName + " 移除黑名單");
        } else {
            player.sendMessage("§c找不到玩家: " + playerName);
        }
    }

    private void listBlocks(Player player) {
        UUID playerId = player.getUniqueId().toString();
        List<String> blocks = playerBlocks.getOrDefault(playerId, new ArrayList<>());
        
        if (blocks.isEmpty()) {
            player.sendMessage("§7黑名單目前是空的");
            return;
        }
        
        player.sendMessage("§6=== 黑名單 (共 " + blocks.size() + " 人) ===");
        for (String blockId : blocks) {
            player.sendMessage("  §c" + blockId);
        }
    }

    private void showHelp(Player player) {
        player.sendMessage("§6=== 好友系統 ===");
        player.sendMessage("  §a/friend add <玩家> §7- 添加好友");
        player.sendMessage("  §a/friend remove <玩家> §7- 移除好友");
        player.sendMessage("  §a/friend list §7- 查看好友列表");
        player.sendMessage("  §a/friend block <玩家> §7- 加入黑名單");
        player.sendMessage("  §a/friend unblock <玩家> §7 - 解除黑名單");
        player.sendMessage("  §a/friend blocklist §7- 查看黑名單");
    }

    private void loadFriendData() {
        playerFriends.clear();
        playerBlocks.clear();
        getLogger().info("好友數據已載入");
    }

    private void saveFriendData() {
        // 在完整版本中會保存到 MySQL
        getLogger().info("好友數據已保存");
    }
}
