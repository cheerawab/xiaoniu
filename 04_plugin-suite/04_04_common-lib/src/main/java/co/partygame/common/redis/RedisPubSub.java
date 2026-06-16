package co.partygame.common.redis;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pub/Sub 監聽器抽象基類。
 *
 * 擴展自 JedisPubSub, 提供頻道過濾和基礎事件回調。
 * 實現類只需重寫 `channelFilter` 和 `onMessage` 即可。
 *
 * 使用示例：
 * <pre>{@code
 * public class HealthCheckListener extends RedisPubSub {
 *     public HealthCheckListener() {
 *         super();
 *     }
 *
 *     @Override
 *     public void handleMessage(String channel, String message) {
 *         System.out.println("Received on " + channel + ": " + message);
 *     }
 *
 *     @Override
 *     protected boolean channelFilter(String channel) {
 *         return channel.startsWith("partygame:");
 *     }
 * }
 * }</pre>
 */
public abstract class RedisPubSub extends redis.clients.jedis.JedisPubSub {

    private static final Logger LOGGER = Logger.getLogger(RedisPubSub.class.getName());
    private volatile boolean subscribed = false;

    /**
     * 消息處理器。
     *
     * @param channel 消息頻道
     * @param message 消息內容
     */
    public void handleMessage(String channel, String message) {
        onMessage(channel, message);
    }

    /**
     * 消息過濾。
     * 返回 true 則接收該頻道消息, false 則過濾掉。
     * 重寫此方法以實現自定義過濾邏輯。
     *
     * @param channel 頻道名稱
     * @return 如果應該處理該頻道消息則返回 true
     */
    protected boolean channelFilter(String channel) {
        return true;
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!channelFilter(channel)) {
            LOGGER.fine("Filtered message from channel: " + channel);
            return;
        }
        handleMessage(channel, message);
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        subscribed = true;
        LOGGER.info("Subscribed to channel [" + channel + "]. Count: " + subscribedChannels);
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        if (subscribedChannels == 0) {
            subscribed = false;
        }
        LOGGER.info("Unsubscribed from channel [" + channel + "]. Count: " + subscribedChannels);
    }

    /**
     * Create Pub/Sub instance and subscribe to given channels.
     * This method blocks until unsubscribed.
     *
     * @param redisManager Manager instance
     * @param channels     channels to subscribe to
     */
    public void subscribe(RedisManager redisManager, String... channels) {
        if (redisManager == null || channels == null || channels.length == 0) {
            throw new IllegalArgumentException("RedisManager and at least one channel must not be null");
        }
        redisManager.subscribe(this, channels);
    }
}
