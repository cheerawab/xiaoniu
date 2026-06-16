package co.partygame.common.protocol.packets.backend;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 遊戲結束後的單個玩家結果。
 */
public class GameResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID uuid;
    private final int score;

    public GameResult(UUID uuid, int score) {
        this.uuid = uuid;
        this.score = score;
    }

    public UUID getUuid() { return uuid; }
    public int getScore() { return score; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameResult that = (GameResult) o;
        return score == that.score && Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, score);
    }
}
