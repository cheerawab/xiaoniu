package co.partygame.common;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class DefaultConfigGenerator {
    private final JavaPlugin plugin;

    public DefaultConfigGenerator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void generateDefaultConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }
        
        FileConfiguration config = plugin.getConfig();
        config.set("database.host", "localhost");
        config.set("database.port", 3306);
        config.set("database.database", "partygame");
        config.set("database.username", "root");
        config.set("database.password", "password");
        config.set("database.pool.size", 5);
        config.set("database.pool.max_lifetime", 1800000);
        config.set("database.pool.idle_timeout", 600000);
        
        config.set("redis.host", "localhost");
        config.set("redis.port", 6379);
        config.set("redis.password", "");
        config.set("redis.database", 0);
        config.set("redis.channel", "partygame:lobby");
        config.set("redis.pool.size", 5);
        
        config.set("backend.strategy", "least_players");
        config.set("backend.health_check_interval", 30);
        
        config.set("gui.theme", "dark");
        config.set("gui.refresh_interval", 10);
        
        config.set("matchmaking.queue_ttl", 300);
        config.set("matchmaking.max_matches", 8);
        config.set("matchmaking.cooldown", 5);
        
        plugin.saveConfig();
        plugin.getLogger().info("預設配置文件已建立");
    }
}
