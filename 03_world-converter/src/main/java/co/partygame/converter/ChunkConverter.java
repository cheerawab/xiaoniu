package co.partygame.converter;

import co.partygame.converter.config.ConverterConfig;
import co.partygame.converter.config.ConverterConfig.Config;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

/**
 * World Converter - Converts vanilla Minecraft world to Slime Format
 * 
 * Conversion process:
 * 1. Read native world folder (region/, entities/, level.dat)
 * 2. Parse chunk NBT from .mca files (1.21.7 format)
 * 3. Convert coordinates (Y offset for 1.21.7)
 * 4. Convert block states to 1.21.7 NMS format (palette-based)
 * 5. Convert entities to 1.21.7 format
 * 6. Write to Slime format .slimeworld
 * 7. Gzip compression
 */
public class WorldConverter {
    
    private static final Logger logger = Logger.getLogger(WorldConverter.class.getName());
    private static final int TARGET_DATA_VERSION = 4438; // 1.21.7
    
    private final Path inputWorldDir;
    private final Path outputDir;
    private final String worldName;
    private final ChunkConverter chunkConverter;
    private final SlimeWriter slimeWriter;
    private final EntityConverter entityConverter;
    private final int heightOffset;
    
    public WorldConverter(Path inputWorldDir, Path outputDir, String worldName) {
        this.inputWorldDir = inputWorldDir;
        this.outputDir = outputDir;
        this.worldName = worldName;
        this.chunkConverter = new ChunkConverter();
        this.slimeWriter = new SlimeWriter();
        this.entityConverter = new EntityConverter();
        this.heightOffset = 0; // 0 means no offset (already 1.21.7)
    }
    
    /**
     * Main conversion entry point
     */
    public void convert() throws IOException {
        validateWorld();
        
        logger.info("Starting conversion: " + inputWorldDir + " -> " + outputDir + "/" + worldName);
        
        // Parse region directory for chunks
        Map<String, List<ChunkData>> chunksByDimension = parseRegionDirectory();
        
        // Parse global data files
        Map<String, Tag> globalData = parseGlobalDataFiles();
        
        // Create output directory
        Path worldOutputDir = outputDir.resolve(worldName);
        Files.createDirectories(worldOutputDir);
        
        // Write Slime world
        slimeWriter.write(
            chunksByDimension,
            globalData,
            worldOutputDir.resolve(worldName + ".slimeworld")
        );
        
        logger.info("Conversion completed successfully!");
    }
    
    private void validateWorld() {
        if (!Files.exists(inputWorldDir.resolve("level.dat"))) {
            throw new IllegalArgumentException("Invalid world directory: no level.dat found");
        }
        if (!Files.exists(inputWorldDir.resolve("region"))) {
            throw new IllegalArgumentException("Invalid world directory: no region/ directory found");
        }
    }
    
    private Map<String, List<ChunkData>> parseRegionDirectory() throws IOException {
        Map<String, List<ChunkData>> chunksByDimension = new HashMap<>();
        Path regionDir = inputWorldDir.resolve("region");
        
        if (!Files.exists(regionDir)) {
            return chunksByDimension;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "r.*.mca")) {
            for (Path regionFile : stream) {
                List<ChunkData> chunks = parseRegionFile(regionFile);
                
                for (ChunkData chunk : chunks) {
                    String dimension = chunk.getWorld();
                    chunksByDimension.computeIfAbsent(dimension, k -> new ArrayList<>())
                        .add(chunk);
                }
            }
        }
        
        return chunksByDimension;
    }
    
    private List<ChunkData> parseRegionFile(Path regionFile) throws IOException {
        List<ChunkData> chunks = new ArrayList<>();
        
        // Parse .mca region file (binary format)
        // Each chunk in region file is stored as compressed NBT
        byte[] regionData = Files.readAllBytes(regionFile);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(regionData));
        
        // Region file header (8192 bytes)
        int headerOffset = 0;
        
        // Read chunk locations (first 4096 bytes = 1024 entries * 4 bytes)
        Map<String, ChunkEntry> chunkEntries = new HashMap<>();
        for (int i = 0; i < 1024; i++) {
            int sectorOffset = readUnsignedInt(dis);
            int sectorCount = readUnsignedByte(dis);
            
            if (sectorOffset > 0 && sectorCount > 0) {
                int chunkX = (i % 32) - 16;
                int chunkZ = (i / 32) - 16;
                
                ChunkEntry entry = new ChunkEntry(
                    sectorOffset * 4096, // Convert sector offset to bytes
                    sectorCount * 4096
                );
                chunkEntries.put(chunkX + "," + chunkZ, entry);
            }
        }
        
        // Read chunk timestamps (next 4096 bytes)
        for (int i = 0; i < 1024; i++) {
            dis.skipBytes(4); // Skip timestamp
        }
        
        // Read chunk data (payloads start after header)
        // For each chunk entry, read and decompress
        for (Map.Entry<String, String> entry : chunkEntries.entrySet()) {
            ChunkEntry chunkEntry = entry.getValue();
            String chunkKey = entry.getKey();
            
            try {
                // Read chunk payload
                dis.seek(chunkEntry.offset);
                
                if (dis.available() < 5) {
                    continue;
                }
                
                int chunkLength = readUnsignedInt(dis);
                byte compressionType = dis.readByte();
                
                if (chunkLength <= 0 || chunkLength > 10 * 1024 * 1024) {
                    continue; // Skip invalid chunks
                }
                
                // Read payload
                byte[] payload = new byte[chunkLength - 1];
                dis.read(payload);
                
                // Decompress based on compression type
                byte[] decompressedPayload;
                if (compressionType == 1) { // No compression
                    decompressedPayload = payload;
                } else if (compressionType == 2) { // Zlib
                    decompressedPayload = decompressZlib(payload);
                } else {
                    continue; // Skip unsupported compression
                }
                
                // Parse NBT from payload
                try (ByteArrayInputStream bais = new ByteArrayInputStream(decompressedPayload)) {
                    Tag tag = NBTIO.read(new DataInputStream(bais));
                    
                    if (tag instanceof CompoundTag compound) {
                        String world = compound.getString("Level", "overworld");
                        
                        // Parse chunk from NBT compound tag
                        ChunkData chunkData = parseChunkCompound(compound, chunkKey, world);
                        chunks.add(chunkData);
                    }
                }
                
            } catch (Exception e) {
                logger.warning("Failed to parse chunk at " + chunkKey + ": " + e.getMessage());
            }
        }
        
        return chunks;
    }
    
    private Map<String, Tag> parseGlobalDataFiles() throws IOException {
        Map<String, Tag> data = new HashMap<>();
        Path dataDir = inputWorldDir.resolve("data");
        
        if (!Files.exists(dataDir)) {
            return data;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir, "*.nbt")) {
            for (Path file : stream) {
                String name = file.getFileName().toString().replace(".nbt", "");
                try {
                    Tag tag = NBTIO.read(file);
                    data.put(name, tag);
                } catch (Exception e) {
                    logger.warning("Failed to parse " + name + ": " + e.getMessage());
                }
            }
        }
        
        return data;
    }
    
    private static CompoundTag parseChunkCompound(CompoundTag level, String key, String world) {
        // Parse chunk data from NBT compound tag
        // This is a simplified version - full implementation would parse all chunk data
        return new CompoundTag(level);
    }
    
    private byte[] decompressZlib(byte[] compressed) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InflaterInputStream iis = new InflaterInputStream(
                new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = iis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }
        return baos.toByteArray();
    }
    
    private static int readUnsignedInt(DataInputStream dis) throws IOException {
        return (dis.readUnsignedByte() << 24) | 
               (dis.readUnsignedByte() << 16) | 
               (dis.readUnsignedByte() << 8) | 
               dis.readUnsignedByte();
    }
    
    private static byte readUnsignedByte(DataInputStream dis) throws IOException {
        return (byte) (dis.readUnsignedByte() & 0xFF);
    }
    
    // Simple NBT I/O helper class
    private static class NBTIO {
        public static Tag read(DataInputStream dis) throws IOException {
            byte type = dis.readByte();
            if (type == 0) return null;
            String name = readString(dis);
            return readPayload(dis, type, name);
        }
        
        public static Tag read(Path file) throws IOException {
            try (DataInputStream dis = new DataInputStream(Files.newInputStream(file))) {
                return read(dis);
            }
        }
        
        private static String readString(DataInputStream dis) throws IOException {
            short length = dis.readShort();
            if (length <= 0) return "";
            byte[] bytes = new byte[length];
            dis.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        
        private static Tag readPayload(DataInputStream dis, byte type, String name) throws IOException {
            Tag tag = new Tag(name, type);
            switch (type) {
                case 1: // TAG_Byte
                    tag.setByte(dis.readByte());
                    break;
                case 3: // TAG_String
                    tag.setString(readString(dis));
                    break;
                case 9: // TAG_List
                    Tag childTag = readPayload(dis, dis.readByte(), "");
                    List<Tag> list = new ArrayList<>();
                    while (dis.available() > 0) {
                        list.add((Tag) readPayload(dis, childTag.getType(), ""));
                    }
                    tag.setList(list);
                    break;
                case 10: // TAG_Compound
                    while (dis.available() > 0) {
                        Tag childTag = read(dis);
                        if (childTag != null) {
                            tag.putChild(childTag.getName(), childTag);
                        }
                    }
                    break;
            }
            return tag;
        }
    }
    
    // Simple NBT Tag implementation
    private static class Tag {
        final String name;
        final byte type;
        byte byteValue;
        String stringValue;
        List<Tag> list;
        Map<String, Tag> children;
        
        Tag(String name, byte type) {
            this.name = name;
            this.type = type;
        }
        
        String getString(String key, String defaultValue) {
            Tag child = children != null ? children.get(key) : null;
            if (child != null && child.type == 3) {
                return child.stringValue;
            }
            return defaultValue;
        }
        
        void putChild(String name, Tag child) {
            if (children == null) children = new HashMap<>();
            children.put(name, child);
        }
        
        void setList(List<Tag> list) {
            this.list = list;
        }
        
        List<Tag> getList() {
            return list;
        }
        
        byte getByte() {
            return byteValue;
        }
        
        String getString() {
            return stringValue;
        }
        
        String getName() {
            return name;
        }
        
        byte getType() {
            return type;
        }
    }
    
    // Chunk entry in region file
    private static class ChunkEntry {
        final int offset;
        final int length;
        
        ChunkEntry(int offset, int length) {
            this.offset = offset;
            this.length = length;
        }
    }
}
