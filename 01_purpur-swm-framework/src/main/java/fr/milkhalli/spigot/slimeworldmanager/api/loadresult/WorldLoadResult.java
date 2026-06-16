package fr.milkhalli.spigot.slimeworldmanager.api.loadresult;

/**
 * Stub for SlimeWorldManager's WorldLoadResult.
 * This class is normally provided by the SlimeWorldManager plugin.
 */
public class WorldLoadResult {
    public enum Status {
        SUCCESS, LOAD_FAILED, WORLD_SAVED, WORLD_ALREADY_LOADED, FILE_NOT_FOUND
    }

    private final Status status;
    private final String worldName;
    private final String message;

    public WorldLoadResult(Status status, String worldName) {
        this(status, worldName, null);
    }

    public WorldLoadResult(Status status, String worldName, String message) {
        this.status = status;
        this.worldName = worldName;
        this.message = message;
    }

    public Status status() { return status; }
    public String worldName() { return worldName; }
    public String message() { return message; }

    public boolean isSuccess() { return status == Status.SUCCESS; }
}
