package co.partygame.common.auth;

import org.bukkit.entity.Player;
import java.util.Set;

/**
 * 權限接口抽象 - 所有外部權限插件的統一接口。
 * 
 * 所有 Bukkit 權限插件 (LuckPerms, PermissionsEx, GroupManager, SimplePermission 等)
 * 都通過實現此接口來适配到 common-lib 中。
 * 
 * 不硬編碼任何 Rank 或角色名稱，完全由外部管理。
 */
public interface PermissionProvider {

    /**
     * 檢查玩家是否具備指定權限。
     *
     * @param player 目標玩家
     * @param permission 權限節點 (如 "purswm.match.use")
     * @return 具備權限時返回 true
     */
    boolean hasPermission(Player player, String permission);

    /**
     * 檢查玩家是否具備指定權限，帶上下文參數。
     * 用於 LuckPerms 等支援 Context 的權限插件 (server, world 等條件)。
     *
     * @param player 目標玩家
     * @param permission 權限節點
     * @param contextKey 上下文 Key (如 "server")
     * @param contextValue 上下文 Value (如 "lobby-1")
     * @return 具備權限時返回 true，不支持上下文時回退到基本檢查
     */
    boolean hasPermission(Player player, String permission, String contextKey, String contextValue);

    /**
     * 獲取玩家所在的所有分組。
     *
     * @param player 目標玩家
     * @return 分組名稱集合，如果不支持則返回空集合
     */
    Set<String> getGroups(Player player);

    /**
     * 獲取玩家的主要分組 (Primary Group)。
     *
     * @param player 目標玩家
     * @return 主要分組名稱，不存在則返回 null
     */
    String getPrimaryGroup(Player player);

    /**
     * 獲取此 Provider 支援的名稱 (如 "LuckPerms", "Bukkit-Default")。
     * 用於日誌記錄和監控。
     *
     * @return Provider 名稱
     */
    String getName();
}
