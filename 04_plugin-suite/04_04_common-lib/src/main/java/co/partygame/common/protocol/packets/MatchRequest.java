package co.partygame.common.protocol.packets;

import co.partygame.common.McCommonPlugin;

import java.util.UUID;

public class MatchRequest {
    private final String sessionId;
    private final String gameType;
    private final UUID[] players;
    private final String partyId;
    private final java.util.Map<String, Object> customOptions;
    private final String sourceServer;

    public MatchRequest(String sessionId, String gameType, UUID[] players, 
                       String partyId, java.util.Map<String, Object> customOptions, 
                       String sourceServer) {
        this.sessionId = sessionId;
        this.gameType = gameType;
        this.players = players;
        this.partyId = partyId;
        this.customOptions = customOptions;
        this.sourceServer = sourceServer;
    }

    public String getSessionId() { return sessionId; }
    public String getGameType() { return gameType; }
    public UUID[] getPlayers() { return players; }
    public String getPartyId() { return partyId; }
    public java.util.Map<String, Object> getCustomOptions() { return customOptions; }
    public String getSourceServer() { return sourceServer; }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"session_id\":\"").append(sessionId).append("\",");
        json.append("\"game_type\":\"").append(gameType).append("\",");
        
        json.append("\"players\":[");
        for (int i = 0; i < players.length; i++) {
            json.append("\"").append(players[i].toString()).append("\"");
            if (i < players.length - 1) json.append(",");
        }
        json.append("],");
        
        json.append("\"party_id\":\"").append(partyId != null ? partyId : "null").append("\",");
        
        json.append("\"custom_options\":{");
        if (customOptions != null) {
            for (java.util.Map.Entry<String, Object> entry : customOptions.entrySet()) {
                json.append("\"").append(entry.getKey()).append("\":\"").append(String.valueOf(entry.getValue())).append("\",");
            }
        }
        json.append("},");
        
        json.append("\"source_server\":\"").append(sourceServer).append("\"");
        json.append("}");
        
        return json.toString();
    }
}
