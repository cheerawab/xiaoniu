package co.partygame.common.protocol.packets;

import java.util.UUID;

public class GameNotification {
    private final String sessionId;
    private final String notificationType;
    private final String message;
    private final java.util.Map<String, Object> data;

    public GameNotification(String sessionId, String notificationType, 
                           String message, java.util.Map<String, Object> data) {
        this.sessionId = sessionId;
        this.notificationType = notificationType;
        this.message = message;
        this.data = data;
    }

    public String getSessionId() { return sessionId; }
    public String getNotificationType() { return notificationType; }
    public String getMessage() { return message; }
    public java.util.Map<String, Object> getData() { return data; }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"GAME_NOTIFICATION\",");
        json.append("\"session_id\":\"").append(sessionId).append("\",");
        json.append("\"notification_type\":\"").append(notificationType).append("\",");
        json.append("\"message\":\"").append(message).append("\",");
        
        json.append("\"data\":{");
        if (data != null) {
            for (java.util.Map.Entry<String, Object> entry : data.entrySet()) {
                json.append("\"").append(entry.getKey()).append("\":\"").append(String.valueOf(entry.getValue())).append("\",");
            }
        }
        json.append("}");
        
        json.append("}");
        return json.toString();
    }
}
