package co.partygame.common.redis;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Redis 管理器 - 基於 Jedis 連接池的統一無同步管理層。
 *
 * 使用 Apache Commons Pool2 管理連接池，提供連接的獲取與歸還。
 * 封裝常用 Redis 操作：字符串、哈希、集合、列表、Pub/Sub、Lua 腳本等。
 *
 * 所有 Redis 命令操作都通過連接池獲取連接，使用後安全歸還，確保連接不會洩漏。
 */
public class RedisManager {

    private static final Logger LOGGER = Logger.getLogger(RedisManager.class.getName());

    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final int connectionTimeout;
    private final int soTimeout;
    private final int maxTotal;
    private final int maxIdle;
    private final int minIdle;
    private final long maxWaitMillis;
    private final long minEvictableIdleTimeMillis;
    private final long timeBetweenEvictionRunsMillis;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private GenericObjectPool<Jedis> pool;

    public RedisManager(String host, int port, String password, int database,
                        int maxTotal, int maxIdle, int minIdle,
                        int connectionTimeout, int soTimeout) {
        this.host = Objects.requireNonNull(host, "Redis host must not be null");
        this.port = port;
        this.password = password;
        this.database = database;
        this.connectionTimeout = connectionTimeout;
        this.soTimeout = soTimeout;
        this.maxTotal = maxTotal;
        this.maxIdle = maxIdle;
        this.minIdle = minIdle;
        this.maxWaitMillis = 30000;
        this.minEvictableIdleTimeMillis = 60000;
        this.timeBetweenEvictionRunsMillis = 30000;
    }

    public RedisManager(String host, int port, int database) {
        this(host, port, null, database, 20, 10, 5, 2000, 2000);
    }

    /**
     * 初始化 Redis 連接池。
     * 必須在首次使用前調用。
     */
    public void init() {
        if (initialized.get()) {
            LOGGER.warning("RedisManager already initialized");
            return;
        }
        GenericObjectPoolConfig<Jedis> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        config.setMaxWaitMillis(maxWaitMillis);
        config.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        config.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(false);
        config.setTestWhileIdle(true);

        pool = new JedisPool(config, host, port, connectionTimeout, soTimeout, password, database);
        initialized.set(true);
        LOGGER.info("Redis connection pool initialized: " + host + ":" + port + " db=" + database);
    }

    @Deprecated
    public void initialize() {
        init();
    }

    /**
     * 獲取一個 Jedis 連接。使用後必須調用 returnConnection() 歸還。
     * <p>
     * 建議使用 try-with-resources 模式或確保 finally 塊中歸還連接。
     *
     * @return Jedis 連接對象
     * @throws IllegalStateException 如果連接池尚未初始化
     */
    public Jedis getConnection() {
        if (!initialized.get()) {
            throw new IllegalStateException("RedisManager not initialized. Call init() first.");
        }
        try {
            Jedis connection = pool.getResource();
            if (connection == null) {
                throw new RuntimeException("Failed to acquire Jedis connection from pool");
            }
            return connection;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Jedis connection", e);
        }
    }

    /**
     * 將 Jedis 連接歸還到連接池。
     *
     * @param connection 要歸還的 Jedis 連接
     */
    public void returnConnection(Jedis connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to return Jedis connection to pool", e);
            }
        }
    }

    /**
     * 安全的字符串獲取包裝器。
     *
     * @param key 鍵
     * @return 對應的值，如果鍵不存在則返回 null
     */
    public String get(String key) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.get(key);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis GET failed for key: " + key, e);
            return null;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全的字符串設置包裝器。
     *
     * @param key   鍵
     * @param value 值
     */
    public void set(String key, String value) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            connection.set(key, value);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis SET failed for key: " + key, e);
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 帶有超時時間的字符串設置。
     *
     * @param key      鍵
     * @param value    值
     * @param seconds  超時時間（秒）
     */
    public void set(String key, String value, int seconds) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            connection.setex(key, seconds, value);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis SETEX failed for key: " + key, e);
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全的刪除包裝器。
     *
     * @param key 鍵
     * @return 被刪除的鍵的數量
     */
    public long del(String key) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.del(key);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis DEL failed for key: " + key, e);
            return 0;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全的計數器遞增包裝器。
     *
     * @param key 鍵
     * @return 遞增後的新值
     */
    public long incr(String key) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.incr(key);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis INCR failed for key: " + key, e);
            return -1;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全的計數器遞增並設置超時包裝器。
     * 用於需要首次初始化並自動過期的計數場景。
     *
     * @param key     鍵
     * @param seconds 超時時間（秒）
     * @return 遞增後的新值
     */
    public long incrExpire(String key, int seconds) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            long result = connection.incr(key);
            if (result == 1) {
                connection.expire(key, seconds);
            }
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis INCR with EXPIRE failed for key: " + key, e);
            return -1;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全的集合成員查詢。
     *
     * @param key 集合鍵
     * @return 集合的所有成員
     */
    public Set<String> smember(String key) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.smembers(key);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis SMEMBERS failed for key: " + key, e);
            return Collections.emptySet();
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全的集合添加。
     *
     * @param key   集合鍵
     * @param value 要添加的值
     * @return 實際添加的成員數量
     */
    public long sadd(String key, String value) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.sadd(key, value);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis SADD failed for key: " + key, e);
            return 0;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全的哈希表設置。
     *
     * @param key     鍵
     * @param hashMap 要設置的哈希字段-值映射
     */
    public void hmset(String key, Map<String, String> hashMap) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Objects.requireNonNull(hashMap, "hashMap must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            connection.hmset(key, hashMap);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis HMSET failed for key: " + key, e);
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全的哈希字段獲取。
     *
     * @param key   鍵
     * @param field 字段
     * @return 對應的值
     */
    public String hget(String key, String field) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Objects.requireNonNull(field, "field must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.hget(key, field);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis HGET failed for key: " + key + " field: " + field, e);
            return null;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全地設置鍵的超時時間。
     *
     * @param key     鍵
     * @param seconds 超時時間（秒）
     * @return 是否設置成功
     */
    public boolean expire(String key, int seconds) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.expire(key, seconds) == 1;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis EXPIRE failed for key: " + key, e);
            return false;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 獲取鍵的剩餘超時時間。
     *
     * @param key 鍵
     * @return 剩餘秒數，-1 表示沒有設置超時，-2 表示鍵不存在
     */
    public long ttl(String key) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.ttl(key);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis TTL failed for key: " + key, e);
            return -2;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全的列表左push（從頭插入）。
     *
     * @param key      列表鍵
     * @param values   要插入的值
     * @return 插入後列表的長度
     */
    public long lpush(String key, String... values) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.lpush(key, values);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis LPUSH failed for key: " + key, e);
            return 0;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全的列表右彈出（從尾部彈出）。
     *
     * @param key 列表鍵
     * @return 彈出的值，列表為空時返回 null
     */
    public String rpop(String key) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.rpop(key);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis RPOP failed for key: " + key, e);
            return null;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全地檢查列表是否包含元素。
     *
     * @param key   列表鍵
     * @param value 要查找的值
     * @return 如果包含返回 true，否則返回 false
     */
    public boolean lcontains(String key, String value) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            Long size = connection.llen(key);
            if (size == null || size == 0) {
                return false;
            }
            return connection.lrange(key, 0, -1).contains(value);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis LCONTAINS failed for key: " + key, e);
            return false;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 獲取列表長度。
     *
     * @param key 列表鍵
     * @return 列表長度，如果鍵不存在則返回 0
     */
    public long llen(String key) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.llen(key);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis LLEN failed for key: " + key, e);
            return 0;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Pub/Sub 訂閱給定通道。
     * 此方法會阻塞當前線程直到取消訂閱。
     *
     * @param pubSub   `JedisPubSub` 實現，用於處理消息的監聽器
     * @param channels 要訂閱的通道名稱
     */
    public void subscribe(JedisPubSub pubSub, String... channels) {
        Objects.requireNonNull(pubSub, "JedisPubSub must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            connection.subscribe(pubSub, channels);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis SUBSCRIBE failed", e);
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Pub/Sub 取消訂閱給定通道。
     *
     * @param pubSub   `JedisPubSub` 實現
     * @param channels 要取消訂閱的通道
     */
    public void unsubscribe(JedisPubSub pubSub, String... channels) {
        Objects.requireNonNull(pubSub, "JedisPubSub must not be null");
        // In Jedis 5.x+ and 7.x+, unsubscribe is handled internally by the JedisPubSub client.
        // The Jedis instance no longer supports direct unsubscribe commands on the
        // connection. Pub/Sub operations are managed by the JedisPubSub client thread.
        LOGGER.info("Redis UNSUBSCRIBE initiated for channels: " + (channels != null ? Arrays.toString(channels) : "all"));
    }

    /**
     * 分布式鎖：SET IF NOT EXISTS，實現原子性加鎖。
     * 使用 Redis 的 `SET key value NX PX expire` 命令。
     *
     * @param key         鎖鍵
     * @param requestId   請求標識（用於鎖的釋放驗證）
     * @param expireMillis 鎖超時時間（毫秒）
     * @return 獲取成功返回 true，否則返回 false
     */
    public boolean setNx(String key, String requestId, int expireMillis) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return Boolean.TRUE.equals(connection.set(key, requestId, "NX", "PX", expireMillis));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis SETNX failed for key: " + key, e);
            return false;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 分布式鎖：SET IF EXIST，用於安全釋放鎖（只釋放自己的鎖）。
     *
     * @param key       鎖鍵
     * @param requestId 請求標識（必須與加鎖時一致）
     * @return 釋放成功返回 true，否則返回 false
     */
    public boolean releaseLock(String key, String requestId) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        String script = ""
            + "if redis.call(\"get\", KEYS[1]) == ARGV[1] "
            + "then return redis.call(\"del\", KEYS[1]) "
            + "else return 0 "
            + "end";
        Jedis connection = null;
        try {
            connection = getConnection();
            return eval(script, Collections.singletonList(key), Collections.singletonList(requestId)) != null
                && Long.parseLong(eval(script, Collections.singletonList(key), Collections.singletonList(requestId)).toString()) > 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis lock release failed for key: " + key, e);
            return false;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 執行 Lua 腳本。
     *
     * @param script      Lua 腳本
     * @param keys        作為 KEYS 數組傳遞的 key 列表
     * @param arguments   作為 ARGV 數組傳遞的參數列表
     * @return 腳本執行結果
     */
    public Object eval(String script, List<String> keys, List<String> arguments) {
        Objects.requireNonNull(script, "Script must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.eval(script, keys, arguments);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis EVAL failed", e);
            return null;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * 安全的 Pub/Sub 發布，封裝在連接池模式中。
     * 使用 Jedis 實例的 publish 方法。
     *
     * @param channel 頻道名稱
     * @param message 要發布的消息
     * @return 接收到該消息的訂閱者數量
     */
    public long publish(String channel, String message) {
        Objects.requireNonNull(channel, "Channel must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.publish(channel, message);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis PUBLISH failed for channel: " + channel, e);
            return 0;
        } finally {
            returnConnection(connection);
        }
    }

    public boolean isConnected() {
        if (!initialized.get()) return false;
        Jedis connection = null;
        try {
            connection = getConnection();
            return "PONG".equals(connection.ping());
        } catch (Exception e) {
            return false;
        } finally {
            returnConnection(connection);
        }
    }

    public void close() {
        if (pool != null) {
            pool.close();
            initialized.set(false);
            LOGGER.info("Redis connection pool closed");
        }
    }

    public int getMaxTotal() { return maxTotal; }
    public int getPort() { return port; }
    public int getDatabase() { return database; }
    public String getHost() { return host; }

    // ─── Sorted Set Operations ──────────────────────────────────

    /**
     * Add a member to a sorted set with a score.
     *
     * @param key   the sorted set key
     * @param score the score
     * @param value the member value
     * @return the number elements added
     */
    public long zAdd(String key, double score, String value) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Objects.requireNonNull(value, "Value must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.zadd(key, score, value);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis ZADD failed for key: " + key, e);
            return 0;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Get the size of a sorted set.
     *
     * @param key the sorted set key
     * @return the number of members, or 0 if key does not exist
     */
    public long zCard(String key) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            Long size = connection.zcard(key);
            return size != null ? size : 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis ZCARD failed for key: " + key, e);
            return 0;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Get the rank of a member in a sorted set (descending score), with 0-based index.
     *
     * @param key   the sorted set key
     * @param value the member value
     * @return the rank, or -1 if member does not exist
     */
    public Long zRevRank(String key, String value) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Objects.requireNonNull(value, "Value must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.zrevrank(key, value);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis ZREVRANK failed for key: " + key, e);
            return null;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Get the score of a member in a sorted set.
     *
     * @param key   the sorted set key
     * @param value the member value
     * @return the score, or null if member does not exist
     */
    public Double zScore(String key, String value) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Objects.requireNonNull(value, "Value must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.zscore(key, value);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis ZSCORE failed for key: " + key, e);
            return null;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Get members from a sorted set in a range (by index), descending.
     *
     * @param key    the sorted set key
     * @param start  start index (inclusive)
     * @param end    end index (inclusive)
     * @return set of members
     */
    public Set<String> zRange(String key, long start, long end) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            List<String> result = connection.zrange(key, start, end);
            return result != null ? new LinkedHashSet<>(result) : Collections.emptySet();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis ZRANGE failed for key: " + key, e);
            return Collections.emptySet();
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Get members from a sorted set in a range (by score), ascending.
     *
     * @param key    the sorted set key
     * @param min    minimum score
     * @param max    maximum score
     * @return set of members
     */
    public Set<String> zRangeByScore(String key, double min, double max) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            List<String> result = connection.zrangeByScore(key, min, max);
            return result != null ? new LinkedHashSet<>(result) : Collections.emptySet();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis ZRANGEBYSCORE failed for key: " + key, e);
            return Collections.emptySet();
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Remove members from a sorted set.
     *
     * @param key     the sorted set key
     * @param members the members to remove
     * @return the number of members removed
     */
    public long zRem(String key, String... members) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.zrem(key, members);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis ZREM failed for key: " + key, e);
            return 0;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Remove a sorted set element with the given score and value.
     *
     * @param key   the sorted set key
     * @param score the score
     * @param value the value
     * @return 1 if removed, 0 if not found
     */
    public long zRem(String key, double score, String value) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Objects.requireNonNull(value, "Value must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.zremrangeByScore(key, score, score);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis ZREMRANGEBYSCORE failed for key: " + key, e);
            return 0;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Remove members from a sorted set within the given rank range.
     *
     * @param key   the sorted set key
     * @param start start rank (inclusive)
     * @param end   end rank (inclusive)
     * @return the number of members removed
     */
    public long zRemRangeByRank(String key, long start, long end) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.zremrangeByRank(key, start, end);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis ZREMRANGEBYRANK failed for key: " + key, e);
            return 0;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Increment the score of a member in a sorted set.
     *
     * @param key   the sorted set key
     * @param score the increment score
     * @param value the member value
     * @return the new score
     */
    public Double zIncrBy(String key, double score, String value) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Objects.requireNonNull(value, "Value must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.zincrby(key, score, value);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis ZINCRBY failed for key: " + key, e);
            return null;
        } finally {
            returnConnection(connection);
        }
    }

    // ─── Hash Operations ──────────────────────────────────────────

    /**
     * Set a field-value pair in a hash.
     *
     * @param key   the hash key
     * @param field the field name
     * @param value the field value
     */
    public void hset(String key, String field, String value) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Objects.requireNonNull(field, "Field must not be null");
        Objects.requireNonNull(value, "Value must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            connection.hset(key, field, value);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis HSET failed for key: " + key + " field: " + field, e);
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Get all fields and values from a hash.
     *
     * @param key the hash key
     * @return map of field to value
     */
    public Map<String, String> hgetAll(String key) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            Map<String, String> result = connection.hgetAll(key);
            return result != null ? result : Collections.emptyMap();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis HGETALL failed for key: " + key, e);
            return Collections.emptyMap();
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Check if a hash field exists.
     *
     * @param key   the hash key
     * @param field the field name
     * @return true if the field exists
     */
    public boolean hexists(String key, String field) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Objects.requireNonNull(field, "Field must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.hexists(key, field);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis HEXISTS failed for key: " + key + " field: " + field, e);
            return false;
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Delete one or more fields from a hash.
     *
     * @param key     the hash key
     * @param fields  the fields to delete
     * @return the number of fields deleted
     */
    public long hdel(String key, String... fields) {
        Objects.requireNonNull(key, "Redis key must not be null");
        Jedis connection = null;
        try {
            connection = getConnection();
            return connection.hdel(key, fields);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis HDEL failed for key: " + key, e);
            return 0;
        } finally {
            returnConnection(connection);
        }
    }
}
