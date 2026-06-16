# 插件開發者指南

## 1. 新增遊戲類型

### 1.1 步驟

```
1. 新增遊戲配置文件 (games/<game_id>.yaml)
2. 創建遊戲插件 (實現 IGamePlugin 接口)
3. 註冊遊戲到 GameRegistry
4. 配置 LuckPerms 權限
```

### 1.2 範例

```yaml
# games/survival.yaml
id: "survival"
name: "Survival Game"
min_players: 4  // 最小玩家數
max_players: 8  // 最大玩家數
world_template: "survival_world"  // 世界模板 (對應 .slimeworld 文件)
custom_options:  // 遊戲自訂選項
  - key: "rounds"
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
    enum: ["easy", "normal", "hard"]
    default: "normal"
  - key: "pvp"
    type: "boolean"
    default: true
  - key: "death_penalty"
    type: "integer"
    default: 0  // 0 = 無懲罰, -1 = 扣分, 等
```

### 1.3 實現遊戲插件 (實現 IGamePlugin 接口)

```java
public class SurvivalGamePlugin implements IGamePlugin {
    
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
        return "survival_world";  // 對應 .slimeworld 文件
    }
    
    @Override
    public Map<String, Object> getDefaultCustomOptions() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("rounds", 5);
        defaults.put("time_per_round", 120);
        defaults.put("difficulty", "normal");
        defaults.put("pvp", true);
        defaults.put("death_penalty", 0);
        return defaults;
    }
    
    @Override
    public List<String> getGameTypes() {
        return List.of("survival");
    }
    
    @Override
    public void onReady(List<Player> players, Map<String, Object> customOptions) {
        // 準備就緒
        // 通知 Lobby 準備開始
        PartyGameCore.getLobbyProtocolHandler().notifyGameReady(  // 通知 Lobby 準備開始
            players,
            customOptions
        );
    }
    
    @Override
    public void onStart(List<Player> players, Map<String, Object> customOptions) {
        // 遊戲開始
        // 將玩家 teleport 到世界
        // 開始倒數
        // 通知 Lobby 倒數開始
        PartyGameCore.getLobbyProtocolHandler().notifyGameStart(
            players,
            customOptions
        );
    }
    
    @Override
    public void onRoundStart(List<Player> players, int round, Map<String, Object> customOptions) {
        // 回合開始
        // 通知 Lobby 通知 Lobby
        PartyGameCore.getLobbyProtocolHandler().notifyRoundStart(
            players,
            round,
            customOptions
        );
    }
    
    @Override
    public void onRoundEnd(List<Player> players, int round, Map<String, Object> customOptions) {
        // 回合結束
        // 通知 Lobby 通知 Lobby
        PartyGameCore.getLobbyProtocolHandler().notifyRoundEnd(
            players,
            round,
            customOptions
        );
    }
    
    @Override
    public void onEnd(List<Player> players, Map<String, Object> customOptions) {
        // 遊戲結束
        // 計算結果
        List<GameResult> results = calculateResults(players);
        
        // 通知 Lobby 遊戲結束
        PartyGameCore.getLobbyProtocolHandler().notifyGameEnd(
            players,
            results
        );
    }
    
    @Override
    public void onCancel(List<Player> players) {
        // 遊戲被取消
        // 將玩家 teleport 回 Lobby
        PartyGameCore.getLobbyProtocolHandler().notifyGameCancelled(
            players
        );
    }
    
    /**
     * 計算遊戲結果
     */
    private List<GameResult> calculateResults(List<Player> players) {
        // 例如：存活時間越長，分數越高；殺敵越多，分數越高等
        // 實際邏輯根據遊戲需求而定
        List<GameResult> results = new ArrayList<>();
        for (Player player : players) {
            int score = calculatePlayerScore(player);
            results.add(new GameResult(player.getUniqueId(), score));
        }
        return results;
    }
    
    /**
     * 計算玩家分數 (根據存活時間、殺敵數等)
     */
    private int calculatePlayerScore(Player player) {
        // 實現邏輯
        return 0;
    }
}
```

## 1.4 註冊到 GameRegistry

```java
public class PartyGameCorePlugin extends JavaPlugin {
    
    @Override
    public void onEnable() {
        // 加載所有遊戲
        loadGames();
    }
    
    /**
     * 加載所有遊戲
     */
    private void loadGames() {
        // 掃描 games/ 目錄
        Path gamesPath = getDataFolder().toPath().resolve("games");
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gamesPath, "*.yaml")) {
            for (Path yamlFile : stream) {
                // 解析 yaml 文件
                GameConfig config = loadGameConfig(yamlFile);
                
                // 加載對應的 .slimeworld 文件
                loadSlimeWorld(yamlFile).forEach(world -> {
                    // 註冊到 GameRegistry
                    gameRegistry.register(world);
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

## 1.5 配置 LuckPerms 權限

```
# 在 LuckPerms 中設置
lp group create game_survival
lp group setpermission game_survival partygame.match.game.survival

# 或使用 ladder
ladder create game_ladder
ladder addnode game_ladder game_basic game_survival
ladder addnode game_ladder game_basic game_zombie
```
