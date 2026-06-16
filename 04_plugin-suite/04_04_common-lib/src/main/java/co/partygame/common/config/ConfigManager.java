package co.partygame.common.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 統一配置文件管理器。
 *
 * 使用 Bukkit 的 YamlConfiguration 加載 YAML 文件，
 * 支持文件修改自動重載（可配置監控間隔）。
 * 支持配置文件段的默認值、描述、註釋管理。
 *
 * 使用示例：
 * <pre>{@code
 * ConfigManager config = new ConfigManager(plugin, "config.yml");
 * config.loadConfig();
 *
 * String welcomeMessage = config.getString("messages.welcome", "&aWelcome!");
 * config.setValue("messages.welcome", "&bGoodbye!");
 * config.saveConfig();
 *
 * // 支持自動重載 (每 10 秒檢查一次)
 * config.setWatchInterval(10, TimeUnit.SECONDS);
 * }</pre>
 */
public class ConfigManager {

    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());

    private final File file;
    private FileConfiguration config;
    private final String fileName;
    private ScheduledExecutorService watchService;
    private volatile boolean watchEnabled = false;
    private volatile long lastModified = 0L;
    private volatile long lastLength = 0L;

    /**
     * 創建配置文件管理器。
     *
     * @param plugin 插件實例，用於獲取數據文件夹
     * @param fileName 文件名（例如 "config.yml"）
     */
    public ConfigManager(org.bukkit.plugin.Plugin plugin, String fileName) {
        Objects.requireNonNull(plugin, "plugin must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");
        this.fileName = fileName;
        this.file = new File(plugin.getDataFolder(), fileName);
    }

    /**
     * 創建配置文件管理器（自定義文件路徑）。
     *
     * @param filePath 文件完整路徑
     * @param fileName 文件名
     */
    public ConfigManager(String filePath, String fileName) {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");
        this.file = new File(filePath, fileName);
        this.fileName = fileName;
    }

    /**
     * 加載配置文件。
     * 如果文件不存在會自動創建默認文件。
     *
     * @return 此 ConfigManager 實例，便於鏈式調用
     */
    public ConfigManager loadConfig() {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            if (!file.exists()) {
                copyDefaultConfig();
            }
            config = YamlConfiguration.loadConfiguration(file);
            lastModified = file.lastModified();
            lastLength = file.length();
            LOGGER.info("Configuration loaded: " + fileName);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load config: " + fileName, e);
        }
        return this;
    }

    /**
     * 獲取配置文件實例。
     * 如果尚未加載，則先加載。
     *
     * @return FileConfiguration 配置對象
     */
    public FileConfiguration getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }

    /**
     * 保存配置文件到磁盤。
     *
     * @return 是否保存成功
     */
    public boolean saveConfig() {
        if (config == null || file == null) {
            LOGGER.severe("Cannot save config - file not initialized");
            return false;
        }
        try {
            config.save(file);
            lastModified = file.lastModified();
            lastLength = file.length();
            LOGGER.fine("Configuration saved: " + fileName);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save config: " + fileName, e);
            return false;
        }
    }

    /**
     * 重新加載配置文件。
     * 從磁盤重新讀取，丟棄未保存的更改。
     */
    public void reloadConfig() {
        if (config != null) {
            config = YamlConfiguration.loadConfiguration(file);
        }
        lastModified = file.lastModified();
        lastLength = file.length();
        LOGGER.info("Configuration reloaded: " + fileName);
    }

    /**
     * 配置自動重載：監控文件更改並自動重新加載。
     *
     * @param interval 檢測間隔
     * @param unit     時間單位
     */
    public void setWatchInterval(long interval, TimeUnit unit) {
        Objects.requireNonNull(unit, "TimeUnit must not be null");
        watchEnabled = true;
        if (watchService != null) {
            watchService.shutdownNow();
        }
        watchService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Config-Watcher-" + fileName);
            t.setDaemon(true);
            return t;
        });
        long knownMod = file.lastModified();
        long knownLen = file.length();
        watchService.scheduleAtFixedRate(() -> {
            if (!watchEnabled) return;
            try {
                long curMod = file.lastModified();
                long curLen = file.length();
                if (curMod > knownMod || curLen != knownLen) {
                    if (config == null || YamlConfiguration.loadConfiguration(file) != config) {
                        reloadConfig();
                        LOGGER.info("Auto-reload triggered for: " + fileName);
                    }
                }
                knownMod = curMod;
                knownLen = curLen;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Config watch failed for: " + fileName, e);
            }
        }, interval, interval, unit);
    }

    /**
     * 停止配置文件監控。
     */
    public void stopWatch() {
        watchEnabled = false;
        if (watchService != null) {
            watchService.shutdownNow();
            watchService = null;
        }
    }

    /**
     * 設置配置的默認值並添加描述。
     *
     * @param key     鍵路徑
     * @param value   默認值
     * @param comment 描述（添加為註釋）
     * @param <T>     值類型
     * @return 此 ConfigManager 實例
     */
    public <T> ConfigManager setDefaultValue(String key, T value, String comment) {
        Objects.requireNonNull(key, "key must not be null");
        if (config == null) loadConfig();
        if (!config.contains(key)) {
            config.set(key, value);
        }
        if (comment != null && !comment.isEmpty()) {
            setComments(key, comment);
        }
        return this;
    }

    /**
     * 為配置鍵添加註釋/描述。
     * 註釋添加在鍵的上一行。
     *
     * @param key     配置鍵
     * @param comment 註釋內容（支持多行，用換行符分隔）
     */
    public void setComments(String key, String... comment) {
        if (key == null || comment == null || comment.length == 0) return;
        if (config == null) loadConfig();
        List<String> comments = new ArrayList<>();
        for (String line : comment) {
            if (line != null) {
                comments.add("  #" + line);
            }
        }
        config.set(key + ".@", comments);
    }

    /**
     * 添加配置段分隔註釋。
     *
     * @param key      配置鍵（段開頭）
     * @param section  段的標題
     */
    public void addSection(String key, String section) {
        if (key == null || section == null) return;
        if (config == null) loadConfig();
        List<String> lines = new ArrayList<>();
        lines.add("  # " + section);
        config.set(key + ".@", lines);
    }

    /**
     * 便捷方法：獲取字符串配置。
     *
     * @param key        鍵路徑
     * @param defaultValue 默認值
     * @return 字符串值
     */
    public String getString(String key, String defaultValue) {
        if (config == null) loadConfig();
        return config.getString(key, defaultValue);
    }

    /**
     * 便捷方法：獲取整數配置。
     *
     * @param key        鍵路徑
     * @param defaultValue 默認值
     * @return 整數值
     */
    public int getInt(String key, int defaultValue) {
        if (config == null) loadConfig();
        return config.getInt(key, defaultValue);
    }

    /**
     * 便捷方法：獲取布爾值配置。
     *
     * @param key        鍵路徑
     * @param defaultValue 默認值
     * @return 布爾值
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        if (config == null) loadConfig();
        return config.getBoolean(key, defaultValue);
    }

    /**
     * 便捷方法：獲取雙精度浮點數配置。
     *
     * @param key        鍵路徑
     * @param defaultValue 默認值
     * @return 雙精度值
     */
    public double getDouble(String key, double defaultValue) {
        if (config == null) loadConfig();
        return config.getDouble(key, defaultValue);
    }

    /**
     * 便捷方法：獲取字符串列表配置。
     *
     * @param key        鍵路徑
     * @param defaultValue 默認值
     * @return 字符串列表
     */
    public List<String> getStringList(String key, List<String> defaultValue) {
        if (config == null) loadConfig();
        return config.getStringList(key, defaultValue);
    }

    /**
     * 設置配置值。
     *
     * @param key   鍵路徑
     * @param value 值
     * @param <T>   值類型
     */
    public <T> void setValue(String key, T value) {
        Objects.requireNonNull(key, "key must not be null");
        if (config == null) loadConfig();
        config.set(key, value);
    }

    /**
     * 檢查配置是否包含指定鍵。
     *
     * @param key 鍵路徑
     * @return 如果包含返回 true
     */
    public boolean contains(String key) {
        if (config == null) loadConfig();
        return config.contains(key);
    }

    /**
     * 刪除指定鍵。
     *
     * @param key 鍵路徑
     */
    public void removeValue(String key) {
        if (key == null) return;
        if (config == null) loadConfig();
        config.set(key, null);
    }

    /**
     * 獲取配置的所有鍵。
     *
     * @return 鍵集合
     */
    public Set<String> getKeys(boolean includeDefaults) {
        if (config == null) loadConfig();
        return config.getKeys(includeDefaults);
    }

    /**
     * 獲取 ConfigurationSection 的指定子段。
     *
     * @param key 鍵路徑
     * @return ConfigurationSection，如果不存在則返回 null
     */
    public org.bukkit.configuration.ConfigurationSection getSection(String key) {
        if (config == null) loadConfig();
        return config.getConfigurationSection(key);
    }

    /**
     * 設置子段中的值。
     * 鍵相對於子段進行設置。
     *
     * @param sectionKey 子段鍵
     * @param key        子段內的鍵
     * @param value      值
     */
    public void setInSection(String sectionKey, String key, Object value) {
        Objects.requireNonNull(sectionKey, "sectionKey must not be null");
        Objects.requireNonNull(key, "key must not be null");
        if (config == null) loadConfig();
        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection(sectionKey);
        if (section != null) {
            section.set(key, value);
        } else {
            config.set(sectionKey + "." + key, value);
        }
    }

    /**
     * 獲取配置文件路徑。
     */
    public String getFilePath() {
        return file.getPath();
    }

    /**
     * 獲取配置文件對象。
     */
    public File getFile() {
        return file;
    }

    /**
     * 判斷監控是否啟用。
     */
    public boolean isWatchEnabled() {
        return watchEnabled;
    }

    /**
     * 從插件資源包複製默認配置文件。
     */
    private void copyDefaultConfig() {
        try {
            InputStream defaultsStream = getResourceStream(fileName);
            if (defaultsStream != null) {
                try (InputStream in = defaultsStream;
                     OutputStream out = new FileOutputStream(file)) {
                    byte[] buffer = new byte[2048];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
                LOGGER.info("Config copied from jar resources: " + fileName);
                return;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to copy default config", e);
        }
        try {
            file.createNewFile();
            LOGGER.info("Config created (empty): " + fileName);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to create config: " + fileName, e);
        }
    }

    private InputStream getResourceStream(String name) {
        try {
            org.bukkit.plugin.Plugin p = findPlugin();
            if (p != null) {
                InputStream is = p.getResource(name);
                if (is != null) return is;
            }
        } catch (Exception ignored) {
        }
        return this.getClass().getClassLoader().getResourceAsStream(name);
    }

    private org.bukkit.plugin.Plugin findPlugin() {
        for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
            try {
                Class<?> c = Class.forName(el.getClassName());
                if (org.bukkit.plugin.Plugin.class.isAssignableFrom(c)) {
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        if (org.bukkit.plugin.Plugin.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            Object o = f.get(null);
                            if (o instanceof org.bukkit.plugin.Plugin) {
                                return (org.bukkit.plugin.Plugin) o;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
