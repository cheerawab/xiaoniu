package co.partygame.common.protocol.packets;

import java.util.UUID;
import java.util.Map;

public class MatchAccepted {
    private final String sessionId;
    private final String gameType;
    private final String world;
    private final String server;
    private final UUID[] players;

    public MatchAccepted(String sessionId, String gameType, String world, 
                        String server, UUID[] players) {
        this.sessionId = sessionId;
        this.gameType = gameType;
        this.world = world;
        this.server = server;
        this.players = players;
    }

    public String getSessionId() { return sessionId; }
    public String getGameType() { return gameType; }
    public String getWorld() { return world; }
    public String getServer() { return server; }
    public UUID[] getPlayers() { return players; }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"MATCH_ACCEPTED\",");
        json.append("\"session_id\":\"").append(sessionId).append("\",");
        json.append("\"game_type\":\"").append(gameType).append("\",");
        json.append("\"world\":\"").append(world).append("\",");
        json.append("\"server\":\"").append(server).append("\",");
        
        json.append("\"players\":[");
        for (int i = 0; i < players.length; i++) {
            json.append("\"").append(players[i].toString()).append("\"");
            if (i < players.length - 1) json.append(",");
        }
        json.append("]");
        
        json.append("}");
        return json.toString();
    }
}
