package co.partygame.core.event;

import co.partygame.core.game.GameSession;
import java.util.EventObject;

/**
 * Event fired when a round ends in a session.
 */
public class SessionRoundEndEvent implements SessionEventListener {
    private final GameSession session;
    private final int round;

    public SessionRoundEndEvent(GameSession session, int round) {
        this.session = session;
        this.round = round;
    }

    public GameSession getSession() { return session; }
    public int getRound() { return round; }
}
