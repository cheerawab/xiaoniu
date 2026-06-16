package co.partygame.common.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try {
                plugin.saveDefaultConfig();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "無法建立預設配置文件", e);
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "無法保存配置文件", e);
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        InputStream defaultConfig = plugin.getResource("config.yml");
        if (defaultConfig != null) {
            YamlConfiguration defaultConfigObj = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultConfig)
            );
            config.setDefaults(defaultConfigObj);
        }
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }

    public void set(String path, Object value) {
        getConfig().set(path, value);
        saveConfig();
    }

    public String getString(String path) {
        return getConfig().getString(path);
    }

    public int getInteger(String path) {
        return getConfig().getInt(path);
    }

    public boolean getBoolean(String path) {
        return getConfig().getBoolean(path);
    }

    public double getDouble(String path) {
        return getConfig().getDouble(path);
    }
}
