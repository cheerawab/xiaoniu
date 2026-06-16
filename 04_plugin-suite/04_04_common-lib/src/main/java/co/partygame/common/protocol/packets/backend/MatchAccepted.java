package co.partygame.common.protocol.packets.backend;

import java.util.List;
import java.util.Objects;

/**
 * 配對成功通知 - 由 Backend 發送到 Lobby 伺服器。
 * 所有配對成功的玩家將被路由到指定伺服器。
 */
public class MatchAccepted {
    private final String sessionId;
    private final String gameType;
    private final String world;
    private final String server;
    private final List<PlayerInfo> players;
    private final String gamePayloadJson;

    public MatchAccepted(String sessionId, String gameType, String world, String server, List<PlayerInfo> players, String gamePayloadJson) {
        this.sessionId = sessionId;
        this.gameType = gameType;
        this.world = world;
        this.server = server;
        this.players = players;
        this.gamePayloadJson = gamePayloadJson;
    }

    public String getSessionId() { return sessionId; }
    public String getGameType() { return gameType; }
    public String getWorld() { return world; }
    public String getServer() { return server; }
    public List<PlayerInfo> getPlayers() { return players; }
    public String getGamePayloadJson() { return gamePayloadJson; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchAccepted that = (MatchAccepted) o;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
}
