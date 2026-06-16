package co.partygame.common.auth;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bukkit 預設權限插件的橋接器。
 * 
 * 使用 Player.hasPermission() 進行權限檢查。
 * 作為沒有安裝 LuckPerms 等第三方權限插件時的可選回退方案。
 * 
 * 不支援以下功能：
 *  - Context 權限檢查
 *  - Group 查詢
 *  - Primary Group 查詢
 */
public class DefaultPermissionProvider implements PermissionProvider {

    private static final Logger LOGGER = Logger.getLogger(DefaultPermissionProvider.class.getName());

    @Override
    public boolean hasPermission(Player player, String permission) {
        if (player == null || permission == null) {
            return false;
        }
        return player.hasPermission(permission);
    }

    @Override
    public boolean hasPermission(Player player, String permission, String contextKey, String contextValue) {
        if (player == null || permission == null || contextKey == null || contextValue == null) {
            return false;
        }
        LOGGER.fine("DefaultPermissionProvider does not support context - falling back to basic check for " + permission);
        return player.hasPermission(permission);
    }

    @Override
    public Set<String> getGroups(Player player) {
        LOGGER.fine("DefaultPermissionProvider does not support group queries");
        return Collections.emptySet();
    }

    @Override
    public String getPrimaryGroup(Player player) {
        LOGGER.fine("DefaultPermissionProvider does not support primary group queries");
        return null;
    }

    @Override
    public String getName() {
        return "Bukkit-Default";
    }
}
