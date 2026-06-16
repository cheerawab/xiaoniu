package co.partygame.common.mysql.tables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import co.partygame.common.mysql.MySQLManager;

/**
 * 數據表管理 - 包含所有 MySQL 表的 DDL 語句。
 * 
 * 提供以下表的建表語句：
 * <ul>
 *   <li>USER - 玩家主數據表</li>
 *   <li>FRIEND - 好友關係表</li>
 *   <li>MATCH_RECORD - 配對記錄表</li>
 *   <li>CUSTOM_OPTIONS - 自定義選項表</li>
 *   <li>PARTY_STATE - 團隊狀態表</li>
 * </ul>
 * 
 * 所有表使用 utf8mb4 字符集和 utf8mb4_unicode_ci 排序規則。
 * 
 * 使用示例：
 * <pre>{@code
 * // 自動創建所有表
 * DatabaseTables.initTables(manager);
 * 
 * // 驗證特定表是否存在
 * boolean exists = DatabaseTables.verifyTable(manager, "pg_users");
 * }</pre>
 */
public final class DatabaseTables {

    private static final Logger LOGGER = Logger.getLogger(DatabaseTables.class.getName());

    private static final String CHARSET       = "utf8mb4";
    private static final String COLLATION     = "utf8mb4_unicode_ci";

    private static final String COLUMN_BIGINT      = "BIGINT UNSIGNED NOT NULL";
    private static final String COLUMN_CHAR64      = "VARCHAR(64) NOT NULL";
    private static final String COLUMN_CHAR128     = "VARCHAR(128) NOT NULL";
    private static final String COLUMN_CHAR255     = "VARCHAR(255) NOT NULL";
    private static final String COLUMN_CHAR32      = "VARCHAR(32) NOT NULL";
    private static final String COLUMN_TEXT        = "TEXT";
    private static final String COLUMN_TEXT_NULL   = "TEXT NULL";
    private static final String COLUMN_INT         = "INT NOT NULL";
    private static final String COLUMN_DOUBLE      = "DOUBLE NOT NULL";
    private static final String COLUMN_TINYINT     = "TINYINT NOT NULL";
    private static final String COLUMN_TIMESTAMP   = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP";
    private static final String COLUMN_TIMESTAMP_U = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP";
    private static final String COLUMN_INT_D0      = "INT NOT NULL DEFAULT 0";

    private DatabaseTables() {
        throw new UnsupportedOperationException("Utility class - no instantiation");
    }

    /**
     * 獲取所有表的建表語句。
     * 返回完整 DDL 語句列表，可直接逐條執行。
     * 
     * @return 包含所有表建表語句的 List<String>
     */
    public static List<String> getCreateTableStatements() {
        List<String> statements = new ArrayList<>(5);
        statements.add(getUserTableDDL());
        statements.add(getFriendTableDDL());
        statements.add(getMatchRecordTableDDL());
        statements.add(getCustomOptionsTableDDL());
        statements.add(getPartyStateTableDDL());
        return Collections.unmodifiableList(statements);
    }

    /**
     * 通過 MySQLManager 自動創建所有表。
     * 
     * @param manager MySQL 管理器實例
     * @return 成功創建表的數量
     */
    public static int initTables(MySQLManager manager) {
        int count = 0;
        for (String ddl : getCreateTableStatements()) {
            int rows = manager.update(ddl);
            if (rows >= 0) {
                count++;
            }
        }
        LOGGER.info("DatabaseTables: Created/verified " + count + " tables");
        return count;
    }

    /**
     * 驗證指定表是否存在。
     * 
     * @param manager MySQL 管理器
     * @param tableName 表名
     * @return 如果表存在返回 true
     */
    public static boolean verifyTable(MySQLManager manager, String tableName) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables "
            + "WHERE table_schema = DATABASE() AND table_name = ?";
        String result = manager.querySingle(sql, tableName);
        return result != null && Integer.parseInt(result) > 0;
    }

    // ─── DDL 語句 ──────────────────────────────────────────────

    private static String getUserTableDDL() {
        return String.format(
            "CREATE TABLE IF NOT EXISTS `pg_users` (" +
                "`id` %s AUTO_INCREMENT PRIMARY KEY, " +
                "`uuid` %s NOT NULL, " +
                "`name` %s NOT NULL, " +
                "`first_seen` %s, " +
                "`last_seen` %s, " +
                "`playtime_seconds` %s, " +
                "`game_wins` %s, " +
                "`game_losses` %s, " +
                "`match_count` %s, " +
                "`streak` %s, " +
                "`total_kills` %s, " +
                "`total_deaths` %s, " +
                "`stats` %s, " +
                "KEY `idx_uuid` (`uuid`), " +
                "KEY `idx_name` (`name`), " +
                "KEY `idx_last_seen` (`last_seen`) " +
            ") ENGINE=InnoDB DEFAULT CHARSET=%s COLLATE=%s",
            COLUMN_BIGINT, COLUMN_CHAR64, COLUMN_CHAR64,
            COLUMN_TIMESTAMP, COLUMN_TIMESTAMP_U,
            COLUMN_INT_D0, COLUMN_INT_D0, COLUMN_INT_D0,
            COLUMN_INT_D0, COLUMN_INT_D0, COLUMN_INT_D0,
            COLUMN_TEXT_NULL, CHARSET, COLLATION);
    }

    private static String getFriendTableDDL() {
        return String.format(
            "CREATE TABLE IF NOT EXISTS `pg_friends` (" +
                "`id` %s AUTO_INCREMENT PRIMARY KEY, " +
                "`player_uuid` %s NOT NULL, " +
                "`friend_uuid` %s NOT NULL, " +
                "`status` %s NOT NULL DEFAULT '%s', " +
                "`added_at` %s, " +
                "UNIQUE KEY `unique_friend` (`player_uuid`, `friend_uuid`), " +
                "KEY `idx_player_uuid` (`player_uuid`), " +
                "KEY `idx_friend_uuid` (`friend_uuid`), " +
                "KEY `idx_status` (`status`) " +
            ") ENGINE=InnoDB DEFAULT CHARSET=%s COLLATE=%s",
            COLUMN_BIGINT, COLUMN_CHAR64, COLUMN_CHAR64,
            COLUMN_CHAR32, "pending", COLUMN_TIMESTAMP, CHARSET, COLLATION);
    }

    private static String getMatchRecordTableDDL() {
        return String.format(
            "CREATE TABLE IF NOT EXISTS `pg_match_records` (" +
                "`id` %s AUTO_INCREMENT PRIMARY KEY, " +
                "`match_id` %s NOT NULL, " +
                "`session_id` %s NOT NULL, " +
                "`game_type` %s NOT NULL, " +
                "`team_a` %s DEFAULT ('[]'), " +
                "`team_b` %s DEFAULT ('[]'), " +
                "`winner` %s NOT NULL DEFAULT '', " +
                "`server` %s NOT NULL, " +
                "`status` %s NOT NULL DEFAULT '%s', " +
                "`started_at` %s, " +
                "`ended_at` %s, " +
                "`metadata` %s, " +
                "`created_at` %s, " +
                "KEY `idx_match_id` (`match_id`), " +
                "KEY `idx_game_type` (`game_type`), " +
                "KEY `idx_server` (`server`), " +
                "KEY `idx_status` (`status`), " +
                "KEY `idx_started_at` (`started_at`) " +
            ") ENGINE=InnoDB DEFAULT CHARSET=%s COLLATE=%s",
            COLUMN_BIGINT, COLUMN_CHAR64, COLUMN_CHAR32,
            COLUMN_CHAR32, COLUMN_TEXT, COLUMN_TEXT,
            COLUMN_CHAR32, COLUMN_CHAR32, COLUMN_CHAR32,
            "waiting", COLUMN_TIMESTAMP, COLUMN_TIMESTAMP_U,
            COLUMN_TEXT_NULL, COLUMN_TIMESTAMP_U, CHARSET, COLLATION);
    }

    private static String getCustomOptionsTableDDL() {
        return String.format(
            "CREATE TABLE IF NOT EXISTS `pg_custom_options` (" +
                "`id` %s AUTO_INCREMENT PRIMARY KEY, " +
                "`key_name` %s NOT NULL, " +
                "`value` %s NOT NULL, " +
                "`description` %s, " +
                "`category` %s NOT NULL DEFAULT 'general', " +
                "UNIQUE KEY `unique_key` (`key_name`), " +
                "KEY `idx_category` (`category`) " +
            ") ENGINE=InnoDB DEFAULT CHARSET=%s COLLATE=%s",
            COLUMN_BIGINT, COLUMN_CHAR128, COLUMN_TEXT,
            COLUMN_CHAR255, COLUMN_CHAR64, CHARSET, COLLATION);
    }

    private static String getPartyStateTableDDL() {
        return String.format(
            "CREATE TABLE IF NOT EXISTS `pg_party_states` (" +
                "`id` %s AUTO_INCREMENT PRIMARY KEY, " +
                "`party_id` %s NOT NULL, " +
                "`player_uuid` %s NOT NULL, " +
                "`player_name` %s NOT NULL, " +
                "`role` %s NOT NULL DEFAULT '%s', " +
                "`state` %s NOT NULL DEFAULT '%s', " +
                "`joined_at` %s, " +
                "`last_active` %s, " +
                "UNIQUE KEY `unique_player_party` (`party_id`, `player_uuid`), " +
                "KEY `idx_party_id` (`party_id`), " +
                "KEY `idx_player_uuid` (`player_uuid`) " +
            ") ENGINE=InnoDB DEFAULT CHARSET=%s COLLATE=%s",
            COLUMN_BIGINT, COLUMN_CHAR32, COLUMN_CHAR64, COLUMN_CHAR64,
            COLUMN_CHAR32, "leader", COLUMN_CHAR32, "active",
            COLUMN_TIMESTAMP, COLUMN_TIMESTAMP_U, CHARSET, COLLATION);
    }
}
