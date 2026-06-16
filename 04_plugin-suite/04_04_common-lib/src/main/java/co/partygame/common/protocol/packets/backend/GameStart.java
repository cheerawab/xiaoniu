package co.partygame.common.protocol.packets.backend;

import java.util.Objects;

/**
 * 遊戲開始通知 - 由 Backend 發送到 Lobby 伺服器。
 * 告知伺服器遊戲已開始，包含倒計時信息供 UI 展示。
 */
public class GameStart {
    private final String sessionId;
    private final String gameType;
    private final String world;
    private final int countdown;

    public GameStart(String sessionId, String gameType, String world, int countdown) {
        this.sessionId = sessionId;
        this.gameType = gameType;
        this.world = world;
        this.countdown = countdown;
    }

    public String getSessionId() { return sessionId; }
    public String getGameType() { return gameType; }
    public String getWorld() { return world; }
    public int getCountdown() { return countdown; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameStart gameStart = (GameStart) o;
        return countdown == gameStart.countdown && Objects.equals(sessionId, gameStart.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, gameType, world, countdown);
    }
}
