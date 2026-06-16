package co.partygame.common.redis;

import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class RedisManager {
    private final JavaPlugin plugin;
    private JedisPool pool;
    private String channel;
    private boolean connected = false;
    private ExecutorService executor;

    public RedisManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(4);
    }

    public void connect() {
        try {
            String host = plugin.getConfig().getString("redis.host", "localhost");
            int port = plugin.getConfig().getInt("redis.port", 6379);
            String password = plugin.getConfig().getString("redis.password", "");
            int database = plugin.getConfig().getInt("redis.database", 0);
            this.channel = plugin.getConfig().getString("redis.channel", "partygame:match");
            
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(plugin.getConfig().getInt("redis.pool.size", 5));
            
            if (password != null && !password.isEmpty()) {
                pool = new JedisPool(poolConfig, host, port, 2000, password, database);
            } else {
                pool = new JedisPool(poolConfig, host, port, 2000, null, database);
            }
            
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
                connected = true;
                plugin.getLogger().info("Redis 連接成功: " + host + ":" + port);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Redis 連接失敗", e);
        }
    }

    public void disconnect() {
        if (pool != null) {
            pool.close();
            connected = false;
        }
        executor.shutdown();
    }

    public void publish(String message) {
        if (!connected) return;
        executor.submit(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.publish(channel, message);
            }
        });
    }

    public JedisPool getPool() {
        return pool;
    }

    public String getChannel() {
        return channel;
    }

    public boolean isConnected() {
        return connected;
    }
}
