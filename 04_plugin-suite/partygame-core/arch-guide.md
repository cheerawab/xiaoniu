# 後端 Party Game 核心架構指南 (Backend Core Architecture Guide)

## 1. 核心理念 (Concept)

後端核心是 Party Game 伺服器的基礎框架，負責控制和管理整個遊戲流程。它的主要功能是：

1. 接收來自 Lobby 的配對請求 (MATCH_REQUEST)
2. 管理 SWM 世界池 (World Pool)
3. 分配世界給遊戲會話 (Session)
4. 管理遊戲的開始、進行、結束階段
5. 通知 Lobby 遊戲完成 (via 遊戲完成事件)

## 2. 核心架構

```
┌───────────────────────────────────────────────────────────┐
│  PartyGamePlugin                                             │
│  ────────────────────                                          │
│  ┌──────────┐  ┌─────────────┐  ┌──────────┐  │
│  │Session   │  │Game    │  │World  │  │Protocol  │  │
│  │Manager   │ │Registry │ │Pool  │ │Handler  │  │
│  │          │ │          │ │      │ │         │  │
│  │- Start   │ │- Game 1  │ │- Pool│ │- Lobby  │  │
│  │- Run    │ │- Game 2  │ │- Allocate│ │- Backend │  │
│  │- End     │ │- ...     │ │- Free │ │- Match │  │
│  │- Notify  │ └──────────┘ └──────────┘ └──────────┘  │
│  └──────────┘                                        │
└───────────────────────────────────────────────────────────┘
```

## 3. 遊戲會話管理 (Session Management)

### 3.1 SessionManager

```java
/**
 * 遊戲會話管理器。
 * 負責追蹤和管理所有遊戲會話 (Game Sessions)。
 * 每個會話代表一組玩家正在進行的遊戲。
 */
public class SessionManager {
    private final Map<String, Session> sessions = new HashMap<>();
    
    /**
     * 創建新的遊戲會話 (當配對成功後)
     */
    public GameSession createSession(String sessionId, String gameType, 
                                       String worldName, List<Player> players) {
        // 創建新會話
        GameSession session = new GameSession();
        session.setSessionId(sessionId);
        session.setGameType(gameType);
        session.setWorldName(worldName);
        session.setPlayers(players);
        session.setState(GameState.WAITING);  // INIT, RUNNING, ENDING
        
        // 將會話加入 map
        sessions.put(sessionId, session);
        
        return session;
    }
    
    /**
     * 獲取會話
     */
    public GameSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    /**
     * 銷毀會話 (遊戲結束後)
     */
    public void destroySession(String sessionId) {
        GameSession session = sessions.remove(sessionId);
        if (session != null) {
            // 通知 Lobby 遊戲完成
            notifyLobbySessionEnd(session);
            
            // 回收世界
            worldPoolManager.returnWorld(session.getWorldName());
        }
    }
}
```

### 3.2 遊戲會話狀態 (GameSession State Machine)

```
INIT (初始化)
  │
  ├── 等待玩家進入 → 分配世界 → 通知後端
  │
  ▼
GAME_STARTING (開始階段)
  │
  └── 倒數 → 進入遊戲
  │
  ▼
GAME_STARTED (遊戲進行階段)
  │
  ├── 遊戲邏輯執行中
  │
  ▼
GAME_ENDING (結束階段)
  │
  ├── 計算結果
  │
  ▼
GAME_ENDED (遊戲結束)
  └── 通知 Lobby → 回收世界 → 銷毀會話
```

## 4. 遊戲註冊中心 (GameRegistry)

### 4.1 遊戲邏輯

```java
/**
 * 遊戲註冊中心。
 * 管理所有 registered 遊戲插件 (IGamePlugin)。
 * 
 * 每個遊戲插件都需要繼承 IGamePlugin interface。
 */
public class GameRegistry {
    
    private final Map<String, IGamePlugin> registeredGames = new HashMap<>();
    
    /**
     * 註冊一個遊戲插件
     */
    public void register(IGamePlugin gamePlugin) {
        // 獲取遊戲 ID
        String id = gamePlugin.getGameId();
        registeredGames.put(id, gamePlugin);
        
        System.out.println("Game registered: " + id);
    }
    
    /**
     * 獲取遊戲
     */
    public Optional<IGamePlugin> get(String id) {
        // 獲取遊戲
        return Optional.ofNullable(registeredGames.get(id));
    }
}
```

### 4.2 IGamePlugin (遊戲插件 interface)

```java
/**
 * 遊戲插件接口。
 * 所有遊戲插件都需要實現這個接口。
 * 
 * 每個遊戲插件可以定義：
 * - 最小/最大玩家数
 * - 世界模板
 * - 遊戲規則 (勝利條件等)
 * - 自訂選項 (例如 round count, time limit, etc.)
 */
public interface IGamePlugin {
    
    /**
     * 獲取遊戲 ID
     */
    String getId();
    
    /**
     * 獲取遊戲名稱
     */
    String getName();
    
    /**
     * 獲取最小玩家数
     */
    int getMinPlayers();
    
    /**
     * 獲取最大玩家数
     */
    int getMaxPlayers();
    
    /**
     * 獲取預設世界模板
     */
    String getDefaultWorldTemplate();
    
    /**
     * 獲取預設自訂選項 (默認情況下由 JSON config 定義)
     */
    Map<String, Object> getDefaults();
    
    /**
     * 驗證自訂選項 (例如驗證回合數是否合法)
     * 如果選項無效，拋出 IllegalArgumentException
     */
    void validateOptions(Map<String, Object> customOptions);
    
    /**
     * 遊戲準備就緒時呼叫 (所有玩家準備好)
     * 準備就緒 -> 分配世界
     */
    void onReady(List<Player> players, Map<String, Object> customOptions);
    
    /**
     * 遊戲開始時呼叫
     */
    void onStart(List<Player> players, Map<String, Object> customOptions);
    
    /**
     * 遊戲回合開始時呼叫 (如果有回合制)
     */
    void onRoundStart(List<Player> players, int round, Map<String, Object> customOptions);
    
    /**
     * 遊戲回合結束時呼叫
     */
    void onRoundEnd(List<Player> players, int round, Map<String, Object> customOptions);
    
    /**
     * 遊戲結束後呼叫
     */
    void onEnd(List<Player> players, Map<String, Object> customOptions);
    
    /**
     * 計算遊戲結果 (計分)
     */
    List<GameResult> calculateResults(List<Player> players);
    
    /**
     * 遊戲被撤銷時呼叫 (例如取消配對)
     */
    void onCancel(List<Player> players);
}
```

## 5. 世界池管理 (WorldPoolManager)

### 5.1 WorldPoolManager

```java
/**
 * 世界池管理器。
 * 管理所有可用的 SWM 世界。
 * 每個遊戲類型可以有多個世界。
 */
public class WorldPoolManager {
    
    private final Map<String, Set<SlimeWorld>> worldPools = new HashMap<>();
    
    /**
     * 當伺服器加載時，掃描 swm_worlds/ 目錄並加載所有 .slimeworld 文件
     */
    public void loadWorlds(Path worldsDir) {
        // 掃描 worldsDir 目錄
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(worldsDir, "*.slimeworld")) {
            for (Path slimeFile : stream) {
                String worldName = slimeFile.getFileName().toString();
                
                // 從 SlimeWorldManager 加載世界
                SlimeWorld world = slimeWorldLoader.load(worldName);
                
                // 將世界加入對應的 pool
                worldPools.computeIfAbsent(world.getCategory(), k -> new HashSet<>())
                    .add(world);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 從 worldPool 中分配一個 world 給會話
     */
    public SlimeWorld allocate(String gameCategory) {
        Set<SlimeWorld> pool = worldPools.get(gameCategory);
        if (pool == null || pool.isEmpty()) {
            throw new NoWorldAvailableException("No world available for category: " + gameCategory);
        }
        
        // 選擇一個 world
        SlimeWorld world = pool.iterator().next();
        pool.remove(world);
        
        return world;
    }
    
    /**
     * 回世界 pool  (遊戲結束後)
     */
    public void returnWorld(String gameCategory, SlimeWorld world) {
        Set<SlimeWorld> pool = worldPools.computeIfAbsent(gameCategory, k -> new HashSet<>());
        pool.add(world);
        
        // 通知 Lobby 遊戲完成
    }
}
```

## 6. 遊戲配對接收 (Lobby Protocol Handler)

### 6.1 接收 MatchRequest

```java
/**
 * 處理 Lobby 發送的 MatchRequest 消息。
 * 當 Lobby 收到玩家的配對請求後，會發送 MatchRequest 到後端。
 * 後端收到後，分配世界並開始遊戲。
 */
public class LobbyProtocolHandler {
    
    private final GameRegistry gameRegistry;
    private final WorldPoolManager worldPool;
    private final SessionManager sessionManager;
    
    /**
     * 接收 MatchRequest 消息
     */
    public void onMatchRequest(MatchRequest request) {
        // 1. 獲取遊戲類型
        String gameId = request.getGameId();
        String gameCategory = gameId;  // 例如 "survival"
        
        // 2. 驗證遊戲是否已註冊
        IGamePlugin gamePlugin = gameRegistry.get(gameId);
        if (gamePlugin == null) {
            log.warning("Game not found: " + gameId);
            return;
        }
        
        // 3. 驗證玩家數量是否滿足要求
        if (request.getPlayers().size() < gamePlugin.getMinPlayers()) {
            log.warning("Not enough players for game: " + gameId);
            return;
        }
        if (request.getPlayers().size() > gamePlugin.getMaxPlayers()) {
            log.warning("Too many players for game: " + gameId);
            return;
        }
        
        // 4. 驗證自訂選項是否合法
        Map<String, Object> customOptions = request.getCustomOptions();
        gamePlugin.validateOptions(customOptions);
        
        // 5. 分配世界
        SlimeWorld world = worldPool.allocate(gameCategory);
        
        // 6. 創建遊戲會話
        GameSession session = sessionManager.createSession(
            request.getSessionId(),
            gameId,
            world.getName(),
            request.getPlayers()
        );
        
        // 7. 通知 Lobby 配對成功 (分配了世界)
        sendMatchAccepted(request);
        
        // 8. 通知 Lobby 將玩家 teleport 到後端伺服器
        notifyLobbyTeleport(request, gameCategory);
        
        // 9. 開始遊戲 (將玩家傳送至世界)
        gamePlugin.onReady(request.getPlayers(), customOptions);
    }
    
    /**
     * 發送 MatchAccepted 消息到 Lobby
     */
    private void sendMatchAccepted(MatchRequest request) {
        MatchAcceptedResponse response = new MatchAcceptedResponse();
        response.setSessionId(request.getSessionId());
        response.setGameCategory(request.getGameCategory());
        response.setServerName(getServerName());  // 當前後端伺服器名稱
        response.setPlayers(request.getPlayers());  // 玩家列表
        response.setWorldName(world.getName());  // 分配的世界
        
        // 使用 BungeeCord/Velocity 發送消息包
        bungeeMessenger.sendMatchAccepted(request.getSourceServer(), response);
    }
    
    /**
     * 通知 Lobby 將玩家 teleport 到後端伺服器
     */
    private void notifyLobbyTeleport(MatchRequest request, String gameCategory) {
        // Lobby 端會將玩家 teleport 到後端伺服器
        // 這是 Lobby 端的責任，後端只需要確保 MatchAccepted 消息已發送
    }
}
```

## 7. 遊戲流程

### 7.1 完整流程

```
1. Lobby 發送 MatchRequest 到後端
2. 後端收到 MatchRequest，分配世界，開始遊戲
3. 後端通知 Lobby 配對成功 (MatchAccepted)
4. Lobby 通知玩家 teleport 到後端伺服器
5. 後端收到 MatchAccepted 回應，通知 Lobby 將玩家 teleport 到後端伺服器
6. Lobby 通知玩家 teleport 到後端伺服器
7. 後端將玩家傳送至世界
8. 後端通知 Lobby 遊戲開始
9. 後端執行遊戲邏輯
10. 遊戲結束後，後端通知 Lobby 遊戲結束
11. 後端回收世界，回到 pool
```

### 7.2 詳細流程

```
Lobby 發送 MatchRequest 到後端
    │
    ▼
後端收到 MatchRequest
    ├── 驗證遊戲類型是否已註冊
    ├── 驗證玩家數量
    ├── 驗證自訂選項
    └── 分配世界
    │
    ▼
發送 MatchAccepted 回應到 Lobby
    │
    ▼
Lobby 通知玩家 teleport 到後端伺服器
    │
    ▼
後端收到 MatchAccepted 回應
    ├── 通知 Lobby 將玩家 teleport 到後端伺服器
    └── 通知 Lobby 將玩家 teleport 到後端伺服器
    │
    ▼
Lobby 通知玩家 teleport 到後端伺服器
    │
    ▼
後端將玩家傳送至世界
    │
    ▼
後端通知 Lobby 遊戲開始
    │
    ▼
後端執行遊戲邏輯
    ├── 初始化遊戲 (初始化世界、spawn 玩家)
    ├── 遊戲開始 (開始倒數)
    ├── 遊戲回合 (如果有回合制)
    │   ├── Round 1 → Round 2 → ... → Round N
    │   └── 每回合結束時通知 Lobby
    └── 遊戲結束 (計算結果)
        │
        ▼
        通知 Lobby 遊戲結束
        │
        ▼
        回收世界 (回到 pool)
```

## 8. 遊戲插件範例 (Example Game Plugin)

### 8.1 基本範例

```java
/**
 * 生存遊戲插件範例。
 * 這是一個簡單的遊戲插件，演示如何實現 IGamePlugin interface。
 */
public class SurvivalGamePlugin implements IGamePlugin {
    
    private final PartyGamePlugin mainPlugin;
    
    public SurvivalGamePlugin(PartyGamePlugin mainPlugin) {
        this.mainPlugin = mainPlugin;
    }
    
    @Override
    public String getId() {
        return "survival";
    }
    
    @Override
    public String getName() {
        return "Survival Game";
    }
    
    @Override
    public int getMinPlayers() {
        return 4;
    }
    
    @Override
    public int getMaxPlayers() {
        return 8;
    }
    
    @Override
    public String getDefaultWorldTemplate() {
        return "world.survival";  // 對應 .slimeworld 檔案命名
    }
    
    @Override
    public Map<String, Object> getDefaults() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("rounds", 5);
        defaults.put("time_per_round", 120);
        defaults.put("difficulty", "normal");
        defaults.put("pvp", true);
        return defaults;
    }
    
    @Override
    public void validateOptions(Map<String, Object> customOptions) {
        // 驗證回合數
        Object roundsObj = customOptions.get("rounds");
        if (roundsObj != null) {
            int rounds = Integer.parseInt(roundsObj.toString());
            if (rounds < 1 || rounds > 10) {
                throw new IllegalArgumentException("rounds must be between 1 and 10");
            }
        }
        
        // 驗證時間限制
        Object timeObj = customOptions.get("time_per_round");
        if (timeObj != null) {
            int time = Integer.parseInt(timeObj.toString());
            if (time < 30 || time > 600) {
                throw new IllegalArgumentException("time_per_round must be between 30 and 600");
            }
        }
    }
    
    @Override
    public void onReady(List<Player> players, Map<String, Object> customOptions) {
        // 所有玩家準備就緒
        // 通知後端開始遊戲
        mainPlugin.getGameSession().notifyGameReady();
    }
    
    @Override
    public void onStart(List<Player> players, Map<String, Object> customOptions) {
        // 遊戲開始
        // 將玩家傳送至世界
        TeleportService.teleportPlayers(players, session.getWorld());
        
        // 開始倒數
        mainPlugin.getGameSession().startCountdown(10, new CountdownCallback() {
            @Override
            public void onTick(int remaining) {
                // 通知 Lobby 倒數剩餘時間
                mainPlugin.getGameSession().notifyCountdown(remaining);
            }
            
            @Override
            public void onComplete() {
                // 倒數完成，開始遊戲
                // 初始化地圖、spawn 玩家等
                mainPlugin.getGameSession().startRound(1);
            }
        });
    }
    
    @Override
    public void onRoundStart(List<Player> players, int round, Map<String, Object> customOptions) {
        // 回合開始
        // 放置玩家、 spawn 怪獸、等等
        mainPlugin.getGameSession().notifyRoundStart(round);
        
        // 等待回合結束
        int timeLimit = Integer.parseInt(customOptions.get("time_per_round").toString());
        mainPlugin.getGameSession().startRoundTimer(timeLimit, () -> {
            onRoundEnd(players, round, customOptions);
        });
    }
    
    @Override
    public void onRoundEnd(List<Player> players, int round, Map<String, Object> customOptions) {
        // 回合結束
        // 計算計分、通知 Lobby
        List<GameResult> results = calculateResults(players);
        mainPlugin.getGameSession().notifyRoundEnd(round, results);
        
        // 如果有更多回合，繼續下一回合
        int totalRounds = Integer.parseInt(customOptions.get("rounds").toString());
        if (round < totalRounds) {
            mainPlugin.getGameSession().startRound(round + 1);
        } else {
            // 遊戲結束
            onEnd(players, customOptions);
        }
    }
    
    @Override
    public void onEnd(List<Player> players, Map<String, Object> customOptions) {
        // 遊戲結束
        // 計算最終計分
        List<GameResult> finalResults = calculateResults(players);
        mainPlugin.getGameSession().notifyGameEnd(finalResults);
        
        // 通知 Lobby 玩家 teleport 回 Lobby
        mainPlugin.getGameSession().notifyLobbyTeleport();
    }
    
    @Override
    public List<GameResult> calculateResults(List<Player> players) {
        // 計算結果
        // 例如：存活時間越長，分數越高；殺敵越多，分數越高等
        List<GameResult> results = new ArrayList<>();
        for (Player player : players) {
            int score = calculatePlayerScore(player);
            results.add(new GameResult(player.getUniqueId(), score));
        }
        return results;
    }
    
    @Override
    public void onCancel(List<Player> players) {
        // 遊戲被取消 (例如取消配對)
        // 將玩家 teleport 回 Lobby
        mainPlugin.getGameSession().notifyLobbyTeleport();
    }
}
```

## 9. 熱重載 (Hot Reload)

### 9.1 HotReloadManager

```java
/**
 * 熱重載管理器。
 * 定期檢查文件變更，如果有變更則自動重載插件或配置。
 */
public class HotReloadManager {
    
    private final ScheduledExecutorService scheduler;
    private final FileSystemWatcher fileWatcher;
    
    /**
     * 啟動熱重載 (例如每 5 秒檢查一次)
     */
    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            // 檢查文件變更
            Map<String, Path> changedFiles = fileWatcher.getChangedFiles();
            
            if (!changedFiles.isEmpty()) {
                // 通知相關組件重載
                for (Map.Entry<String, Path> entry : changedFiles.entrySet()) {
                    String filePath = entry.getKey();
                    Path filePath = entry.getValue();
                    
                    // 根據文件類型決定重載哪些組件
                    if (filePath.getName().endsWith(".java")) {
                        // 如果是 .java 文件，重載對應的遊戲邏輯插件
                        reloadGamePlugin(filePath);
                    } else if (filePath.getName().endsWith(".json")) {
                        // 如果是 .json 文件，重載遊戲配置
                        reloadGameConfig(filePath);
                    } else if (filePath.getName().endsWith(".yml")) {
                        // 如果是 .yml 文件，重載一般配置
                        reloadConfig(filePath);
                    }
                }
                
                // 清空 changedFiles
                fileWatcher.clearChangedFiles();
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 重載遊戲邏輯插件
     */
    private void reloadGamePlugin(Path changedFile) {
        // 解析文件名，找到對應的 GamePlugin
        String pluginName = changedFile.getFileName().toString().replace(".java", "");
        if (pluginName.contains("Game") || pluginName.contains("game")) {
            // 找到對應的 GamePlugin
            IGamePlugin plugin = gameRegistry.get(pluginName);
            if (plugin != null) {
                // 通知 plugin 重新載入
                plugin.onHotReload();
                
                System.out.println("GamePlugin reloaded: " + pluginName);
            }
        }
    }
    
    /**
     * 重載遊戲配置
     */
    private void reloadConfig(Path changedFile) {
        // 重新讀取配置文件
        gameConfig.reload(changedFile);
        
        System.out.println("Config reloaded: " + changedFile);
    }
}
```

## 10. 配置 (Configuration)

```yaml
# config.yml

# World Pool 配置
world_pool:
  world_folder: ./swm_worlds/  # 存放 .slimeworld 的目錄
  max_worlds_per_game: 3       # 每個遊戲最大世界數 (用於輪流使用)
  world_timeout: 300           # 世界空閒超時時間 (秒)，超過後自動卸載

# 遊戲配置
games:
  - id: "survival"
    name: "Survival Game"
    worlds: ["survival_world_1.slimeworld", "survival_world_2.slimeworld"]
    custom_options:
      key: "rounds"
        type: "integer"
        default: 5
        min: 1
        max: 10
      - key: "time_per_round"
        type: "integer"
        default: 120
        min: 30
        max: 600
      - key: "difficulty"
        type: "string"
        values: ["easy", "normal", "hard"]
      - key: "pvp"
        type: "boolean"
        default: true

# 後端配置
backend:
  id: "backend1"  # 唯一 ID
  name: "Backend 1"  # 顯示名稱 (用於 Lobby GUI)
  
  # 世界池配置
  world_pool:
    world_folder: ./swm_worlds/
    
  # Redi 配置
  redis:
    host: "127.0.0.1"
    port: 6379
    database: 0
    channel: "partygame:backend"
    
  # Lobby 配置
  lobby:
    protocol:
      enabled: true  # 是否啟用 Lobby 協議
    servers: ["lobby1"]  # 可連線的 Lobby 伺服器
    
  # 熱重載配置
  hotreload:
    enabled: true
    interval: 5  # 秒 (檢查文件變更的間隔)
```
