package co.partygame.converter;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a Minecraft chunk with its sections, biomes, and metadata.
 *
 * <p>Chunks are 16 blocks wide and long, and span the full world height.
 * Each chunk is divided into sections (16x16x16 blocks) plus a possible
 * lower section for the -64 to -1 range in 1.21.7.</p>
 *
 * @since 1.0.0
 */
public class ChunkData implements Comparable<ChunkData> {

    private final int x;
    private final int z;
    private final List<SectionData> sections;
    private final List<String> biomes;
    private final Map<String, byte[]> heightmaps;
    private final Map<String, Object> blockEntities;
    private final int dataVersion;
    private final String dimension;
    private final Map<String, Object> additionalData;

    /**
     * Creates a new ChunkData.
     *
     * @param x             the chunk X coordinate
     * @param z             the chunk Z coordinate
     * @param sections      the list of section data within this chunk
     * @param biomes        the biome array for this chunk
     * @param heightmaps    the heightmap data (MOTION_BLOCKING, etc.)
     * @param blockEntities the block entities (chests, signs, etc.)
     * @param dataVersion   the Minecraft data version
     * @param dimension     the dimension identifier
     * @param additionalData extra chunk metadata
     */
    public ChunkData(
            int x,
            int z,
            List<SectionData> sections,
            List<String> biomes,
            Map<String, byte[]> heightmaps,
            Map<String, Object> blockEntities,
            int dataVersion,
            String dimension,
            Map<String, Object> additionalData
    ) {
        this.x = x;
        this.z = z;
        this.sections = Objects.requireNonNull(sections, "Sections cannot be null");
        this.biomes = Objects.requireNonNullElse(biomes, List.of());
        this.heightmaps = Objects.requireNonNullElse(heightmaps, Map.of());
        this.blockEntities = Objects.requireNonNullElse(blockEntities, Map.of());
        this.dataVersion = dataVersion;
        this.dimension = Objects.requireNonNullElse(dimension, "overworld");
        this.additionalData = Objects.requireNonNullElse(additionalData, Map.of());
    }

    /**
     * Creates a chunk with default values.
     *
     * @param x the chunk X coordinate
     * @param z the chunk Z coordinate
     */
    public ChunkData(int x, int z) {
        this(x, z, List.of(), List.of(), Map.of(), Map.of(), 0, "overworld", Map.of());
    }

    /**
     * Gets the chunk X coordinate.
     *
     * @return the X coordinate
     */
    public int getX() {
        return x;
    }

    /**
     * Gets the chunk Z coordinate.
     *
     * @return the Z coordinate
     */
    public int getZ() {
        return z;
    }

    /**
     * Gets the sections in this chunk.
     *
     * @return the list of section data, sorted by Y
     */
    public List<SectionData> getSections() {
        return sections;
    }

    /**
     * Gets the biome array for this chunk.
     *
     * @return the list of biome identifiers (625 entries for 16x16x64)
     */
    public List<String> getBiomes() {
        return biomes;
    }

    /**
     * Gets the heightmap data.
     *
     * @return a map of heightmap names to their byte data
     */
    public Map<String, byte[]> getHeightmaps() {
        return Map.copyOf(heightmaps);
    }

    /**
     * Gets the block entities (chests, signs, droppers, etc.).
     *
     * @return a map of block entity positions to their data
     */
    public Map<String, Object> getBlockEntities() {
        return Map.copyOf(blockEntities);
    }

    /**
     * Gets the data version.
     *
     * @return the Minecraft data version integer
     */
    public int getDataVersion() {
        return dataVersion;
    }

    /**
     * Gets the dimension this chunk belongs to.
     *
     * @return the dimension identifier (e.g., "overworld", "minecraft:the_nether")
     */
    public String getDimension() {
        return dimension;
    }

    /**
     * Gets additional metadata for this chunk.
     *
     * @return a map of custom metadata fields
     */
    public Map<String, Object> getAdditionalData() {
        return Map.copyOf(additionalData);
    }

    /**
     * Checks if this chunk is fully air (empty).
     *
     * @return true if all sections and blocks are air
     */
    public boolean isEmpty() {
        return sections.stream().allMatch(SectionData::isEmpty);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkData that)) return false;
        return x == that.x && z == that.z && sections.equals(that.sections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z, sections);
    }

    @Override
    public int compareTo(ChunkData other) {
        int zCompare = Integer.compare(this.z, other.z);
        if (zCompare != 0) return zCompare;
        return Integer.compare(this.x, other.x);
    }

    /**
     * Returns a string representation suitable for debugging.
     *
     * @return string description of the chunk
     */
    @Override
    public String toString() {
        return "ChunkData{x=" + x + ", z=" + z + ", sections=" + sections.size() + ", biomes=" + biomes.size() + "}";
    }
}
