package co.partygame.converter;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a section (16x16xY slice) within a Minecraft chunk.
 *
 * <p>A chunk is divided into sections of 16x16x16 blocks.
 * In 1.21.7, sections use Y indices relative to the chunk's
 * bottom Y position (-64 for the overworld).</p>
 *
 * @since 1.0.0
 */
public class SectionData implements Comparable<SectionData> {

    private final int y;
    private final List<BlockState> blockStates;
    private final List<String> biomes;
    private final Map<String, Object> light;
    private final Map<String, Object> additionalData;
    private final int dataVersion;

    /**
     * Creates a new SectionData.
     *
     * @param y             the section Y index
     * @param blockStates   the list of block states in this section
     * @param biomes        the biome data for this section
     * @param light         the light maps (sky, block)
     * @param additionalData  extra section metadata
     * @param dataVersion   the Minecraft data version
     */
    public SectionData(
            int y,
            List<BlockState> blockStates,
            List<String> biomes,
            Map<String, Object> light,
            Map<String, Object> additionalData,
            int dataVersion
    ) {
        this.y = y;
        this.blockStates = Objects.requireNonNull(blockStates, "Block states cannot be null");
        this.biomes = Objects.requireNonNullElse(biomes, List.of());
        this.light = Objects.requireNonNullElse(light, Map.of());
        this.additionalData = Objects.requireNonNullElse(additionalData, Map.of());
        this.dataVersion = dataVersion;
    }

    /**
     * Creates a section with default values.
     *
     * @param y the section Y index
     */
    public SectionData(int y) {
        this(y, List.of(), List.of(), Map.of(), Map.of(), 0);
    }

    /**
     * Gets the section Y index.
     *
     * @return the Y index (-4 to 19 in 1.21.7 overworld)
     */
    public int getY() {
        return y;
    }

    /**
     * Gets the block states in this section.
     *
     * @return the list of block states
     */
    public List<BlockState> getBlockStates() {
        return blockStates;
    }

    /**
     * Gets the biome data for this section.
     *
     * @return the list of biome identifiers
     */
    public List<String> getBiomes() {
        return biomes;
    }

    /**
     * Gets the light maps for this section.
     *
     * @return a map containing "block" and "sky" light data
     */
    public Map<String, Object> getLight() {
        return Map.copyOf(light);
    }

    /**
     * Gets additional section metadata.
     *
     * @return a map of extra section data
     */
    public Map<String, Object> getAdditionalData() {
        return Map.copyOf(additionalData);
    }

    /**
     * Gets the data version stored in this section.
     *
     * @return the data version number
     */
    public int getDataVersion() {
        return dataVersion;
    }

    /**
     * Checks if this section is empty (contains only air).
     *
     * @return true if all block states are air
     */
    public boolean isEmpty() {
        return blockStates.stream().allMatch(BlockState::isAir);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SectionData that)) return false;
        return y == that.y && blockStates.equals(that.blockStates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(y, blockStates);
    }

    @Override
    public int compareTo(SectionData other) {
        return Integer.compare(this.y, other.y);
    }

    @Override
    public String toString() {
        return "SectionData{y=" + y + ", blocks=" + blockStates.size() + ", empty=" + isEmpty() + "}";
    }
}
