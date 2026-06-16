package co.partygame.common;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import co.partygame.common.config.ConfigManager;
import co.partygame.common.redis.RedisManager;
import co.partygame.common.mysql.MySQLManager;
import co.partygame.common.auth.PermissionManager;

public class McCommonPlugin extends JavaPlugin {
    private static McCommonPlugin instance;
    private ConfigManager configManager;
    private RedisManager redisManager;
    private MySQLManager mysqlManager;
    private PermissionManager permissionManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        this.configManager = new ConfigManager(this);
        this.redisManager = new RedisManager(this);
        this.mysqlManager = new MySQLManager(this);
        this.permissionManager = new PermissionManager(this);
        
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new co.partygame.common.listener.ServerLifecycleListener(this), this);
        
        getLogger().info("McCommon v1.0.0 已啟用 - 共享基礎設施載入完成");
    }

    @Override
    public void onDisable() {
        if (redisManager != null) redisManager.disconnect();
        if (mysqlManager != null) mysqlManager.disconnect();
        getLogger().info("McCommon v1.0.0 已停用");
    }

    public static McCommonPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public MySQLManager getMySQLManager() {
        return mysqlManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }
}
