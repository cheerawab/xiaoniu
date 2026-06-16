# API 參考 (API Reference)

## 1. 配對 API (Matchmaking)

### 1.1 加入配對

```yaml
POST /matchmaking/join
{
    "player_uuid": "string",
    "player_name": "string",
    "game_id": "string",
    "custom_options": {
        "key": "value"  // 遊戲自訂欄位
    }
}

回應:
{
    "status": "queued",
    "queue_position": 1,
    "estimated_wait_time": 30,
    "party_id": null,  // 如果以 Party 身份，此字段為 Party ID
    "backend": {  // 後端伺服器名稱 (如果有分配)
        "name": "backend1"
    }
}
```

### 1.2 取消配對

```yaml
POST /matchmaking/cancel
{
    "player_uuid": "string"
}

回應:
{
    "status": "cancelled"
}
```

### 1.3 查詢配對狀態

```yaml
GET /matchmaking/status?player_uuid=string

回應:
{
    "status": "queued",  // queued, matching, completed, failed
    "queue_position": 1,  // 隊列位置
    "estimated_wait_time": 30,  // 預估等待時間 (秒)
    "backend": {  // 如果有後端，顯示後端信息
        "name": "backend1"
    }
}
```

## 2. Party API (Party)

### 2.1 創建 Party

```yaml
POST /party/create
{
    "leader_uuid": "string",
    "max_members": 4,  // 最大成員數
    "password": null  // 密碼 (可選)
}

回應:
{
    "status": "created",
    "party_id": "uuid",
    "leader_uuid": "uuid",
    "max_members": 4,
    "password": true  // 是否有密碼
}
```

### 2.2 邀請加入 Party

```yaml
POST /party/invite
{
    "leader_uuid": "uuid",
    "target_uuid": "uuid",
    "target_name": "string"
}

回應:
{
    "status": "invited",
    "party_id": "uuid"
}
```

### 2.3 接受邀請

```yaml
POST /party/accept
{
    "player_uuid": "uuid",
    "inviter_uuid": "uuid"
}

回應:
{
    "status": "accepted",
    "party_id": "uuid"
}
```

### 2.4 踢出 Party

```yaml
POST /party/kick
{
    "leader_uuid": "uuid",
    "member_uuid": "uuid"
}

回應:
{
    "status": "kicked",
    "party_id": "uuid"
}
```

### 2.5 離開 Party

```yaml
POST /party/leave
{
    "player_uuid": "uuid"
}

回應:
{
    "status": "left",
    "party_id": "uuid"
}
```

### 2.6 解散 Party

```yaml
POST /party/disband
{
    "player_uuid": "uuid"  // 只能是領導者
}

回應:
{
    "status": "disbanded",
    "party_id": "uuid"
}
```

## 3. 好友 API (Friend)

### 3.1 添加好友

```yaml
POST /friend/add
{
    "player_uuid": "string",
    "friend_uuid": "string",
    "friend_name": "string"
}

回應:
{
    "status": "added",
    "friend_uuid": "string"
}
```

### 3.2 移除好友

```yaml
POST /friend/remove
{
    "player_uuid": "string",
    "friend_uuid": "string"
}

回應:
{
    "status": "removed",
    "friend_uuid": "string"
}
```

### 3.3 設置黑名單/忽略

```yaml
POST /friend/block
{
    "player_uuid": "string",
    "target_uuid": "string",
    "target_name": "string",
    "action": "block"  // 或 "ignore"
}

回應:
{
    "status": "blocked"  // 或 "ignored"
}
```

### 3.4 獲取好友列表

```yaml
POST /friend/list
{
    "player_uuid": "string",
    "type": "all"  // 或 "friends", "blocked", "ignored"
}

回應:
{
    "friends": [
        {
            "uuid": "string",
            "name": "string",
            "online": true  // 是否線上
        }
    ]
}
```

## 4. 遊戲 API (Game)

### 4.1 獲取遊戲列表

```yaml
GET /games/list
{
    "include_online": false  // 是否包含線上遊戲信息
}

回應:
{
    "games": [
        {
            "id": "survival",
            "name": "Survival",
            "min_players": 4,
            "max_players": 8,
            "world_template": "survival_world",
            "online_sessions": 3  // 如果有 include_online=true
        }
    ]
}
```

### 4.2 獲取遊戲自訂欄位

```yaml
GET /games/custom_options?game_id=survival

回應:
{
    "custom_options": [
        {
            "key": "rounds",
            "type": "integer",
            "default": 5,
            "min": 1,
            "max": 10
        }
    ]
}
```

## 5. Lobby API (Lobby)

### 5.1 發送 MatchRequest 到後端

```yaml
// 這是 Lobby ↔ Backend 的內部 API (使用 BungeeCord/Velocity 消息包)
POST /backend/match_request
{
    "session_id": "uuid",
    "game_id": "string",
    "custom_options": {
        "key": "value"
    },
    "players": [
        {
            "uuid": "string",
            "name": "string"
        }
    ],
    "party_id": null  // 如果以 Party 身份
}

回應:
{
    "status": "accepted",  // 或 "failed"
    "backend": "backend1",
    "world": "survival_world_001",
    "players": [
        {
            "uuid": "string",
            "name": "string"
        }
    ]
}
```

### 5.2 通知玩家 teleport

```yaml
POST /lobby/notify_teleport
{
    "session_id": "uuid",
    "players": [
        {
            "uuid": "string",
            "name": "string"
        }
    ],
    "server": "backend1"
}
```

## 6. Backend API (Backend)

### 6.1 註冊後端

```yaml
POST /backend/register
{
    "server_id": "backend1",
    "server_name": "Backend 1",
    "capacity": 100,  // 最大容量
    "current_players": 0  // 當前玩家數
}

回應:
{
    "status": "registered"
}
```

### 6.2 註冊 Party Game

```java
// 在 PartyGameCore 插件中
public class PartyGameCore extends JavaPlugin {
    
    private final GameRegistry gameRegistry = new GameRegistry();
    
    @Override
    public void onEnable() {
        // 註冊所有遊戲
        for (GamePlugin plugin : gamePlugins) {
            gameRegistry.register(plugin);
        }
    }
    
    public GameRegistry getGameRegistry() {
        return gameRegistry;
    }
}

// GameRegistry API
public class GameRegistry {
    public void register(GamePlugin plugin) {
        // 註冊
        games.put(plugin.getId(), plugin);
    }
    
    public Optional<GamePlugin> get(String id) {
        return Optional.ofNullable(games.get(id));
    }
    
    public List<GamePlugin> getAll() {
        return new ArrayList<>(games.values());
    }
}
```

## 7. 後端 API (Backend)

### 7.1 發送 MatchAccepted 回應到 Lobby

```yaml
POST /lobby/match_accepted
{
    "session_id": "uuid",
    "backend": "backend1",
    "world": "survival_world_001",
    "players": [
        {
            "uuid": "string",
            "name": "string"
        }
    ]
}
```

### 7.2 通知 Lobby 遊戲開始

```yaml
POST /lobby/game_start
{
    "session_id": "uuid",
    "game_id": "string",
    "world": "world_name",
    "countdown": 10  // 倒數秒數
}
```

### 7.3 通知 Lobby 遊戲結束

```yaml
POST /lobby/game_end
{
    "session_id": "uuid",
    "game_id": "string",
    "results": [
        {
            "uuid": "string",
            "score": 100
        }
    ]
}
```

## 使用範例

### 7.1 Lua 腳本範例

```lua
-- 使用 cURL 調用 API

-- 1. 加入配對
local response = http.post(
    "http://localhost:8000/matchmaking/join",
    {
        player_uuid = "12345",
        game_id = "survival",
        custom_options = {
            rounds = 5,
            time_per_round = 120
        }
    }
)

-- 2. 取消配對
http.post("http://localhost:8000/matchmaking/cancel", {
    player_uuid = "12345"
})

-- 3. 創建 Party
local party_response = http.post(
    "http://localhost:8000/party/create",
    {
        leader_uuid = "12345",
        max_members = 4
    }
)

-- 4. 邀請加入 Party
http.post("http://localhost:8000/party/invite", {
    leader_uuid = "12345",
    target_uuid = "67890"
})

-- 5. 接受邀請
http.post("http://localhost:8000/party/accept", {
    player_uuid = "67890",
    inviter_uuid = "12345"
})

-- 6. 添加好友
http.post("http://localhost:8000/friend/add", {
    player_uuid = "12345",
    friend_uuid = "67890"
})

-- 7. 設置黑名單
http.post("http://localhost:8000/friend/block", {
    player_uuid = "12345",
    target_uuid = "99999",
    target_name = "BadPlayer"
})

-- 8. 獲取好友列表
local friends_response = http.get(
    "http://localhost:8000/friend/list?player_uuid=12345&type=friends"
)
```

## 8. Redis Pub/Sub API (Redis Pub/Sub API)

### 8.1 Channel 命名規則

```
{prefix}:{category}:{action}:{resource}
```

- `prefix`: "partygame"  (所有 channel 的前綴)
- `category`: "lobby", "backend", "match", "party", "friend", etc.
- `action`: "request", "response", "event", "event", "request", "response", etc.
- `resource`: "matchmaking", "backend", etc.

### 8.2 主要 Channel

| Channel | 說明 |
|---------|------|
| `partygame:lobby:request` | Lobby 發送的請求 (例如配對請求) |
| `partygame:lobby:response` | Lobby 發送的回應 (例如 MatchAccepted) |
| `partygame:backend:request` | Backend 發送的請求 (例如遊戲開始通知) |
| `partygame:backend:response` | Backend 發送的回應 (例如 MatchAccepted) |
| `partygame:match:queue` | 配對佇列 (Redis List) |
| `partygame:party:create` | Party 創件事件 |
| `partygame:party:invite` | Party 邀請事件 |
| `partygame:friend:add` | 好友添加事件 |

### 8.3 發送消息

```java
// Java 發送消息
public class RedisPublisher {
    public void publish(String channel, String message) {
        Jedis jedis = jedisPool.getResource();
        try {
            jedis.publish(channel, message);
        } finally {
            jedis.close();
        }
    }
}
```

### 訂閱消息

```java
// Java 訂閱消息
public class RedisSubscriber {
    public void subscribe(String channel) {
        Jedis jedis = jedisPool.getResource();
        JedisPubSub pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                // 處理消息
                handle(channel, message);
            }
        };
        
        jedis.subscribe(pubSub, channel);
    }
}
```
