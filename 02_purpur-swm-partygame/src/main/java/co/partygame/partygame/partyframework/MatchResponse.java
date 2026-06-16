package co.partygame.partygame.partyframework;

import java.util.Map;

/**
 * Response to a MatchRequest.
 * <p>
 * Sent back to the Lobby (via BungeeCord) after processing a game request.
 */
public final class MatchResponse {

    private final String requestId;
    private final ResponseStatus status;
    private final String sessionId;
    private final String worldName;
    private final String errorMessage;

    public enum ResponseStatus {
        ACCEPTED,
        DENIED,
        QUEUE
    }

    private MatchResponse(Builder builder) {
        this.requestId = builder.requestId;
        this.status = builder.status;
        this.sessionId = builder.sessionId;
        this.worldName = builder.worldName;
        this.errorMessage = builder.errorMessage;
    }

    public String getRequestId() { return requestId; }
    public ResponseStatus getStatus() { return status; }
    public String getSessionId() { return sessionId; }
    public String getWorldName() { return worldName; }
    public String getErrorMessage() { return errorMessage; }

    public static Builder builder(String requestId) {
        return new Builder(requestId);
    }

    public static class Builder {
        private final String requestId;
        private ResponseStatus status;
        private String sessionId;
        private String worldName;
        private String errorMessage;

        public Builder(String requestId) {
            this.requestId = requestId;
        }

        public Builder accepted(String sessionId, String worldName) {
            this.status = ResponseStatus.ACCEPTED;
            this.sessionId = sessionId;
            this.worldName = worldName;
            return this;
        }

        public Builder denied(String reason) {
            this.status = ResponseStatus.DENIED;
            this.errorMessage = reason;
            return this;
        }

        public Builder queued(String reason) {
            this.status = ResponseStatus.QUEUE;
            this.errorMessage = reason;
            return this;
        }

        public MatchResponse build() {
            if (status == null) {
                if (sessionId != null) {
                    status = ResponseStatus.ACCEPTED;
                } else {
                    status = ResponseStatus.DENIED;
                    if (errorMessage == null || errorMessage.isEmpty()) {
                        errorMessage = "Unknown error";
                    }
                }
            }
            return new MatchResponse(this);
        }
    }
}
