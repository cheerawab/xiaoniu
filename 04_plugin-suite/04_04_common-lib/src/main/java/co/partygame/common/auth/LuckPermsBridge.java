package co.partygame.common.auth;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Logger;

/**
 * LuckPerms 專用橋接器。
 * 
 * 支援完整的功能：
 *  - 基本權限檢查
 *  - Context 權限 (server, world, 等)
 *  - TTL 限時權限 (LuckPerms 內部處理)
 *  - Group 繼承 (直接讀取 LuckPerms 計算後的結果)
 *  - 分組查詢
 *  - Primary Group 查詢
 * 
 * {@link LuckPerms} API 內部會自動處理 LuckPerms 數據庫查詢和緩存。
 */
public class LuckPermsBridge implements PermissionProvider {

    private static final Logger LOGGER = Logger.getLogger(LuckPermsBridge.class.getName());

    private final LuckPerms lp;
    private final JavaPlugin plugin;

    public LuckPermsBridge(JavaPlugin plugin, LuckPerms lp) {
        Objects.requireNonNull(plugin, "plugin must not be null");
        Objects.requireNonNull(lp, "lp must not be null");
        this.plugin = plugin;
        this.lp = lp;
    }

    @Override
    public boolean hasPermission(Player player, String permission) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(permission, "permission must not be null");

        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            LOGGER.fine("LuckPerms: No user found for player " + player.getName());
            return false;
        }

        return user.getCachedData()
            .getPermissionData(user.getQueryOptions())
            .checkPermission(permission)
            .asBoolean();
    }

    @Override
    public boolean hasPermission(Player player, String permission, String contextKey, String contextValue) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(contextKey, "contextKey must not be null");
        Objects.requireNonNull(contextValue, "contextValue must not be null");

        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            LOGGER.fine("LuckPerms: No user found for player " + player.getName());
            return false;
        }

        Map<String, String> context = Collections.singletonMap(contextKey, contextValue);
        QueryOptions contextOptions = user.getQueryOptions().toBuilder()
            .context(context)
            .build();

        return user.getCachedData()
            .getPermissionData(contextOptions)
            .checkPermission(permission)
            .asBoolean();
    }

    @Override
    public Set<String> getGroups(Player player) {
        Objects.requireNonNull(player, "player must not be null");

        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            LOGGER.fine("LuckPerms: No user found for player " + player.getName());
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(new HashSet<>(user.getGroupNames()));
    }

    @Override
    public String getPrimaryGroup(Player player) {
        Objects.requireNonNull(player, "player must not be null");

        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            LOGGER.fine("LuckPerms: No user found for player " + player.getName());
            return null;
        }

        String primaryGroup = user.getPrimaryGroup();
        return primaryGroup != null ? primaryGroup : null;
    }

    @Override
    public String getName() {
        return "LuckPerms";
    }
}
