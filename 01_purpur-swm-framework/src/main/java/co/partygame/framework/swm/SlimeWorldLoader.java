package co.partygame.framework.swm;

import co.partygame.framework.SwmPurpurPlugin;

import java.io.*;
import java.nio.file.*;
import java.util.logging.Level;

/**
 * Handles loading and saving of .slimeworld files.
 * <p>
 * This class provides a clean abstraction over world file I/O. The actual conversion
 * of slime data to Bukkit worlds is handled by {@link SwmWorldManager} which has
 * access to the SlimeWorldManager plugin API.
 */
public class SlimeWorldLoader {

    private final SwmPurpurPlugin plugin;
    private final Path worldsDirectory;

    public SlimeWorldLoader(SwmPurpurPlugin plugin, Path worldsDirectory) {
        this.plugin = plugin;
        this.worldsDirectory = worldsDirectory;
    }

    /**
     * Get the path to a .slimeworld file by world name.
     *
     * @param worldName the world name without extension
     * @return the file path
     */
    public Path getWorldFilePath(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            throw new IllegalArgumentException("worldName cannot be null or empty");
        }
        // Sanitize to prevent path traversal
        String safeName = worldName.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return worldsDirectory.resolve(safeName + ".slimeworld");
    }

    /**
     * Check if a .slimeworld file exists for the given world name.
     *
     * @param worldName the world name without extension
     * @return true if the file exists
     */
    public boolean worldFileExists(String worldName) {
        Path p = getWorldFilePath(worldName);
        return Files.exists(p) && Files.isRegularFile(p);
    }

    /**
     * Check if a world file is valid (non-empty, readable).
     *
     * @param worldName the world name without extension
     * @return true if the file is valid
     */
    public boolean isWorldFileValid(String worldName) {
        if (!worldFileExists(worldName)) {
            return false;
        }
        Path p = getWorldFilePath(worldName);
        try {
            return Files.size(p) > 0 && Files.isReadable(p);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * List all .slimeworld files in the worlds directory, sorted by name.
     *
     * @return list of world names (without .slimeworld extension)
     */
    public java.util.List<String> listWorldFiles() {
        if (!Files.exists(worldsDirectory)) {
            return java.util.Collections.emptyList();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(worldsDirectory, "*.slimeworld")) {
            java.util.List<String> results = new java.util.ArrayList<>();
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (name.endsWith(".slimeworld")) {
                    name = name.substring(0, name.length() - ".slimeworld".length());
                }
                if (!name.isEmpty() && !name.startsWith(".")) {
                    results.add(name);
                }
            }
            results.sort(java.util.Comparator.naturalOrder());
            return results;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to list world files", e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Read raw bytes from a .slimeworld file.
     *
     * @param worldName the world name without extension
     * @return byte array, or null if the file does not exist
     */
    public byte[] readWorldFile(String worldName) {
        Path p = getWorldFilePath(worldName);
        if (!Files.exists(p)) {
            return null;
        }
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read world file: " + worldName, e);
            return null;
        }
    }

    /**
     * Write raw bytes to a .slimeworld file.
     * Atomic write (writes to temp file then renames).
     *
     * @param worldName the world name without extension
     * @param data the byte data to write
     * @return true on success
     */
    public boolean writeWorldFile(String worldName, byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        Path targetPath = getWorldFilePath(worldName);
        try {
            // Create parent directories if needed
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // Atomic write: write to temp then rename
            Path tempPath = Files.createTempFile(parent, worldName + "_", ".slimeworld.tmp");
            try {
                Files.write(tempPath, data);
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (Exception e) {
                Files.deleteIfExists(tempPath);
                throw e;
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write world file: " + worldName, e);
            return false;
        }
    }

    /**
     * Delete a .slimeworld file.
     *
     * @param worldName the world name without extension
     * @return true if the file was deleted
     */
    public boolean deleteWorldFile(String worldName) {
        Path p = getWorldFilePath(worldName);
        if (!Files.exists(p)) {
            return false;
        }
        try {
            Files.delete(p);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete world file: " + worldName, e);
            return false;
        }
    }

    /**
     * Check if a world file is newer than the last-modified time of another world file.
     * Useful for detecting template updates that should propagate to derived worlds.
     *
     * @param newerName the potential newer world name
     * @param olderName the potential older world name
     * @return true if newerName's file was modified after olderName's file
     */
    public boolean isFileNewer(String newerName, String olderName) {
        Path a = getWorldFilePath(newerName);
        Path b = getWorldFilePath(olderName);
        try {
            return Files.getLastModifiedTime(a).toMillis() > Files.getLastModifiedTime(b).toMillis();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get the total free disk space available in the worlds directory's partition.
     *
     * @return size in bytes, or -1 if unavailable
     */
    public long getAvailableDiskSpace() {
        if (!Files.exists(worldsDirectory)) {
            try {
                Files.createDirectories(worldsDirectory);
            } catch (IOException e) {
                return -1;
            }
        }
        var fileStore = getFileSystem();
        if (fileStore == null) return -1;
        try {
            return fileStore.getUsableSpace();
        } catch (IOException e) {
            return -1;
        }
    }

    private java.nio.file.FileStore getFileSystem() {
        try {
            return Files.getFileStore(worldsDirectory);
        } catch (IOException e) {
            return null;
        }
    }
}
