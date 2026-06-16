package co.partygame.core.game.entity;

import java.util.Map;

/**
 * Represents a spawn point or spawnable entity for a game session.
 */
public class SpawnPoint {
    private final String name;
    private final int x, y, z;
    private final int dimension;
    private final Map<String, Object> spawnConfig;

    public SpawnPoint(String name, int x, int y, int z, int dimension, Map<String, Object> spawnConfig) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.spawnConfig = spawnConfig;
    }

    public String getName() { return name; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getDimension() { return dimension; }
    public Map<String, Object> getSpawnConfig() { return spawnConfig; }
}
