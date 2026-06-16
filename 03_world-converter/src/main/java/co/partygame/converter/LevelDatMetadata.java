package co.partygame.converter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the metadata from Minecraft's level.dat file.
 *
 * <p>level.dat contains world-level data including world name,
 * game mode, seed, spawn position, data version, and other
 * global settings.</p>
 *
 * @since 1.0.0
 */
public class LevelDatMetadata {

    private final int dataVersion;
    private final String levelName;
    private final long seed;
    private final String gameMode;
    private final boolean hardCore;
    private final boolean spectatorMode;
    private final boolean allowCommands;
    private final double generatorOptions;
    private final int generatorType;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final Map<String, Object> surfaceSettings;
    private final Map<String, Object> dimensions;
    private final long lastTimePlayed;
    private final long playedTime;
    private final long randomSeed;
    private final boolean reducedDebugInfo;
    private final String version;
    private final int versionId;

    /**
     * Creates a new LevelDatMetadata instance.
     *
     * @param dataVersion       the Minecraft data version
     * @param levelName         the world name
     * @param seed              the world seed (raw long value)
     * @param gameMode          the default game mode (survival, creative, etc.)
     * @param hardCore          whether hardcore mode is enabled
     * @param spectatorMode     whether spectator mode is enabled
     * @param allowCommands     whether commands are allowed
     * @param generatorOptions  custom generator options string
     * @param generatorType     the world generator type
     * @param spawnX            the spawn X coordinate
     * @param spawnY            the spawn Y coordinate
     * @param spawnZ            the spawn Z coordinate
     * @param surfaceSettings   surface settings map
     * @param dimensions        dimension data
     * @param lastTimePlayed    last time the world was played (ticks)
     * @param playedTime        total played time (ticks)
     * @param randomSeed        random seed for structures
     * @param reducedDebugInfo  whether reduced debug info is shown
     * @param version           the Minecraft version string
     * @param versionId         the internal version ID
     */
    @SuppressWarnings("unchecked")
    public LevelDatMetadata(
            int dataVersion,
            String levelName,
            long seed,
            String gameMode,
            boolean hardCore,
            boolean spectatorMode,
            boolean allowCommands,
            String generatorOptions,
            int generatorType,
            double spawnX,
            double spawnY,
            double spawnZ,
            Map<String, Object> surfaceSettings,
            Map<String, Object> dimensions,
            long lastTimePlayed,
            long playedTime,
            long randomSeed,
            boolean reducedDebugInfo,
            String version,
            int versionId
    ) {
        this.dataVersion = dataVersion;
        this.levelName = Objects.requireNonNullElse(levelName, "world");
        this.seed = seed;
        this.gameMode = Objects.requireNonNullElse(gameMode, "survival");
        this.hardCore = hardCore;
        this.spectatorMode = spectatorMode;
        this.allowCommands = allowCommands;
        this.generatorOptions = generatorOptions != null ? generatorOptions.hashCode() : 0;
        this.generatorType = generatorType;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.surfaceSettings = (Map<String, Object>) Objects.requireNonNullElse(surfaceSettings, Map.of());
        this.dimensions = (Map<String, Object>) Objects.requireNonNullElse(dimensions, Map.of());
        this.lastTimePlayed = lastTimePlayed;
        this.playedTime = playedTime;
        this.randomSeed = randomSeed;
        this.reducedDebugInfo = reducedDebugInfo;
        this.version = Objects.requireNonNullElse(version, "unknown");
        this.versionId = versionId;
    }

    /**
     * Creates a minimal LevelDatMetadata with defaults.
     *
     * @param dataVersion the Minecraft data version
     */
    public LevelDatMetadata(int dataVersion) {
        this(dataVersion, "world", 0L, "survival", false, false, false,
                null, 0, 0, 80, 0, Map.of(), Map.of(),
                0, 0, 0, false, "unknown", 0);
    }

    public int getDataVersion() { return dataVersion; }
    public String getLevelName() { return levelName; }
    public long getSeed() { return seed; }
    public String getGameMode() { return gameMode; }
    public boolean isHardCore() { return hardCore; }
    public boolean isSpectatorMode() { return spectatorMode; }
    public boolean isAllowCommands() { return allowCommands; }
    public int getGeneratorType() { return generatorType; }
    public double getSpawnX() { return spawnX; }
    public double getSpawnY() { return spawnY; }
    public double getSpawnZ() { return spawnZ; }
    public Map<String, Object> getSurfaceSettings() { return Map.copyOf(surfaceSettings); }
    public Map<String, Object> getDimensions() { return Map.copyOf(dimensions); }
    public long getLastTimePlayed() { return lastTimePlayed; }
    public long getPlayedTime() { return playedTime; }
    public long getRandomSeed() { return randomSeed; }
    public boolean isReducedDebugInfo() { return reducedDebugInfo; }
    public String getVersion() { return version; }
    public int getVersionId() { return versionId; }

    /**
     * Gets the seed as an integer value.
     *
     * @return the seed cast to int
     */
    public int getSeedInt() {
        return (int) (seed ^ (seed >>> 32));
    }

    /**
     * Checks if the generator options are custom (not default terrain).
     *
     * @return true if generator options differ from default
     */
    public boolean hasCustomGeneratorOptions() {
        return generatorOptions != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LevelDatMetadata that)) return false;
        return dataVersion == that.dataVersion && Objects.equals(levelName, that.levelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataVersion, levelName);
    }

    @Override
    public String toString() {
        return "LevelDatMetadata{levelName='" + levelName + "', version='" + version
                + "', dataVersion=" + dataVersion + ", seed=" + seed
                + ", gameMode='" + gameMode + "'}";
    }
}
