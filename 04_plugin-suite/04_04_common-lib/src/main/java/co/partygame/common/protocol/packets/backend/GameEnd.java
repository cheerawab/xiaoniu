package co.partygame.common.protocol.packets.backend;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 遊戲結束通知 - 由 Backend 發送到 Lobby 伺服器。
 * 包含所有參與玩家的得分結果。
 */
public class GameEnd implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sessionId;
    private final String gameType;
    private final List<GameResult> results;

    public GameEnd(String sessionId, String gameType, List<GameResult> results) {
        this.sessionId = sessionId;
        this.gameType = gameType;
        this.results = results;
    }

    public String getSessionId() { return sessionId; }
    public String getGameType() { return gameType; }
    public List<GameResult> getResults() { return results; }

    /**
     * 獲取某個 UUID 玩家的得分，不存在時返回 0。
     */
    public int getScoreFor(java.util.UUID uuid) {
        if (results == null) return 0;
        for (GameResult r : results) {
            if (Objects.equals(r.getUuid(), uuid)) {
                return r.getScore();
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameEnd gameEnd = (GameEnd) o;
        return Objects.equals(sessionId, gameEnd.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, gameType);
    }
}
