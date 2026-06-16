# 通用配對插件架構指南 (Matchmaking Architecture Guide)

## 1. 插件定位

通用配對插件 (Matchmaking) 是 Lobby 伺服器的核心插件，負責：
- 配對佇列管理
- 遊戲選擇界面
- 後端伺服器選址
- 遊戲自訂欄位打包
- Party 整合
- 權限檢查
- 與後端伺服器通信

## 2. 插件結構

```
04_plugin-suite/04_01_matchmaking/
├── src/main/java/co/partygame/matchmaking/
│   ├── MatchmakingPlugin.java      # 主插件類
│   ├── MatchQueue.java             # 配對佇列管理
│   ├── MatchRouter.java            # 後端路由
│   ├── MatchStrategy.java          # 匹配策略
│   ├── PlayerState.java            # 玩家狀態
│   ├── GamePayloadBuilder.java     # 遊戲自訂欄位打包
│   ├── party/                      # ── 隊友系統整合
│   │   ├── PartyMatchQueue.java    # 隊友配對隊列
│   │   ├── PartyMatchValidator.java # 隊友配對驗證
│   │   └── CustomRoomCreator.java  # 自訂房間創建者
│   ├── backend/                    # 後端伺服器管理
│   │   ├── BackendManager.java     # 後端伺服器管理
│   │   ├── BackendHealth.java      # 後端伺服器健康檢查
│   │   └── BackendSelector.java    # 後端伺服器選擇
│   ├── gui/                        # 界面
│   │   ├── MatchGUI.java           # 主界面
│   │   ├── WaitingGUI.java         # 等待界面
│   │   ├── StatsGUI.java           # 統計界面
│   │   └── CustomOptionsGUI.java   # 自訂欄位界面 (Dynamic)
│   ├── auth/                       # ── 權限檢查
│   │   ├── LobbyPermissionChecker.java  # 隊友前權限檢查
│   │   ├── RoomPermissionChecker.java   # 自訂房間權限/加入
│   │   └── RankGateChecker.java         # 權限/Gate 檢查 (由 LP 控制)
│   └── storage/                    # 持久化
│       ├── MatchRecord.java        # 配對記錄
│       └── QueueStats.java         # 隊列統計
```

## 3. 配對流程

### 3.1 主要流程

```
玩家點擊匹配按鈕
    │
    ▼
┌──────────────────────────────────────────┐
│ Lobby 驗證 (MatchmakingPlugin)           │
│ ├── 檢查 lp 權限                         │
│ │   ├── 有權限: continue                 │
│ │   └── 無權限: 顯示 "Access Denied"    │
│ ├── 檢查是否有 Party                     │
│ │   ├── 有 Party: 使用 Party 成員列表    │
│ │   └── 無 Party: 使用玩家個人           │
│ └── 檢查自訂房間                         │
│     ├── 有自訂房間: 使用自訂選項         │
│     └── 無自訂房間: 使用預設選項         │
└──────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────┐
│ 後端選址 (BackendSelector)               │
│ ├── 獲取已連線後端 (Redis)               │
│ ├── 選擇負載最低的後端 (或輪流)           │
│ └── 選定後端 → backend1, backend 2, ...  │
└──────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────┐
│ 打包配對請求 (GamePayloadBuilder)        │
│ ├── 打包玩家列表                         │
│ ├── 打包遊戲自訂欄位                      │
│ └── 打包 Party 資訊 (如有)               │
└──────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────┐
│ 發送 MatchRequest 到後端 (BungeeCord)   │
│ └── 等待後端回應                         │
└──────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────┐
│ 接收 MatchAccepted                       │
│ ├── 通知玩家開始 teleport                │
│ └── 關閉 MatchGUI                        │
└──────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────┐
│ 玩家 teleport 到後端                     │
│ └── 玩家參與遊戲                           │
└──────────────────────────────────────────┘
```

### 3.2 配對佇列管理 (Match Queue)

```java
public class MatchQueue {
    // 單一玩家配對佇列
    private final Map<UUID, MatchState> soloQueue = new HashMap<>();
    
    // Party 配對佇列
    private final Map<UUID, PartyMatchState> partyQueue = new HashMap<>();
    
    // 記錄玩家狀態
    public class MatchState {
        UUID uuid;
        String playerName;
        String gameType;
        String customOptions;  // JSON
        long joinTime;
        boolean isParty;
        UUID partyId;  // 如果屬於 Party
    }
    
    // 加入配對
    public void join(Player player, String gameType, String customOptions) {
        MatchState state = new MatchState();
        state.uuid = player.getUniqueId();
        state.playerName = player.getName();
        state.gameType = gameType;
        state.customOptions = customOptions;
        state.joinTime = System.currentTimeMillis();
        state.isParty = false;
        state.partyId = null;
        
        soloQueue.put(player.getUniqueId(), state);
        
        // 通知後端
        notifyBackend(state);
    }
    
    // 离开配隊
    public void leave(Player player) {
        soloQueue.remove(player.getUniqueId());
    }
    
    // 通知後端
    private void notifyBackend(MatchState state) {
        // 建立 MatchRequest 消息包
        MatchRequest request = new MatchRequest();
        request.setSessionId(UUID.randomUUID().toString());
        request.setGameType(state.gameType);
        request.setPlayers(List.of(
            new PlayerInfo(state.uuid, state.playerName)
        ));
        request.setCustomOptions(state.customOptions);
        
        // 後端選址
        BackendInfo backend = backendSelector.selectBackend();
        
        // 使用 BungeeCord 發送消息包
        BungeeMessenger.sendMatchRequest(backend.name, request);
    }
}
```

## 4. 後端選址 (Backend Selector)

```java
/**
 * 後端選址策略。
 * 支援兩種模式：
 * 1. Lowest Load → 選擇玩家最少的後端
 * 2. Round Robin → 輪流選址 (避免後端負載不均)
 */
public class BackendSelector {
    private final BackendManager backendManager;
    private int lastIndex = 0;
    
    public BackendInfo selectBackend() {
        List<BackendInfo> backends = backendManager.getOnlineBackends();
        
        if (backends.isEmpty()) {
            throw new NoBackendAvailableException("沒有可用的後端伺服器");
        }
        
        // 根據策略選址
        BackendInfo backend = backends.stream()
            .min(Comparator.comparingLong(BackendInfo::getPlayerCount))
            .orElse(backends.get(lastIndex++ % backends.size()));
        
        return backend;
    }
}
```

## 5. 遊戲自訂欄位打包 (GamePayloadBuilder)

```java
/**
 * 遊戲自訂欄位打包器。
 * 所有遊戲相關細節 (game_id, custom_options) 都封裝在 game_payload 裡。
 * 配對插件不知道也不關心底層遊戲邏輯。
 */
public class GamePayloadBuilder {
    
    /**
     * 打包 MatchRequest 包
     */
    public MatchRequest buildMatchRequest(
            List<Player> players,
            String gameId,
            String gameType,
            Map<String, Object> customOptions,
            String worldConfig,
            UUID partyId) {
        
        MatchRequest request = new MatchRequest();
        request.setSessionId(UUID.randomUUID().toString());
        request.setGameType(gameId);
        request.setGameType(gameType);
        request.setCustomOptions(customOptions);
        request.setPlayers(playersInfo(players));
        request.setPartyId(partyId);
        
        // 打包 game_payload
        GamePayload payload = new GamePayload();
        payload.setGameType(gameType);
        payload.setGameId(gameId);
        payload.setSettings(customOptions);
        payload.setWorldConfig(worldConfig);
        request.setPayload(payload);
        
        return request;
    }
}
```

**GamePayload 結構**:

```json
{
    "game_type": "partygame",
    "game_id": "survival_001",
    "settings": {
        "rounds": 5,
        "time_per_round": 120,
        "difficulty": "normal",
        "pvp": true
    },
    "world_config": {
        "template": "partygame_template",
        "dimension": "overworld"
    }
}
```

## 6. 權限檢查 (Lobby Permission Checker)

```java
/**
 * 所有權限檢查都依賴 LuckPerms (LP)
 * 不硬編碼任何 rank
 */
public class LobbyPermissionChecker {
    
    private final PermissionChecker permissionChecker;
    private final PermissionManager permissionManager;
    
    /**
     * 檢查玩家是否有權限加入配對
     */
    public boolean canJoinMatch(Player player, String gameType) {
        // 1. 檢查基本配對權限 (partygame.match.join)
        if (!permissionChecker.hasPermission(player, "partygame.match.join")) {
            return false;
        }
        
        // 2. 檢查遊戲類型權限 (partygame.game.<gameType>)
        if (!permissionChecker.hasPermission(player, "partygame.game." + gameType)) {
            return false;
        }
        
        // 3. 检查是否有通配符權限 (partygame.game.*)
        if (permissionChecker.hasPermission(player, "partygame.game.*")) {
            return true;  // 绕過所有單一權限檢查
        }
        
        return true;
    }
    
    /**
     * 檢查玩家是否有權限創建自訂房間
     */
    public boolean canCreateCustomRoom(Player player) {
        return permissionChecker.hasAllPermissions(player,
            "partygame.match.custom_room",
            "partygame.custom_room.create",
            "partygame.custom_room.settings"
        );
    }
    
    /**
     * 檢查玩家是否有權限使用 Party 匹配
     */
    public boolean canPartyMatch(Player leader, List<Player> members) {
        // 1. 检查 leader 有 party.match 權限
        if (!permissionChecker.hasPermission(leader, "partygame.party.match")) {
            return false;
        }
        
        // 2. 检查所有成员都有基本配對權限
        for (Player member : members) {
            if (!permissionChecker.hasPermission(member, "partygame.match.join")) {
                return false;
            }
            if (!permissionChecker.hasPermission(member, "partygame.game." + gameType)) {
                return false;
            }
        }
        
        return true;
    }
}
```

## 7. Party 整合 (Party Integration)

### 7.1 Party 配對流程

```
1. 玩家創建 Party (GUI 或 /party create)
2. 隊友邀請隊友加入
3. 整個 Party 作為一個整體進行匹配
4. 所有隊員一起被傳送到同一個後端伺服器
```

### 7.2 Party 配對隊列 (PartyMatchQueue)

```java
/**
 * Party 配對隊列管理。
 * 優先匹配整個 Party，如果 Party 成員數不足，再從 solo 隊伍中填補。
 */
public class PartyMatchQueue {
    
    /**
     * Party 成員加入配對
     */
    public void joinPartyMatch(PartyLeader leader, List<Player> members,
                                String gameType, String customOptions) {
        // 1. 验证 Party 配對權限
        if (!permissionChecker.canPartyMatch(leader, members)) {
            leader.sendMessage("Access denied");
            return;
        }
        
        // 2. 记录 Party 配對請求
        partyMatchQueue.add(PartymatchRequest(
            leader.getUniqueId(),  // Party 領導者 ID
            members,   // 所有隊員
            gameType,  // 遊戲類型
            customOptions  // 自訂 options
        ));
        
        // 3. 通知後端
        notifyBackend(request);
    }
    
    /**
     * 通知後端
     */
    private void notifyBackend(PartyMatchRequest request) {
        // 使用 BungeeCord 發送 PartyMatchRequest 到後端
        // 所有隊員信息都包含在 request 中
    }
}
```

### 7.3 Party 配對驗證 (PartyMatchValidator)

```java
/**
 * 验证 Party 配對要求。
 * 驗證所有隊員是否都有遊戲所需權限。
 * 如果 Party 成員數不足，嘗試從 solo 隊伍中填補。
 */
public class PartyMatchValidator {
    
    /**
     * 驗證 Party 配對要求
     */
    public boolean validatePartyMatch(
            String gameType, String customOptions) {
        
        for (Player member : members) {
            if (!permissionChecker.hasPermission(member, "partygame.match.join")) {
                return false;
            }
            if (!permissionChecker.hasPermission(member, "partygame.game." + gameType)) {
                return false;
            }
        }
        
        // 如果有自訂房間，檢查自訂房間權限
        if (hasCustomRoom) {
            if (!permissionChecker.canCreateCustomRoom(leader)) {
                return false;
            }
        }
        
        return true;
    }
}
```

## 8. 界面 (GUI)

### 8.1 Match GUI

```java
/**
 * 配對主界面。
 * 提供遊戲類型選擇。
 */
public class MatchGUI {
    
    /**
     * 玩家打開 GUI
     */
    public void openMatchGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9 * 4, "配對界面");
        
        // 添加遊戲類型按鈕
        Map<String, GameType> games = gameTypeManager.getAvailableGames();
        int slot = 0;
        for (Map.Entry<String, GameType> entry : games.entrySet()) {
            String gameName = entry.getKey();
            GameType gameType = entry.getValue();
            
            ItemStack item = new ItemStack(Material.STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(gameName);
            meta.setLore(List.of(
                "玩家數: " + gameType.getMinPlayers() + "-" + gameType.getMaxPlayers(),
                "玩法: " + gameType.getDescription()
            ));
            item.setItemMeta(meta);
            
            gui.setItem(slot++, item);
        }
        
        player.openInventory(gui);
    }
    
    /**
     * 玩家點擊 GUI 事件
     */
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getName().equals("配對界面")) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null) return;
            
            String gameName = ItemMeta.getDisplayName();
            String gameType = gameNameToType(gameName);
            
            // 檢查權限
            Player player = (Player) event.getWhoClicked();
            if (!permissionChecker.canJoinMatch(player, gameType)) {
                player.sendMessage("Access denied");
                return;
            }
            
            // 關閉 GUI
            event.getWhoClicked().closeInventory();
            
            // 顯示等待 GUI
            openWaitingGUI(player);
            
            // 發送 MatchRequest 到後端
            matchmakingQueue.joinMatch(player, gameType);
        }
    }
}
```

### 8.2 Waiting GUI

```java
/**
 * 等待配對界面。
 * 顯示隊列位置、預估等待時間等。
 */
public class WaitingGUI {
    
    /**
     * 玩家打開等待 GUI
     */
    public void openWaitingGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9 * 4, "等待配對");
        
        // 顯示隊列位置
        int queuePosition = matchmakingQueue.getQueuePosition(player);
        int estimatedWaitTime = matchmakingQueue.getEstimatedWaitTime(player);
        
        // 更新 GUI 內容
        // ...
        
        player.openInventory(gui);
        
        // 定期更新 GUI
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // 更新 GUI 內容
            // ...
        }, 20L, 20L);
    }
}
```

### 8.3 Stats GUI

```java
/**
 * 個人統計界面。
 * 顯示玩家配對歷史、勝場數等。
 */
public class StatsGUI {
    
    /**
     * 玩家打開統計界面
     */
    public void openStatsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9 * 5, "個人統計");
        
        // 從 storage 獲取玩家統計
        MatchRecord record = storage.getStats(player);
        
        // 顯示統計
        // ...
        
        player.openInventory(gui);
    }
}
```

## 9. 後端通信 (Backend Communication)

### 9.1 使用 BungeeCord 傳輸

```java
/**
 * 后端的通信機制。
 * 使用 BungeeCord 的 Proxy channel 或 Velocity 的 PluginMessage 進行跨伺服器通信。
 */
public class BackendCommunication {
    
    /**
     * 發送 MatchRequest 到後端
     */
    public void sendMatchRequest(BackendInfo backend, MatchRequest request) {
        ProxiedPlayer proxy = BungeeCord.getInstance().getPlugin("Matchmaking");
        
        // 使用 BungeeCord 發送匹配請求
        // 後端會在另一個 BungeeCord channel 接收
        BungeeCord.getInstance().getPlugin("matchmaking");
        
        // 設置消息包
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        try {
            out.writeUTF("MatchRequest");
            out.writeUTF(request.getSessionId());
            out.writeUTF(request.getGameType());
            out.writeUTF(request.getCustomOptionsJson());
            out.writeUTF(request.getPlayersJson());
            out.writeUTF(request.getPartyId() != null ? request.getPartyId().toString() : "none");
            out.writeUTF(request.getSourceServer());
            out.writeUTF(backend.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // 發送消息包
        proxy.getServer().getChannels().get(backend.getName()).send("matchmaking", out.toByteArray());
    }
}
```

### 9.2 接收後端回傳 (MatchAccepted)

```java
/**
 * 接收 Backend 回傳的 MatchAccepted 消息。
 * 這通常在 Lobby 伺服器的插件中實現。
 */
public class MatchAcceptedHandler {
    
    @EventHandler
    public void onProxyMessage(ProxyChannelMessageEvent event) {
        if (event.getChannel().equals("matchmaking")) {
            ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
            String subChannel = in.readUTF();
            
            if (subChannel.equals("MatchAccepted")) {
                String sessionId = in.readUTF();
                String gameType = in.readUTF();
                String serverName = in.readUTF();
                
                // 通知所有相關玩家開始 teleport
                for (Player player : getParticipants(sessionId)) {
                    player.sendMessage("配對成功！正在 teleport 到後端...");
                    ServerInfo target = BungeeCord.getInstance().getServerInfo(serverName);
                    player.connect(target);
                }
                
                // 更新 MatchState
                clearMatchState(sessionId);
                
                // 關閉 GUI
                for (Player player : getParticipants(sessionId)) {
                    player.closeInventory();
                }
            }
        }
    }
}
```

## 10. 配置 (Configuration)

```yaml
# matchmaking/config.yml

# 數據庫配置
database:
  host: "127.0.0.1"
  port: 3306
  database: "partygame"
  username: "root"
password: "password"
  pool:
    size: 5
    maxLifetime: 1800000  # 30 minutes

# Redis 配置
redis:
  host: "127.0.0.1"
  port: 6379
  database: 0
  password: ""
  channel: "partygame:lobby"
  pool:
    size: 5

# 後端配置
backend:
  # 後端伺服器列表 (Redis 中動態更新)
  servers: []
  # 選址策略 (lowest_load 或 round_robin)
  selection_strategy: "lowest_load"
  # 後端健康檢查間隔 (秒)
  health_check_interval: 30

# 配對配置
matching:
  # 配對佇列 TTL (秒) (玩家超時自動退出)
  queue_ttl: 300
  # 配對超時 (秒) (未匹配到則 timeout)
  match_timeout: 600
  # 最大玩家數 (Party/Team 模式)
  max_players: 8

# 界面配置
gui:
  theme: "dark"
  refresh_interval: 10  # 秒
  stats:
    show_history: true
    max_history: 10

```

## 11. 拓展指南

### 11.1 新增遊戲類型

```java
/**
 * 新增遊戲類型只需要：
 * 1. 在 config.yml 中添加 game 配置
 * 2. 在 lp 中添加對應的 lp 權限
 * 3. 不需要修改任何代碼！
 */

// 示例: 新增 BedWars 遊戲類型
// config.yml
games:
  - id: "bedwars"
    name: "BedWars"
    min_players: 8
    max_players: 32
    game_type: "bedwars"
    custom_options:  # BedWars 特有的自訂欄位
      - key: "team_size"
        type: "integer"
        default: 4
        min: 2
        max: 8
      - key: "gen_type"
        type: "string"
        enum: ["iron", "gold", "diamond", "emerald"]
        default: "iron"

// 對應的 LP 權限
// lp group create bedwars_vip
// lp group setpermission bedwars_vip partygame.match.game.bedwars
// lp group setinheritance bedwars_vip add free

// 之後玩家要玩 BedWars 需要：
// - 有 partygame.match.game.bedwars 權限 (LP)
// - 有 partygame.match.join 權限 (LP)
```

### 11.2 拓展自訂欄位

```java
/**
 * 拓展自訂欄位：
 * 1. 在 config.yml 中添加欄位配置
 * 2. 在 CustomOptionsGUI 中動態顯示
 * 3. 在 GamePayloadBuilder 中打包
 * 4. 後端收到後再解析應用
 */

// 示例: 新增 BedWars 自訂欄位
// config.yml
custom_options:
  - key: "team_size"
    type: "integer"
    default: 4
    min: 2
    max: 8
    description: "每隊人數"
  - key: "gen_type"
    type: "string"
    enum: ["iron", "gold", "diamond", "emerald"]
    default: "iron"
    description: "資源生成類型"

// 後端 (PartyGameCore) 收到後解析：
public class CustomOptionsParser {
    public Map<String, Object> parse(GamePayload payload) {
        return payload.getSettings();
        // { team_size: 4, gen_type: "iron" }
    }
}
```

## 12. 常見問題 (FAQ)

### 12.1 玩家加入配對後消失，如何處理？

處理方式：

1. **玩家斷開連線**: 檢查玩家是否掉線。如果掉線，移除配對狀態。
2. **玩家 teleport 失敗**: 嘗試重新 teleport，或通知後端取消 MatchRequest。
3. **後端無回應**: 超時後通知玩家配對失敗。

### 12.2 Party 配對時成員人數不足如何處理？

處理方式：

1. **等待其他 Party 加入**: 如果 Party 人數不足，嘗試從 solo 隊伍中填補。
2. **通知後端**: 如果 Party 成員數不符合遊戲要求，通知後端取消配對。

## 13. 插件版本資訊 (Version Information)

### 13.1 Maven/Gradle 依賴

```groovy
// build.gradle.kts
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("redis.clients:jedis:5.1.0")
    compileOnly("mysql:mysql-connector-java:8.0.33")
    compileOnly("org.luckperms:luckperms:5.4.102")  // LuckPerms API (optional)
    compileOnly("com.github.placeholderapi:placeholderapi:2.11.6")  // PlaceholderAPI (optional)
}
```
