package co.partygame.matchmaking;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import redis.clients.jedis.JedisPubSub;
import co.partygame.common.util.BungeeMessenger;
import co.partygame.common.McCommonPlugin;

import java.util.UUID;
import java.util.logging.Level;

public class MatchMessageHandler extends JedisPubSub {
    private final MatchmakingPlugin plugin;
    private final BungeeMessenger messenger;

    public MatchMessageHandler(MatchmakingPlugin plugin, BungeeMessenger messenger) {
        this.plugin = plugin;
        this.messenger = messenger;
    }

    @Override
    public void onMessage(String channel, String message) {
        try {
            if (message.contains("\"type\":\"MATCH_ACCEPTED\"")) {
                handleMatchAccepted(message);
            } else if (message.contains("\"type\":\"GAME_START\"")) {
                handleGameStart(message);
            } else if (message.contains("\"type\":\"GAME_END\"")) {
                handleGameEnd(message);
            } else if (message.contains("\"type\":\"GAME_NOTIFICATION\"")) {
                handleGameNotification(message);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "處理 Redis 消息失敗", e);
        }
    }

    private void handleMatchAccepted(String json) {
        String sessionId = extractField(json, "session_id");
        String gameType = extractField(json, "game_type");
        String server = extractField(json, "server");
        
        plugin.getLogger().info("配對成功 - 會話: " + sessionId + ", 遊戲: " + gameType);
        
        messenger.sendMessage("*, " + "MatchAccepted", sessionId, server);
    }

    private void handleGameStart(String json) {
        String sessionId = extractField(json, "session_id");
        plugin.getLogger().info("遊戲開始通知 - 會話: " + sessionId);
        messenger.sendMessage("*", "GameStart", sessionId);
    }

    private void handleGameEnd(String json) {
        String sessionId = extractField(json, "session_id");
        plugin.getLogger().info("遊戲結束通知 - 會話: " + sessionId);
        messenger.sendMessage("*", "GameEnd", sessionId);
    }

    private void handleGameNotification(String json) {
        String msgType = extractField(json, "notification_type");
        String message = extractField(json, "message");
        plugin.getLogger().info("遊戲通知 [" + msgType + "]: " + message);
    }

    private String extractField(String json, String field) {
        String target = "\"" + field + "\":\"";
        int start = json.indexOf(target) + target.length();
        if (start < target.length()) return "";
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
