package co.partygame.common.auth;

import co.partygame.common.McCommonPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class PermissionManager {
    private final McCommonPlugin plugin;
    private boolean luckPermsAvailable = false;

    public PermissionManager(McCommonPlugin plugin) {
        this.plugin = plugin;
        this.luckPermsAvailable = plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms");
        if (luckPermsAvailable) {
            plugin.getLogger().info("LuckPerms 已檢測到 - 權限系統已啟用");
        } else {
            plugin.getLogger().log(Level.WARNING, "未檢測到 LuckPerms - 權限檢查將 fallback 到 Bukkit 預設權限");
        }
    }

    public boolean hasPermission(Player player, String permission) {
        if (luckPermsAvailable) {
            try {
                net.luck.perp api = (net.luck.perp) plugin.getServer().getPluginManager().getPlugin("LuckPerms");
                if (api != null) {
                    net.luck.perp.api.LuckPerms api = api.api;
                    return api.getUserManager().getUser(player.getUniqueId()).queryPrincipal().checkPermission(
                        net.luck.perp.api.context.ContextManager.createContextSet()
                    ).checkPermission(net.luck.perp.api.permissions.MetaNode.of(permission)).elementPresent();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "LuckPerms 權限檢查失敗，fallback 到 Bukkit", e);
            }
        }
        return player.hasPermission(permission);
    }

    public boolean hasAllPermissions(Player player, String... permissions) {
        for (String perm : permissions) {
            if (!hasPermission(player, perm)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasAnyPermission(Player player, String... permissions) {
        for (String perm : permissions) {
            if (hasPermission(player, perm)) {
                return true;
            }
        }
        return false;
    }

    public boolean isLuckPermsAvailable() {
        return luckPermsAvailable;
    }
}
