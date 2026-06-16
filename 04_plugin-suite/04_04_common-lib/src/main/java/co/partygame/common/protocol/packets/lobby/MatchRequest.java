package co.partygame.common.protocol.packets.lobby;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 配對請求 - 由 Lobby 伺服器發送到 Backend。
 * 當玩家主動請求配對時發送，包含所有參與玩家的信息。
 */
public class MatchRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sessionId;
    private final String gameId;
    private final String gameType;
    private final List<PlayerInfo> players;
    private final Map<String, Object> customOptions;
    private final UUID partyId;
    private final String sourceServer;

    public MatchRequest(String sessionId, String gameId, String gameType, List<PlayerInfo> players,
                        Map<String, Object> customOptions, UUID partyId, String sourceServer) {
        this.sessionId = sessionId;
        this.gameId = gameId;
        this.gameType = gameType;
        this.players = players;
        this.customOptions = customOptions;
        this.partyId = partyId;
        this.sourceServer = sourceServer;
    }

    /**
     * 便捷構造方法，無團隊、無自定義選項的簡單配對請求。
     */
    public MatchRequest(String sessionId, String gameId, String gameType, List<PlayerInfo> players, String sourceServer) {
        this(sessionId, gameId, gameType, players, null, null, sourceServer);
    }

    public String getSessionId() { return sessionId; }
    public String getGameId() { return gameId; }
    public String getGameType() { return gameType; }
    public List<PlayerInfo> getPlayers() { return players; }
    public Map<String, Object> getCustomOptions() { return customOptions; }
    public UUID getPartyId() { return partyId; }
    public String getSourceServer() { return sourceServer; }

    /**
     * 獲取自定義選項中的 String 類型參數。
     * 如果選項不存在或類型不匹配，返回 default 值。
     */
    public String getCustomOption(String key, String defaultValue) {
        if (customOptions == null || !customOptions.containsKey(key)) return defaultValue;
        Object val = customOptions.get(key);
        return val instanceof String ? (String) val : defaultValue;
    }

    /**
     * 獲取自定義選項中的 Boolean 類型參數。
     */
    public Boolean getCustomOptionBool(String key, Boolean defaultValue) {
        if (customOptions == null || !customOptions.containsKey(key)) return defaultValue;
        Object val = customOptions.get(key);
        return val instanceof Boolean ? (Boolean) val : defaultValue;
    }

    /**
     * 獲取自定義選項中的 Integer 類型參數。
     */
    public Integer getCustomOptionInt(String key, Integer defaultValue) {
        if (customOptions == null || !customOptions.containsKey(key)) return defaultValue;
        Object val = customOptions.get(key);
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchRequest that = (MatchRequest) o;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }

    @Override
    public String toString() {
        return "MatchRequest{sessionId=" + sessionId +", gameType=" + gameType
            +", players=" + (players != null ? players.size() : 0)
            +", partyId=" + partyId
            +", sourceServer=" + sourceServer + "}";
    }
}
