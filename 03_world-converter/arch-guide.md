# 世界轉換工具架構指南 (World Converter Guide)

## 1. 工具定位

世界轉換工具用於將 Minecraft 原生 world 資料夾轉換為 .slimeworld 檔案。

```
Input:
└── world/           # 原生 world 資料夾
    ├── region/      # 原生 world 轉換工具結構 (nbt/region/ 資料夾 → Slime 格式 (slime/ 資料夾)
Output:
└── world.slimeworld  # Slime 格式 (nbt/region/ .slimeworld)
```

## 2. 轉換流程

```
1. 讀取原生 world 資料夾
   ├── region/   → 解析 .mca 檔案 → 讀取 chunk NBT
   ├── entities/ → 讀取 .dat 檔案 → 讀取 entity NBT
   ├── data/     → 讀取 global data NBT
   └── level.dat → 讀取 world 元數據

2. 轉換格式
   ├── chunk → 1.21.7 NMS chunk format → Slime chunk format
   ├── entity → 1.21.7 NMS entity format → Slime entity format
   ├── global data → 1.21.7 NMS global data → Slime global data
   └── world metadata → 1.21.7 NMS world metadata → Slime world metadata

3. 寫入 Slime 格式 (slime/資料夾)
   └── world.slimeworld  → NBT format → gzip 壓縮

4. 壓縮輸出
   └── world.slimeworld → gzip 壓縮 → world.slimeworld
```

## 3. 主要類別 (Classes)

### 3.1 ConverterMain.java

```java
public class ConverterMain {
    public static void main(String[] args) {
        // 解析命令行參數
        String inputDir = null;
        String outputDir = null;
        String worldName = null;
        
        for (String arg : args) {
            if (arg.startsWith("--input=")) {
                inputDir = arg.substring("--input-".length());
            }
            // ... 其他參數
        }
        
        // 創建轉換器
        WorldConverter converter = new WorldConverter();
        
        // 執行轉換
        converter.convert(inputDir, outputDir, worldName);
    }
}
```

### 3.2 WorldReader.java

```java
/**
 * 讀取原生 world 資料夾。
 */
public class WorldReader {
    private final Path worldPath;
    
    /**
     * 讀取原生 world 資料夾
     */
    public WorldData read(Path worldPath) {
        this.worldPath = worldPath;
        
        WorldData data = new WorldData();
        
        // 讀取 level.dat
        data.setGlobalData(readLevelDat());
        
        // 讀取 region/
        data.setChunks(readChunks());
        
        // 讀取 entities/
        data.setEntities(readEntities());
        
        // 讀取 data/
        data.setDataFiles(readDataFiles());
        
        return data;
    }
    
    /**
     * 讀取 .mca 檔案中的 chunk NBT
     */
    private List<ChunkData> readChunks() {
        List<ChunkData> chunks = new ArrayList<>();
        
        Path regionDir = worldPath.resolve("region");
        for (Path regionFile : regionDir.toFile()) {
            if (regionFile.getName().endsWith(".mca")) {
                ReadRegionFile(regionFile, chunks);
            }
        }
        
        return chunks;
        }
    }
```

### 3.3 ChunkConverter.java

```java
/**
 * 轉換 chunk 格式。
 * 處理 1.21.7 NMS chunk 格式 → Slime chunk 格式
 */
public class ChunkConverter {
    
    /**
     * 轉換 chunk 格式
     */
    public SlimeChunkData convert(ChunkData chunk) {
        SlimeChunkData slimeChunk = new SlimeChunkData();
        
        // 轉換座標 (1.21.7 座標)
        slimeChunk.setX(chunk.getX());
        slimeChunk.setZ(chunk.getZ());
        
        // 轉換 sections (1.21.7 的 section 格式)
        List<SectionData> sections = new ArrayList<>();
        for (SectionData section : chunk.getSections()) {
            SectionData slimeSection = new SectionData();
            slimeSection.setY(section.getY());
            slimeSection.setBlockStates(convertBlockStates(section.getBlockStates()));
            slimeSection.setBiomes(convertBiomes(section.getBiomes()));
            slimeSection.setEntities(section.getEntities());
            slimeSection.setLight(section.getLight());
            sections.add(slimeSection);
        }
        slimeChunk.setSections(sections);
        
        // 轉換 heightmaps (1.21.7 高度圖格式)
        slimeChunk.setHeightmaps(convertHeightmaps(chunk.getHeightmaps()));
        
        // 轉換 block_entities
        slimeChunk.setBlockEntities(chunk.getBlockEntities());
        
        // 轉換 data-version
        slimeChunk.setDataVersion(chunk.getDataVersion());
        
        return slimeChunk;
    }
    
    /**
     * 轉換 blockstates。
     * 處理 1.21.7 的 palette 格式
     */
    private List<BlockState> convertBlockStates(List<BlockState> blockStates) {
        // 1.21.7 使用 palette-based block states
        // 每個 section 有 palette 和 data
        // palette: [minecraft:stone, minecraft:air, ...]
        // data: [0, 1, 0, 1, ...] (palette index)
        List<BlockState> slimeBlockStates = new ArrayList<>();
        for (BlockState state : blockStates) {
            BlockState slimeState = new BlockState();
            slimeState.setName(state.getName());
            slimeState.setProperties(state.getProperties());
            slimeBlockStates.add(slimeState);
        }
        return slimeBlockStates;
    }
    
    /**
     * 轉換 biomes。
     * 處理 1.21.7 的 per-section biome 格式
     */
    private List<String> convertBiomes(List<String> biomes) {
        // 1.21.7 使用 per-section biome 格式
        // 每個 section 有自己的 biome palette
        return biomes.stream()
            .map(b -> b.replace("minecraft:", ""))  // 去掉 minecraft: prefix
            .collect(Collectors.toList());
    }
    
    /**
     * 轉換 heightmaps。
     * 處理 1.21.7 的 heightmap 格式
     */
    private Map<String, LongArray> convertHeightmaps(Map<String, LongArray> heightmaps) {
        // 1.21.7 使用 9-bit signed 格式 (up to 384 values)
        Map<String, LongArray> slimeHeightmaps = new HashMap<>();
        for (Map.Entry<String, LongArray> entry : heightmaps.entrySet()) {
            slimeHeightmaps.put(entry.getKey(), entry.getValue());
        }
        return slimeHeightmaps;
    }
}
```

### 3.4 SlimeWriter.java

```java
/**
 * 寫入 .slimeworld 檔案。
 */
public class SlimeWriter {
    
    /**
     * 寫入 .slimeworld 檔案。
     */
    public void write(SlimeWorldData slimeWorld, Path output) throws IOException {
        // 創建 NBT compound
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("DataVersion", 4438);  // 1.21.7 data version
        
        // 寫入 world metadata
        nbt.put("world", (CompoundTag) slimeWorld.getWorldMetadata().toNBT());
        
        // 寫入 dimensions
        CompoundTag dimensions = new CompoundTag();
        for (Map.Entry<String, List<SlimeChunkData>> entry : 
             slimeWorld.getChunksByDimension().entrySet()) {
            List<CompoundTag> chunkList = new ArrayList<>();
            for (SlimeChunkData chunk : entry.getValue()) {
                chunkList.add(chunk.toNBT());
            }
            dimensions.put(entry.getKey(), chunkList);
        }
        nbt.put("dimensions", dimensions);
        
        // gzip 壓縮
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            nbt.write(gzos);
        }
        
        // 寫入檔案
        Files.write(output, baos.toByteArray());
    }
}
```

### 3.5 EntityConverter.java

```java
/**
 * 轉換 entity 格式。
 * 處理 1.21.7 NMS entity 格式 → Slime entity 格式
 */
public class EntityConverter {
    
    /**
     * 轉換 entity 格式
     */
    public SlimeEntityData convert(EntityData entity) {
        SlimeEntityData slimeEntity = new SlimeEntityData();
        
        // 轉換座標
        slimeEntity.setX(entity.getX());
        slimeEntity.setY(entity.getY());
        slimeEntity.setZ(entity.getZ());
        
        // 轉換 type
        slimeEntity.setType(entity.getType());
        
        // 轉換 NBT 數據
        slimeEntity.setNbt(entity.getNbtData());
        
        return slimeEntity;
    }
}

```

### 3.6 ConverterConfig.java

```java
public class ConverterConfig {
    public static final int TARGET_DATA_VERSION = 4438;  // 1.21.7
    
    private Path input;
    private Path output;
    private String worldName = "world";
    
    // Constructors, getters, setters
    public ConverterConfig(Path input, Path output, String worldName) {
        this.input = input;
        this.output = output;
        this.worldName = worldName;
    }
    
    public Path getInput() {
        return input;
    }
    
    public Path getOutput() {
        return output;
    }
    
    public String getWorldName() {
        return worldName;
    }
}
```

## 4. 使用方式

### 4.1 CLI 使用

```bash
# 基本用法
java -jar world-converter.jar \
  --input ./world \
  --output ./out \
  --name myworld

# 使用自訂 data version
java -jar world-converter.jar \
  --input ./world \
  --output ./out \
  --name myworld \
  --data-version 4438
```

### 4.2 API 使用

```java
// Java API
public class MyConverter {
    public void convert() {
        ConverterConfig config = new ConverterConfig(
            Paths.get("./world"),
            Paths.get("./out"),
            "myworld"
        );
        
        WorldConverter converter = new WorldConverter();
        converter.convert(config);
    }
}
```

## 5. 注意事項

### 5.1 Data Version

- 1.21.7 data version 是 4438
- 轉換時需要確保 world 的 data version 是 4438 (如果是舊版本，需要升級)

### 5.2 Height Offset

- 1.18+ 的 height offset 是 -64
- 轉換時需要將 Y 座標增加 64 (從 0 開始)

### 5.3 Block State

- 1.21.7 使用 palette 格式
- 轉換時需要處理 palette 格式

### 5.4 Biome

- 1.21.7 使用 per-section biome 格式
- 轉換時需要處理 per-section 配置

### 5.5 Entity

- 1.17+ entity 存放在 entity/ 目錄
- 轉換時需要處理 entity/ 目錄 (讀取 .dat 檔案)

## 6. 轉換流程

```
world/
├── region/ → 讀取 .mca 檔案
│   ├── r.0.0.mca → 解析 NBT → chunk data
│   └── r.0.1.mca → 解析 NBT → chunk data
├── entities/ → 讀取 .dat 檔案
│   ├── .dat → 解析 NBT → entity data
│   └── ... → parse NBT → entity data
├── data/ → 讀取 global data NBT
└── global data → parse NBT → global data
├── level.dat → 讀取 world 元數據
├── world metadata → 轉換格式 (1.21.7 NMS → slime format)
└── world metadata → 轉換格式 (1.21.7 NMS → slime format)

output:
└── world slimeworld → (NBT format → gzip compression)
