package co.partygame.core.event;

import java.util.EventListener;
import java.util.UUID;

/**
 * Base event listener interface for all game session events.
 *
 * <p>Events are fired by the SessionController during session lifecycle
 * transitions. Listeners can react to session creation, player join/leave,
 * round transitions, and session completion.</p>
 */
public interface SessionEventListener extends EventListener {
}
