package co.partygame.converter;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a block state within a Minecraft chunk.
 *
 * <p>Stores the block identifier and its associated properties
 * as defined by the chunk's palette system.</p>
 *
 * @since 1.0.0
 */
public class BlockState implements Comparable<BlockState> {

    private final String name;
    private final Map<String, String> properties;
    private final int paletteIndex;

    /**
     * Creates a new BlockState with the given name and properties.
     *
     * @param name         the block identifier (e.g., "stone", "dirt")
     * @param properties   the block state properties (e.g., "age=2", "facing=north")
     */
    public BlockState(String name, Map<String, String> properties) {
        this.name = Objects.requireNonNull(name, "Block name cannot be null");
        this.properties = Objects.requireNonNullElse(properties, Map.of());
        this.paletteIndex = -1;
    }

    /**
     * Creates a new BlockState with a palette index.
     *
     * @param name         the block identifier
     * @param properties   the block state properties
     * @param paletteIndex the index in the chunk palette
     */
    public BlockState(String name, Map<String, String> properties, int paletteIndex) {
        this.name = Objects.requireNonNull(name, "Block name cannot be null");
        this.properties = Objects.requireNonNullElse(properties, Map.of());
        this.paletteIndex = paletteIndex;
    }

    /**
     * Gets the block identifier.
     *
     * @return the block name without namespace prefix
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the block state properties.
     *
     * @return an unmodifiable map of property names to values
     */
    public Map<String, String> getProperties() {
        return Map.copyOf(properties);
    }

    /**
     * Gets the palette index for this block state, or -1 if not indexed.
     *
     * @return the palette index
     */
    public int getPaletteIndex() {
        return paletteIndex;
    }

    /**
     * Checks if this block state represents an air block.
     *
     * @return true if the block is air
     */
    public boolean isAir() {
        return name.equals("air");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockState that)) return false;
        return name.equals(that.name) && properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, properties);
    }

    @Override
    public int compareTo(BlockState other) {
        int nameCompare = this.name.compareTo(other.name);
        if (nameCompare != 0) return nameCompare;
        return Integer.compare(this.paletteIndex, other.paletteIndex);
    }

    @Override
    public String toString() {
        if (properties.isEmpty()) {
            return "BlockState{name='" + name + "'}";
        }
        return "BlockState{name='" + name + "', properties=" + properties + "}";
    }

    /**
     * Parses a block state from NBT tag string format.
     *
     * <p>Expected format: "minecraft:stone{waterlogged=true}" or "stone{age=2}"</p>
     *
     * @param tagString the NBT string representation
     * @return a new BlockState instance
     */
    public static BlockState fromString(String tagString) {
        Objects.requireNonNull(tagString, "Tag string cannot be null");

        String name;
        Map<String, String> properties = Map.of();
        int colonIndex = tagString.indexOf('{');

        if (colonIndex > 0) {
            name = tagString.substring(0, colonIndex).replace("minecraft:", "");
            String propsString = tagString.substring(colonIndex + 1, tagString.length() - 1);
            properties = parseProperties(propsString);
        } else {
            name = tagString.replace("minecraft:", "");
        }

        return new BlockState(name, properties);
    }

    private static Map<String, String> parseProperties(String propsString) {
        Map<String, String> map = Map.of();
        if (propsString.isBlank()) return map;
        // Simple parsing for basic properties
        // Full implementation would use NBT string parser
        return Map.of();
    }

    /**
     * Creates a BlockState from a long-encoded chunk data format.
     *
     * <p>Used for 1.17+ palette-based block encoding where blocks
     * are stored as packed longs in data arrays.</p>
     *
     * @param palette    the chunk palette
     * @param dataArray  the packed block data
     * @param bitSplits  the bit width and offset splits for packing
     * @return the decoded block state list
     */
    @SuppressWarnings("unchecked")
    public static java.util.List<BlockState> decodeBlockStates(
            java.util.List<Map<String, Object>> palette,
            long[] dataArray,
            Map<String, Object> bitSplits
    ) {
        java.util.List<BlockState> results = new java.util.ArrayList<>();
        if (palette == null || palette.isEmpty()) return results;

        int bitsPerBlock = (int) ((bitSplits != null ? bitSplits.get("bits") : 8));
        if (bitsPerBlock <= 0) bitsPerBlock = 8;

        int longArrayLength = (int) bitSplits.size();
        for (int blockIndex = 0; blockIndex < 4096; blockIndex++) {
            int indexInLongArray = blockIndex / (64 / bitsPerBlock);
            int bitOffset = blockIndex * bitsPerBlock % 64;
            int endIndex = bitOffset + bitsPerBlock;

            long val;
            if (endIndex <= 64 && indexInLongArray < dataArray.length) {
                val = dataArray[indexInLongArray] >>> bitOffset;
            } else {
                int lowerBits = 64 - bitOffset;
                long lower = indexInLongArray < dataArray.length ? dataArray[indexInLongArray] : 0;
                long upper = (indexInLongArray + 1) < dataArray.length ? dataArray[indexInLongArray + 1] : 0;
                val = (upper << lowerBits) | (lower >>> bitOffset);
            }

            int paletteIndex = (int) (val & ((1L << bitsPerBlock) - 1));
            if (paletteIndex >= 0 && paletteIndex < palette.size()) {
                Map<String, Object> entry = palette.get(paletteIndex);
                String blockName = (String) entry.get("Name");
                var blockProps = (Map<String, String>) entry.get("Properties");
                if (blockName != null) {
                    results.add(new BlockState(blockName, blockProps));
                } else {
                    results.add(new BlockState("air", Map.of()));
                }
            } else {
                results.add(new BlockState("air", Map.of()));
            }
        }
        return results;
    }
}
