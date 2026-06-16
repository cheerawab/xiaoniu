package co.partygame.converter.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Configuration for the world conversion process.
 *
 * <p>Holds all CLI and programmatic parameters for converting
 * a vanilla Minecraft world folder to the Slime format.</p>
 *
 * @since 1.0.0
 */
public class ConverterConfig {

    /** The data version target for 1.21.7. */
    public static final int DEFAULT_TARGET_DATA_VERSION = 4438;

    private Path input;
    private Path output;
    private String worldName;
    private int targetDataVersion;
    private boolean skipChunks;
    private boolean skipEntities;
    private boolean skipLevelDat;

    /**
     * Creates a ConverterConfig with the given parameters.
     *
     * @param input           the input world directory path
     * @param output          the output directory path
     * @param worldName       the name for the converted world
     */
    public ConverterConfig(Path input, Path output, String worldName) {
        setInput(input);
        setOutput(output);
        setWorldName(worldName);
        this.targetDataVersion = DEFAULT_TARGET_DATA_VERSION;
        this.skipChunks = false;
        this.skipEntities = false;
        this.skipLevelDat = false;
    }

    public ConverterConfig() {
        this.targetDataVersion = DEFAULT_TARGET_DATA_VERSION;
        this.skipChunks = false;
        this.skipEntities = false;
        this.skipLevelDat = false;
    }

    /**
     * Sets the input world directory.
     *
     * @param input the path to the vanilla world folder
     * @return this config for chaining
     */
    public ConverterConfig setInput(Path input) {
        this.input = Objects.requireNonNull(input, "Input path cannot be null");
        return this;
    }

    /**
     * Sets the output directory.
     *
     * @param output the directory path where .slimeworld will be written
     * @return this config for chaining
     */
    public ConverterConfig setOutput(Path output) {
        this.output = Objects.requireNonNull(output, "Output path cannot be null");
        return this;
    }

    /**
     * Sets the world name.
     *
     * @param worldName the name to give the converted world
     * @return this config for chaining
     */
    public ConverterConfig setWorldName(String worldName) {
        this.worldName = Objects.requireNonNullElse(worldName, "world");
        return this;
    }

    /**
     * Sets the target data version.
     *
     * @param targetDataVersion the Minecraft data version to target
     * @return this config for chaining
     */
    public ConverterConfig setTargetDataVersion(int targetDataVersion) {
        this.targetDataVersion = targetDataVersion;
        return this;
    }

    /**
     * Sets whether to skip chunk conversion.
     *
     * @param skipChunks true to skip converting chunks
     * @return this config for chaining
     */
    public ConverterConfig setSkipChunks(boolean skipChunks) {
        this.skipChunks = skipChunks;
        return this;
    }

    /**
     * Sets whether to skip entity conversion.
     *
     * @param skipEntities true to skip converting entities
     * @return this config for chaining
     */
    public ConverterConfig setSkipEntities(boolean skipEntities) {
        this.skipEntities = skipEntities;
        return this;
    }

    /**
     * Sets whether to skip level.dat conversion.
     *
     * @param skipLevelDat true to skip converting level.dat
     * @return this config for chaining
     */
    public ConverterConfig setSkipLevelDat(boolean skipLevelDat) {
        this.skipLevelDat = skipLevelDat;
        return this;
    }

    public Path getInput() { return input; }
    public Path getOutput() { return output; }
    public String getWorldName() { return worldName; }
    public int getTargetDataVersion() { return targetDataVersion; }
    public boolean isSkipChunks() { return skipChunks; }
    public boolean isSkipEntities() { return skipEntities; }
    public boolean isSkipLevelDat() { return skipLevelDat; }
}
