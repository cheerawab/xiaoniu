package co.partygame.partygame.partyframework;

/**
 * Match request received from the Lobby server via BungeeCord messaging channels.
 * <p>
 * This class encapsulates all information needed to process a game request:
 * which game to play, who is playing, where the players are hosted,
 * and what game-specific settings apply.
 */
public final class MatchRequest {

    private final String requestId;
    private final String gameType;
    private final String gameId;
    private final Integer gameVersion;
    private final java.util.List<String> playerNames;
    private final java.util.List<String> playerUUIDs;
    private final java.util.Map<String, String> settings;
    private final java.util.Map<String, Object> customOptions;
    private final String sourceServer;
    private final long timestamp;

    private MatchRequest(Builder builder) {
        this.requestId = builder.requestId;
        this.gameType = builder.gameType;
        this.gameId = builder.gameId;
        this.gameVersion = builder.gameVersion;
        this.playerNames = builder.playerNames;
        this.playerUUIDs = builder.playerUUIDs;
        this.settings = java.util.Collections.unmodifiableMap(builder.settings);
        this.customOptions = java.util.Collections.unmodifiableMap(builder.customOptions);
        this.sourceServer = builder.sourceServer;
        this.timestamp = builder.timestamp;
    }

    public String getRequestId() { return requestId; }
    public String getGameType() { return gameType; }
    public String getGameId() { return gameId; }
    public java.util.Optional<Integer> getGameVersion() { return java.util.Optional.ofNullable(gameVersion); }
    public java.util.List<String> getPlayerNames() { return playerNames; }
    public java.util.List<String> getPlayerUUIDs() { return playerUUIDs; }
    public java.util.Map<String, String> getSettings() { return settings; }
    public java.util.Map<String, Object> getCustomOptions() { return customOptions; }
    public String getSourceServer() { return sourceServer; }
    public long getTimestamp() { return timestamp; }
    public int getPlayerCount() { return playerNames.size(); }

    @Override
    public String toString() {
        return "MatchRequest{" +
                "id='" + requestId + '\'' +
                ", type=" + gameType +
                ", players=" + playerNames +
                ", source=" + sourceServer +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String requestId;
        private String gameType;
        private String gameId;
        private Integer gameVersion;
        private java.util.List<String> playerNames = new java.util.ArrayList<>();
        private java.util.List<String> playerUUIDs = new java.util.ArrayList<>();
        private java.util.Map<String, String> settings = new java.util.HashMap<>();
        private java.util.Map<String, Object> customOptions = new java.util.HashMap<>();
        private String sourceServer;
        private long timestamp;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder gameType(String gameType) {
            this.gameType = gameType;
            return this;
        }

        public Builder gameId(String gameId) {
            this.gameId = gameId;
            return this;
        }

        public Builder gameVersion(int gameVersion) {
            this.gameVersion = gameVersion;
            return this;
        }

        public Builder playerNames(java.util.List<String> playerNames) {
            this.playerNames = java.util.Objects.requireNonNull(playerNames);
            return this;
        }

        public Builder playerUUIDs(java.util.List<String> playerUUIDs) {
            this.playerUUIDs = java.util.Objects.requireNonNull(playerUUIDs);
            return this;
        }

        public Builder settings(java.util.Map<String, String> settings) {
            this.settings = java.util.Objects.requireNonNull(settings);
            return this;
        }

        public Builder customOptions(java.util.Map<String, Object> customOptions) {
            this.customOptions = java.util.Objects.requireNonNull(customOptions);
            return this;
        }

        public Builder sourceServer(String sourceServer) {
            this.sourceServer = sourceServer;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public MatchRequest build() {
            if (requestId == null || requestId.isEmpty()) {
                requestId = java.util.UUID.randomUUID().toString();
            }
            if (gameType == null || gameType.isEmpty()) {
                throw new IllegalStateException("gameType is required");
            }
            if (playerNames == null) {
                playerNames = new java.util.ArrayList<>();
            }
            if (playerUUIDs == null) {
                playerUUIDs = new java.util.ArrayList<>();
            }
            if (settings == null) {
                settings = new java.util.HashMap<>();
            }
            if (customOptions == null) {
                customOptions = new java.util.HashMap<>();
            }
            if (timestamp == 0) {
                timestamp = System.currentTimeMillis();
            }
            return new MatchRequest(this);
        }
    }
}
