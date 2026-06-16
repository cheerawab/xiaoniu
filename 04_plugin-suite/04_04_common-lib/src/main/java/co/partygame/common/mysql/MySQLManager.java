package co.partygame.common.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL 管理器 - 基於 HikariCP 的數據庫連接池管理器。
 *
 * 封裝 Hikari DataSource 連接池的創建與管理，
 * 提供通用的 SQL 查詢和更新方法，使用 try-with-resources 確保資源釋放。
 * 支持泛型查詢回調和批量更新。
 * 
 * 使用示例：
 * <pre>{@code
 * MySQLManager db = new MySQLManager(host, port, database, username, password);
 * db.initConnectionPool();
 * 
 * // 查詢
 * db.querySingle("SELECT COUNT(*) FROM players WHERE uuid = ?", uuid);
 * 
 * // 更新
 * int rows = db.update("INSERT INTO players (uuid, name) VALUES (?, ?)", uuid, name);
 * 
 * // 批量插入
 * List<Object[]> batch = Arrays.asList(new Object[] { "A" }, new Object[] { "B" });
 * db.executeBatch("INSERT INTO t (col) VALUES (?)", batch);
 * }</pre>
 */
public class MySQLManager {

    private static final Logger LOGGER = Logger.getLogger(MySQLManager.class.getName());

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String poolName;
    private final int maxPoolSize;
    private final int minIdle;

    private volatile HikariDataSource dataSource;

    /**
     * 創建 MySQL 管理器實例。
     *
     * @param host     數據庫服務器地址
     * @param port     端口（默認 3306）
     * @param database 數據庫名稱
     * @param username 用戶名
     * @param password 密碼
     */
    public MySQLManager(String host, int port, String database, String username, String password) {
        this(host, port, database, username, password, "PursWM");
    }

    /**
     * 創建 MySQL 管理器實例。
     *
     * @param host     數據庫服務器地址
     * @param port     端口（默認 3306）
     * @param database 數據庫名稱
     * @param username 用戶名
     * @param password 密碼
     * @param poolName 連接池名稱（用於監控）
     */
    public MySQLManager(String host, int port, String database, String username, String password, String poolName) {
        this(host, port, database, username, password, poolName, 10, 2);
    }

    /**
     * 創建 MySQL 管理器實例。
     *
     * @param host        數據庫服務器地址
     * @param port        端口
     * @param database    數據庫名稱
     * @param username    用戶名
     * @param password    密碼
     * @param poolName    連接池名稱
     * @param maxPoolSize 最大連接數
     * @param minIdle     最小空閒連接數
     */
    public MySQLManager(String host, int port, String database, String username, String password,
                        String poolName, int maxPoolSize, int minIdle) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.poolName = poolName;
        this.maxPoolSize = maxPoolSize;
        this.minIdle = minIdle;
    }

    /**
     * 初始化數據庫連接池。
     * 必須在首次使用前調用。
     */
    public void initConnectionPool() {
        if (dataSource != null && dataSource.isRunning()) {
            LOGGER.warning("MySQL connection pool already initialized");
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(String.format(
            "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8mb4",
            host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        // 語句緩存優化
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useUnicode", "true");
        config.addDataSourceProperty("characterEncoding", "utf8mb4");

        try {
            dataSource = new HikariDataSource(config);
            LOGGER.info("MySQL connection pool initialized: " + host + ":" + port + "/"
                + database + " | poolSize=" + maxPoolSize);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MySQL connection pool", e);
        }
    }

    @Deprecated
    public void initPool() {
        initConnectionPool();
    }

    /**
     * 獲取數據庫連接。
     * 使用後必須關閉（調用 conn.close() 將其歸還連接池）。
     *
     * @return 數據庫存連接
     * @throws IllegalStateException 如果連接池尚未初始化
     */
    public Connection getConnection() {
        if (dataSource == null || !dataSource.isRunning()) {
            throw new IllegalStateException("MySQL connection pool not initialized. Call initConnectionPool() first.");
        }
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get database connection", e);
        }
    }

    /**
     * 泛型查詢方法。
     * 逐行處理結果集，適合多行查詢場景。
     *
     * @param sql      SQL 查詢語句
     * @param callback 結果集回調（每一行調用一次）
     * @param params   可選的查詢參數
     * @param <T>      結果對象類型（用於擴展）
     * @return 查詢結果（此版本固定返回 null，用於通用回調）
     */
    public <T> T query(String sql, Consumer<ResultSet> callback, Object... params) {
        Objects.requireNonNull(sql, "SQL must not be null");
        Objects.requireNonNull(callback, "Callback must not be null");

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setParams(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    callback.accept(resultSet);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "MySQL query failed: " + sql, e);
        }
        return null;
    }

    /**
     * 執行 SELECT 查詢並逐行處理結果。
     * 用於查詢多行記錄的場景。
     *
     * @param sql      SQL 查詢語句
     * @param consumer 每一行的處理函數
     * @param params   可選的查詢參數
     */
    public void executeQuery(String sql, Consumer<ResultSet> consumer, Object... params) {
        Objects.requireNonNull(sql, "SQL must not be null");
        Objects.requireNonNull(consumer, "Consumer must not be null");

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setParams(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    consumer.accept(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("MySQL query failed: " + sql, e);
        }
    }

    /**
     * 執行單行查詢，返回 ResultSet 的第一行第一列。
     * 常用於 COUNT, EXISTS, 或獲取單個字段。
     *
     * @param sql    SQL 查詢語句
     * @param params 可選的參數
     * @return 第一行的第一列值，如果不存在返回 null
     */
    public String querySingle(String sql, Object... params) {
        Objects.requireNonNull(sql, "SQL must not be null");

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setParams(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
            return null;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "MySQL single query failed: " + sql, e);
            return null;
        }
    }

    /**
     * 泛型更新方法。
     * 適用於 INSERT, UPDATE, DELETE, DDL 語句。
     *
     * @param sql    SQL 更新語句
     * @param params 可選的 SQL 參數
     * @return 受影響的行數
     */
    public int update(String sql, Object... params) {
        Objects.requireNonNull(sql, "SQL must not be null");

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setParams(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "MySQL update failed: " + sql, e);
            return -1;
        }
    }

    /**
     * 泛型更新方法 (別名).
     * 適用於 INSERT, UPDATE, DELETE, DDL 語句。
     *
     * @param sql    SQL 更新語句
     * @param params 可選的 SQL 參數
     * @return 受影響的行數
     */
    public int executeUpdate(String sql, Object... params) {
        return update(sql, params);
    }

    /**
     * 批量更新方法。
     * 用於高效批量插入/更新操作。
     * <p>
     * 使用示例：
     * <pre>{@code
     * List<Object[]> batch = new ArrayList<>();
     * batch.add(new Object[] { "John", 25 });
     * batch.add(new Object[] { "Jane", 30 });
     * manager.executeBatch("INSERT INTO players (name, age) VALUES (?, ?)", batch);
     * }</pre>
     *
     * @param sql   SQL 模板語句，包含 `?` 佔位符。
     * @param batch 每行一個 Object[] 參數數組
     * @return 所有批次執行的總計數
     */
    public int executeBatch(String sql, List<Object[]> batch) {
        Objects.requireNonNull(sql, "SQL must not be null");
        Objects.requireNonNull(batch, "Batch must not be null");

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Object[] params : batch) {
                setParams(statement, params);
                statement.addBatch();
            }
            int[] results = statement.executeBatch();
            int total = 0;
            for (int count : results) {
                total += Math.max(0, count); // 忽略 -2 (unknown count)
            }
            return total;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "MySQL batch update failed: " + sql, e);
            return -1;
        }
    }

    /**
     * 設置 PreparedStatement 的參數。
     * 根據參數類型自動設置對應的 JDBC 類型。
     *
     * @param statement 準備語句
     * @param params    參數數組
     */
    private void setParams(PreparedStatement statement, Object[] params) throws SQLException {
        if (params == null || params.length == 0) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param == null) {
                statement.setNull(i + 1, Types.NULL);
            } else if (param instanceof String) {
                statement.setString(i + 1, (String) param);
            } else if (param instanceof Integer) {
                statement.setInt(i + 1, (Integer) param);
            } else if (param instanceof Long) {
                statement.setLong(i + 1, (Long) param);
            } else if (param instanceof Double) {
                statement.setDouble(i + 1, (Double) param);
            } else if (param instanceof Boolean) {
                statement.setBoolean(i + 1, (Boolean) param);
            } else if (param instanceof byte[]) {
                statement.setBytes(i + 1, (byte[]) param);
            } else if (param instanceof java.util.UUID) {
                statement.setString(i + 1, param.toString());
            } else {
                statement.setString(i + 1, param.toString());
            }
        }
    }

    /**
     * 獲取連接池中活動連接數。
     *
     * @return 當前活動連接數
     */
    public int getActiveConnections() {
        if (dataSource != null) {
            return dataSource.getHikariPoolMXBean().getActiveConnections();
        }
        return 0;
    }
}
