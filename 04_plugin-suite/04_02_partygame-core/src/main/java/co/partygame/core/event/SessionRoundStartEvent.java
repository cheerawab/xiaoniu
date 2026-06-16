package co.partygame.core.event;

import co.partygame.core.game.GameSession;
import java.util.EventObject;

/**
 * Event fired when a round starts in a session.
 */
public class SessionRoundStartEvent implements SessionEventListener {
    private final GameSession session;
    private final int round;

    public SessionRoundStartEvent(GameSession session, int round) {
        this.session = session;
        this.round = round;
    }

    public GameSession getSession() { return session; }
    public int getRound() { return round; }
}
