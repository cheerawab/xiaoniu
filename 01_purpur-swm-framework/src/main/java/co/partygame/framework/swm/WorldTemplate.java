package co.partygame.framework.swm;

/**
 * Represents a world template that can be used as a blueprint for creating game worlds.
 * <p>
 * World templates store the fundamental configuration (environment, type, seed, generator)
 * needed to bootstrap new game instances without loading full .slimeworld files each time.
 */
public final class WorldTemplate {

    private final String name;
    private final org.bukkit.WorldType worldType;
    private final org.bukkit.World.Environment environment;
    private final Long seed;
    private final String generator;
    private final int buildHeight;
    private final boolean bedSpawnLocked;
    private final boolean allowFlight;
    private final org.bukkit.Difficulty difficulty;
    private final boolean pvp;

    private WorldTemplate(Builder builder) {
        this.name = builder.name;
        this.worldType = builder.worldType;
        this.environment = builder.environment;
        this.seed = builder.seed;
        this.generator = builder.generator;
        this.buildHeight = builder.buildHeight;
        this.bedSpawnLocked = builder.bedSpawnLocked;
        this.allowFlight = builder.allowFlight;
        this.difficulty = builder.difficulty;
        this.pvp = builder.pvp;
    }

    /**
     * Creates a new template builder.
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Creates a simple template with given environment and type.
     */
    public static WorldTemplate of(String name, org.bukkit.WorldEnvironment environment, org.bukkit.WorldType type) {
        return builder(name)
                .environment(environment)
                .worldType(type)
                .build();
    }

    public String getName() {
        return name;
    }

    public org.bukkit.WorldType getWorldType() {
        return worldType;
    }

    public org.bukkit.World.Environment getEnvironment() {
        return environment;
    }

    public java.util.Optional<Long> getSeed() {
        return java.util.Optional.ofNullable(seed);
    }

    public String getGenerator() {
        return generator;
    }

    public int getBuildHeight() {
        return buildHeight;
    }

    public boolean isBedSpawnLocked() {
        return bedSpawnLocked;
    }

    public boolean isAllowFlight() {
        return allowFlight;
    }

    public org.bukkit.Difficulty getDifficulty() {
        return difficulty;
    }

    public boolean isPvp() {
        return pvp;
    }

    @Override
    public String toString() {
        return "WorldTemplate{" +
                "name='" + name + '\'' +
                ", type=" + worldType +
                ", env=" + environment +
                ", seed=" + seed +
                '}';
    }

    // ===================== Builder =====================

    public static class Builder {
        private final String name;
        private org.bukkit.WorldType worldType = org.bukkit.WorldType.NORMAL;
        private org.bukkit.World.Environment environment = org.bukkit.World.Environment.NORMAL;
        private Long seed = null;
        private String generator = null;
        private int buildHeight = 256;
        private boolean bedSpawnLocked = false;
        private boolean allowFlight = false;
        private org.bukkit.Difficulty difficulty = org.bukkit.Difficulty.NORMAL;
        private boolean pvp = true;

        private Builder(String name) {
            this.name = name;
        }

        public Builder worldType(org.bukkit.WorldType worldType) {
            this.worldType = worldType;
            return this;
        }

        public Builder environment(org.bukkit.World.Environment environment) {
            this.environment = environment;
            return this;
        }

        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        public Builder generator(String generator) {
            this.generator = generator;
            return this;
        }

        public Builder buildHeight(int buildHeight) {
            this.buildHeight = buildHeight;
            return this;
        }

        public Builder bedSpawnLocked(boolean bedSpawnLocked) {
            this.bedSpawnLocked = bedSpawnLocked;
            return this;
        }

        public Builder allowFlight(boolean allowFlight) {
            this.allowFlight = allowFlight;
            return this;
        }

        public Builder difficulty(org.bukkit.Difficulty difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public Builder pvp(boolean pvp) {
            this.pvp = pvp;
            return this;
        }

        public WorldTemplate build() {
            return new WorldTemplate(this);
        }
    }
}
