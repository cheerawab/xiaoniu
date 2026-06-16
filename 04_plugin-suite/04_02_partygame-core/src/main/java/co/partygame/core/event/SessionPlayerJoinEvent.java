package co.partygame.core.event;

import java.util.UUID;
import java.util.EventObject;

/**
 * Event fired when a player joins a session.
 */
public class SessionPlayerJoinEvent implements SessionEventListener {
    private final String sessionId;
    private final String gameType;
    private final UUID player;

    public SessionPlayerJoinEvent(String sessionId, String gameType, UUID player) {
        this.sessionId = sessionId;
        this.gameType = gameType;
        this.player = player;
    }

    public String getSessionId() { return sessionId; }
    public String getGameType() { return gameType; }
    public UUID getPlayer() { return player; }
}
