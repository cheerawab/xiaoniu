package co.partygame.common.protocol.packets.backend;

import java.util.Objects;

/**
 * 遊戲進行中的即時通知 - 由 Backend 發送到 Lobby 伺服器。
 * 用於推送遊戲進度、事件等動態信息到玩家界面。
 */
public class GameNotification {
    private final String sessionId;
    private final String type;
    private final String message;
    private final String dataJson;

    public GameNotification(String sessionId, String type, String message, String dataJson) {
        this.sessionId = sessionId;
        this.type = type;
        this.message = message;
        this.dataJson = dataJson;
    }

    public String getSessionId() { return sessionId; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public String getDataJson() { return dataJson; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameNotification that = (GameNotification) o;
        return Objects.equals(sessionId, that.sessionId)
            && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, type);
    }
}
