package co.partygame.converter;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an entity within a Minecraft world.
 *
 * <p>Entities include mobs, items, animals, players, and special
 * entities like armor stands. In Minecraft 1.17+, entity data is
 * stored separately in the entities/ directory.</p>
 *
 * @since 1.0.0
 */
public class EntityData implements Comparable<EntityData> {

    private final String type;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final Map<String, Object> data;
    private final UUID uuid;
    private final int dimension;

    /**
     * Creates a new EntityData.
     *
     * @param type      the entity type (e.g., "minecraft:cow", "minecraft:item")
     * @param x         the X coordinate
     * @param y         the Y coordinate
     * @param z         the Z coordinate
     * @param yaw       the yaw rotation
     * @param pitch     the pitch rotation
     * @param data      the entity's NBT data (health, equipment, etc.)
     * @param uuid      the entity's UUID
     * @param dimension the dimension this entity is in (0=overworld, 1=nether, 2=end)
     */
    public EntityData(
            String type,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            Map<String, Object> data,
            UUID uuid,
            int dimension
    ) {
        this.type = Objects.requireNonNull(type, "Entity type cannot be null");
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.data = Objects.requireNonNullElse(data, Map.of());
        this.uuid = uuid;
        this.dimension = dimension;
    }

    /**
     * Creates a basic EntityData with default position and rotation.
     *
     * @param type the entity type
     */
    public EntityData(String type) {
        this(type, 0.0, 0.0, 0.0, 0.0f, 0.0f, Map.of(), null, 0);
    }

    /**
     * Gets the entity type.
     *
     * @return the entity type identifier (e.g., "minecraft:pig")
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the X coordinate.
     *
     * @return X position
     */
    public double getX() {
        return x;
    }

    /**
     * Gets the Y coordinate.
     *
     * @return Y position
     */
    public double getY() {
        return y;
    }

    /**
     * Gets the Z coordinate.
     *
     * @return Z position
     */
    public double getZ() {
        return z;
    }

    /**
     * Gets the yaw rotation (0-360, clockwise when looking down).
     *
     * @return yaw in degrees
     */
    public float getYaw() {
        return yaw;
    }

    /**
     * Gets the pitch rotation (-90 Looking up to 90 looking down).
     *
     * @return pitch in degrees
     */
    public float getPitch() {
        return pitch;
    }

    /**
     * Gets the entity's NBT data map.
     *
     * @return the data map containing fields like Health, CustomName, etc.
     */
    public Map<String, Object> getData() {
        return Map.copyOf(data);
    }

    /**
     * Gets the entity's UUID.
     *
     * @return the UUID, or null if not present
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the dimension index.
     *
     * @return 0=overworld, 1=nether, 2=the_end
     */
    public int getDimension() {
        return dimension;
    }

    /**
     * Gets the chunk X coordinate this entity is in.
     *
     * @return chunk X coordinate
     */
    public int getChunkX() {
        return (int) Math.floor(x / 16.0);
    }

    /**
     * Gets the chunk Z coordinate this entity is in.
     *
     * @return chunk Z coordinate
     */
    public int getChunkZ() {
        return (int) Math.floor(z / 16.0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntityData that)) return true;
        if (uuid != null && that.uuid != null) return uuid.equals(that.uuid);
        return type.equals(that.type) && Double.compare(that.x, x) == 0 && Double.compare(that.y, y) == 0 && Double.compare(that.z, z) == 0;
    }

    @Override
    public int hashCode() {
        if (uuid != null) return Objects.hash(uuid);
        return Objects.hash(type, x, y, z);
    }

    @Override
    public int compareTo(EntityData other) {
        return Long.compare(Long.doubleToLongBits(this.x), Long.doubleToLongBits(other.x));
    }

    @Override
    public String toString() {
        return "EntityData{type='" + type + "', pos=(" + x + ", " + y + ", " + z + "), dim=" + dimension + "}";
    }
}
