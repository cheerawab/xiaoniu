package co.partygame.common.redis;

/**
 * Redis Pub/Sub 通道名稱常量。
 *
 * 所有跨伺服器通信的 Pub/Sub 頻道都在此集中定義，
 * 避免硬編碼字符串，確保通道名稱一致性。
 */
public final class RedisChannels {

    private static final String PREFIX = "partygame:";

    /**
     * Lobby 伺服器間消息通道：
     * 用於 Lobby 伺服器之間的玩家狀態同步和配對相關事件。
     */
    public static final String LOBBY_MESSAGES = PREFIX + "lobby:messages";

    /**
     * Backend 伺服器間消息通道：
     * 用於後台遊戲伺服器之間的事件同步和遊戲狀態共享。
     */
    public static final String BACKEND_MESSAGES = PREFIX + "backend:messages";

    /**
     * 配對隊列通道：
     * 配對管理器在此發布隊列事件（入隊、出隊、超時等）。
     */
    public static final String MATCH_QUEUE = PREFIX + "match:queue";

    /**
     * 配對結果通道：
     * 配對完成後，配對結果發送到此通道供所有伺服器接收。
     */
    public static final String MATCH_RESULT = PREFIX + "match:result";

    /**
     * 團隊事件通道：
     * 用於團隊創建、解散、成員變更等事件。
     */
    public static final String PARTY_EVENTS = PREFIX + "party:events";

    /**
     * 好友事件通道：
     * 用於好友請求、同意、刪除等事件。
     */
    public static final String FRIEND_EVENTS = PREFIX + "friend:events";

    /**
     * 健康檢查通道：
     * 用於伺服器心跳和健康狀態同步。
     */
    public static final String HEALTH_CHECK = PREFIX + "health:check";

    /**
     * 後台事件通道：
     * 通用的後台事件廣播，管理後台伺服器狀態等。
     */
    public static final String BACKEND_EVENTS = PREFIX + "backend:events";

    private RedisChannels() {
        throw new UnsupportedOperationException("Utility class - no instantiation");
    }
}
