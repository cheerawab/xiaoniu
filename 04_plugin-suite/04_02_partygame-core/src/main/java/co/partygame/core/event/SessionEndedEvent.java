package co.partygame.core.event;

import co.partygame.core.game.GameSession;
import java.util.EventObject;

/**
 * Event fired when a game session ends (all rounds completed).
 */
public class SessionEndedEvent implements SessionEventListener {
    private final GameSession session;
    private final long durationMillis;

    public SessionEndedEvent(GameSession session, long durationMillis) {
        this.session = session;
        this.durationMillis = durationMillis;
    }

    public GameSession getSession() { return session; }
    public long getDurationMillis() { return durationMillis; }
}
