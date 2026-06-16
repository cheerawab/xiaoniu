package co.partygame.common.util;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import co.partygame.common.redis.RedisManager;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI Hook。
 *
 * 實現 me.clip.placeholderapi.expansion.PlaceholderExpansion 接口，
 * 支援在聊天訊息和物品名稱中使用 partygame 相關的占位符。
 *
 * 支援的占位符：
 * <ul>
 *   <li>`%partygame_match_count%` – 當前配對等待中的玩家數</li>
 *   <li>`%partygame_server_count%` – 當前線上伺服器數</li>
 *   <li>`%partygame_wait_time%` – 平均配對等待時間（秒）</li>
 *   <li>`%partygame_played_count%` – 玩家已完成的配對數</li>
 *   <li>`%partygame_streak%` – 連勝次數</li>
 *   <li>`%partygame_win_count%` – 勝場數</li>
 *   <li>`%partygame_party_size%` – 當前團隊大小</li>
 *   <li>`%partygame_party_members%` – 團隊成員數</li>
 * </ul>
 *
 * 使用示例：
 * <pre>{@code
 * new PlaceholderAPIHook(placeholderManager, redisManager).register();
 * }</pre>
 */
public class PlaceholderAPIHook extends PlaceholderExpansion {

    private static final Logger LOGGER = Logger.getLogger(PlaceholderAPIHook.class.getName());

    private final org.bukkit.plugin.Plugin plugin;
    private final RedisManager redisManager;

    /**
     * 創建 PlaceholderAPI Hook。
     *
     * @param plugin       插件實例
     * @param redisManager Redis 管理器實例
     */
    public PlaceholderAPIHook(org.bukkit.plugin.Plugin plugin, RedisManager redisManager) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin must not be null");
        this.redisManager = redisManager;
    }

    /**
     * 註冊此 PlaceholderExpansion 到 PlaceholderAPI。
     */
    public boolean register() {
        return this.register();
    }

    @Override
    public boolean register() {
        try {
            return me.clip.placeholderapi.PlaceholderAPI.registerExpansion(this);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to register PlaceholderAPI hook", e);
            return false;
        }
    }

    @Override
    public String getPlaceholderId() {
        return "partygame";
    }

    @Override
    public String getAuthor() {
        return "PursWM";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (params == null || params.isEmpty()) {
            return null;
        }

        switch (params.toLowerCase(Locale.ROOT)) {
            case "match_count":
                return getMatchCount();
            case "server_count":
                return getServerCount();
            case "wait_time":
                return getWaitTime();
            case "played_count":
                return getPlayedCount(offlinePlayer);
            case "win_count":
                return getWinCount(offlinePlayer);
            case "streak":
                return getStreak(offlinePlayer);
            case "party_size":
                return getPartySize(offlinePlayer);
            case "party_members":
                return getPartyMembers(offlinePlayer);
            default:
                return null;
        }
    }

    private String getMatchCount() {
        try {
            if (redisManager != null && redisManager.isConnected()) {
                String count = redisManager.get("partygame:queue:size");
                if (count != null) return count;
            }
            return "0";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get match count", e);
            return "0";
        }
    }

    private String getServerCount() {
        try {
            if (redisManager != null && redisManager.isConnected()) {
                String count = redisManager.get("partygame:servers:count");
                if (count != null) return count;
            }
            return "0";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get server count", e);
            return "0";
        }
    }

    private String getWaitTime() {
        try {
            if (redisManager != null && redisManager.isConnected()) {
                String waitTime = redisManager.get("partygame:queue:avg_wait_time");
                if (waitTime != null) return waitTime;
            }
            return "0";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get wait time", e);
            return "0";
        }
    }

    private String getPlayedCount(OfflinePlayer player) {
        try {
            if (player != null && player.getUniqueId() != null) {
                String key = "partygame:user:" + player.getUniqueId().toString() + ":played_count";
                if (redisManager != null && redisManager.isConnected()) {
                    String count = redisManager.get(key);
                    if (count != null) return count;
                }
            }
            return "0";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get played count for " + player, e);
            return "0";
        }
    }

    private String getWinCount(OfflinePlayer player) {
        try {
            if (player != null && player.getUniqueId() != null) {
                String key = "partygame:user:" + player.getUniqueId().toString() + ":wins";
                if (redisManager != null && redisManager.isConnected()) {
                    String count = redisManager.get(key);
                    if (count != null) return count;
                }
            }
            return "0";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get win count for " + player, e);
            return "0";
        }
    }

    private String getStreak(OfflinePlayer player) {
        try {
            if (player != null && player.getUniqueId() != null) {
                String key = "partygame:user:" + player.getUniqueId().toString() + ":streak";
                if (redisManager != null && redisManager.isConnected()) {
                    String streak = redisManager.get(key);
                    if (streak != null) return streak;
                }
            }
            return "0";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get streak for " + player, e);
            return "0";
        }
    }

    private String getPartySize(OfflinePlayer player) {
        try {
            if (player != null && player.getUniqueId() != null) {
                String key = "partygame:party:" + player.getUniqueId().toString() + ":size";
                if (redisManager != null && redisManager.isConnected()) {
                    String size = redisManager.get(key);
                    if (size != null) return size;
                }
            }
            return "0";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get party size for " + player, e);
            return "0";
        }
    }

    private String getPartyMembers(OfflinePlayer player) {
        try {
            if (player != null && player.getUniqueId() != null) {
                String key = "partygame:party:" + player.getUniqueId().toString() + ":members";
                if (redisManager != null && redisManager.isConnected()) {
                    String members = redisManager.get(key);
                    if (members != null) return members;
                }
            }
            return "0";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get party members for " + player, e);
            return "0";
        }
    }
}
