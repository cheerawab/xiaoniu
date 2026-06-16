package co.partygame.common.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

public class MySQLManager {
    private final JavaPlugin plugin;
    private HikariDataSource dataSource;
    private boolean connected = false;

    public MySQLManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String database = plugin.getConfig().getString("database.database", "partygame");
            String username = plugin.getConfig().getString("database.username", "root");
            String password = plugin.getConfig().getString("database.password", "");
            
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", 
                host, port, database));
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(plugin.getConfig().getInt("database.pool.size", 5));
            config.setMaxLifetime(plugin.getConfig().getInt("database.pool.max_lifetime", 1800000));
            config.setIdleTimeout(plugin.getConfig().getInt("database.pool.idle_timeout", 600000));
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            
            dataSource = new HikariDataSource(config);
            connected = true;
            plugin.getLogger().info("MySQL 連接成功: " + host + ":" + port + "/" + database);
            
            initializeTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL 連接失敗", e);
        }
    }

    private void initializeTables() {
        String[] tables = {
            "CREATE TABLE IF NOT EXISTS users (uuid CHAR(36) PRIMARY KEY, name VARCHAR(16), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS friends (user_uuid CHAR(36), friend_uuid CHAR(36), status ENUM('FRIEND', 'BLOCKED', 'IGNORED') DEFAULT 'FRIEND', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (user_uuid, friend_uuid))",
            "CREATE TABLE IF NOT EXISTS custom_options (game_id VARCHAR(50), key VARCHAR(100), value TEXT, type ENUM('boolean', 'integer', 'string', 'array') DEFAULT 'string', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (game_id, key))",
            "CREATE TABLE IF NOT EXISTS match_records (id INT AUTO_INCREMENT, session_id CHAR(36), game_id VARCHAR(50), backend_id VARCHAR(50), start_time TIMESTAMP, end_time TIMESTAMP, PRIMARY KEY (id))"
        };
        
        try (Connection conn = dataSource.getConnection()) {
            for (String table : tables) {
                try (PreparedStatement stmt = conn.prepareStatement(table)) {
                    stmt.executeUpdate();
                }
            }
            plugin.getLogger().info("MySQL 資料表初始化完成");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "MySQL 資料表初始化失敗 (可能需要權限)", e);
        }
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            connected = false;
        }
    }

    public Connection getConnection() throws SQLException {
        if (!connected || dataSource == null) {
            throw new SQLException("MySQL 未連接");
        }
        return dataSource.getConnection();
    }

    public boolean isConnected() {
        return connected;
    }
}
