package co.partygame.common.auth;

import net.luckperms.api.LuckPerms;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 統一的權限管理抽象層。
 * 
 * 支援 LuckPerms, PermissionsEx, GroupManager 和其他 Bukkit 權限插件。
 * 不硬編碼任何 Rank。
 * Rank 完全由外部權限插件 (LP) 管理。
 * 
 * 提供統一的 hasPermission/getGroups/getPrimaryGroup 接口。
 * 使用懶加載，在第一次調用時自動檢測可用的權限插件。
 * 
 * 檢測優先級:
 *  1. LuckPerms - 最高優先級，支援完整功能 (Context, TTL 等)
 *  2. Bukkit 預設 Permission 系統 - 最基礎的回退方案
 */
public class PermissionManager {
    private static final Logger LOGGER = Logger.getLogger(PermissionManager.class.getName());
    
    private final Plugin plugin;
    private PermissionProvider provider;

    public PermissionManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
    }

    /**
     * 獲取當前使用的 PermissionProvider。
     * 如果尚未初始化，會觸發自動檢測。
     * 
     * @return 當前的權限提供器
     */
    public PermissionProvider getProvider() {
        if (provider == null) {
            detect();
        }
        return provider;
    }

    /**
     * 檢測可用的權限插件。
     * 按優先級檢查: LuckPerms > 默認 Bukkit Permission
     * 
     * 檢測成功後會記錄日誌並初始化對應的 Provider。
     */
    public void detect() {
        if (provider != null) {
            LOGGER.fine("PermissionProvider already initialized: " + provider.getName());
            return;
        }

        LOGGER.fine("Detecting permission plugin...");

        // Try LuckPerms first (highest priority, context-aware, TTL support)
        if (isPluginEnabled("LuckPerms")) {
            try {
                Class<?> lpApiClass = Class.forName("net.luckperms.api.LuckPerms");
                java.lang.reflect.Method getInstance = lpApiClass.getMethod("getInstance");
                Object lpInstance = getInstance.invoke(null);
                if (lpInstance != null) {
                    @SuppressWarnings("unchecked")
                    LuckPerms lp = (LuckPerms) lpInstance;
                    provider = new LuckPermsBridge(
                        (org.bukkit.plugin.java.JavaPlugin) plugin,
                        lp
                    );
                    LOGGER.info("PermissionManager: Using LuckPerms bridge");
                    return;
                }
            } catch (Exception e) {
                LOGGER.warning("PermissionManager: Failed to initialize LuckPerms: " + e.getMessage());
            }
        }

        // Fallback to Bukkit default permission system
        provider = new DefaultPermissionProvider();
        LOGGER.info("PermissionManager: " + provider.getName() + " detected as fallback");
    }

    /**
     * 檢查 Bukkit 插件是否已加載並處於活動狀態。
     */
    private boolean isPluginEnabled(String pluginName) {
        org.bukkit.Server server = plugin.getServer();
        if (server == null) return false;
        org.bukkit.plugin.Plugin p = server.getPluginManager().getPlugin(pluginName);
        return p != null && p.isEnabled();
    }

    /**
     * 檢查玩家是否具備指定權限。
     */
    public boolean hasPermission(Player player, String permission) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        return getProvider().hasPermission(player, permission);
    }

    /**
     * 檢查玩家是否具備指定權限 (帶上下文)。
     * 支援 Context 的 Provider 會使用上下文參數，不支持的回退到基本檢查。
     */
    public boolean hasPermission(Player player, String permission, String contextKey, String contextValue) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        return getProvider().hasPermission(player, permission, contextKey, contextValue);
    }

    /**
     * 檢查玩家是否具備任一指定權限。
     */
    public boolean hasAnyPermission(Player player, String... permissions) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(permissions, "permissions must not be null");
        for (String perm : permissions) {
            if (hasPermission(player, perm)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 檢查玩家是否具備所有指定權限。
     */
    public boolean hasAllPermissions(Player player, String... permissions) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(permissions, "permissions must not be null");
        for (String perm : permissions) {
            if (!hasPermission(player, perm)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 獲取玩家所在的所有分組。
     */
    public Set<String> getGroups(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        return getProvider().getGroups(player);
    }

    /**
     * 獲取玩家的主要分組。
     */
    public String getPrimaryGroup(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        return getProvider().getPrimaryGroup(player);
    }

    /**
     * 獲取當前使用的 Provider 名稱。
     */
    public String getProviderName() {
        return getProvider().getName();
    }
}
