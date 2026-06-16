# PursWM 專案完整使用文檔

## 專案概觀

PursWM 是一個跨伺服器 Party Game 配對系統，使用 Velocity/BungeeCord 架構，包含 Lobby 伺服器 (標準 Purpur 1.21.7) 和 Backend 伺服器 (SWM Purpur 客製化核心)。

**專案結構**:
```
purswm/
├── 01_purpur-swm-framework/      # 通用 SWM 框架
├── 02_purpur-swm-partygame/      # Party Game 框架
├── 03_world-converter/           # 世界轉換工具
├── 04_plugin-suite/              # 插件套件
│   ├── 04_04_common-lib/         # 共享基礎設施
│   ├── 04_01_matchmaking/        # 配對系統
│   ├── 04_02_partygame-core/     # 遊戲核心
│   └── 04_03_friend/             # 好友系統
└── 文檔/
    ├── architecture.md           # 架構說明
    ├── permission-table.md       # 權限表
    ├── deployment.md             # 部署指南
    └── agent-en.md               # AI 友善英文版
```

## 快速開始

### 1. 環境準備

```bash
# Java 版本要求
java -version  # 需要 Java 21+

# 檢查 MySQL 版本
mysql --version  # 建議 MySQL 8.0+

# 檢查 Redis 版本
redis-cli --version  # 建議 Redis 7.0+
```

### 2. 資料庫初始化

```sql
-- 建立資料庫
CREATE DATABASE partygame CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'partygame'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON partygame.* TO 'partygame'@'localhost';
FLUSH PRIVILEGES;

-- 連接 MySQL 並執行初始 SQL
mysql -u partygame -p partygame < init.sql
```

### 3. 構建專案

```bash
# 進入專案根目錄
cd purswm

# 構建通用框架
cd 01_purpur-swm-framework && ./gradlew build

# 構建 Party Game 框架
cd ../02_purpur-swm-partygame && ./gradlew build

# 構建世界轉換工具
cd ../03_world-converter && ./gradlew build

# 構建插件套件
cd ../04_plugin-suite
# 構建所有插件
./gradlew build
```

### 4. 部署設定

修改 `settings.gradle.kts` 和 `config.yml`:

```yaml
# 資料庫配置
database:
  host: "your_mysql_host"
  port: 3306
  database: "partygame"
  username: "partygame"
  password: "your_password"

# Redis 配置
redis:
  host: "your_redis_host"
  port: 6379
  password: "your_redis_password"
  database: 0
  channel: "partygame:match"
```

## 伺服器架構說明

### Lobby 架構 (標準 Purpur 1.21.7)

**伺服器類型**: 標準 Purpur 1.21.7
安裝插件:
- Matchmaking Plugin (配對系統)
- Friend Plugin (好友系統)
- 必要的工具插件 (LuckPerms, PlaceholderAPI 等)

**啟動指令**:
```bash
java -Xmx4G -Xms4G -XX:+UseG1GC -jar purpur-1.21.7.jar nogui
```

**配置文件位置**:
- `plugins/Matchmaking/config.yml` - 配對配置
- `plugins/Friend/config.yml` - 好友配置
- `plugins/LuckPerms/config.yml` - 權限配置

### Backend 架構 (SWM Purpur 客製化)

**伺服器類型**: 客製化 SWM Purpur 1.21.7 (禁用原生世界)
安裝插件:
- PartyGameCore (遊戲核心)
- 必要的 SWM 相關插件

**啟動指令**:
```bash
java -Xmx4G -Xms4G -XX:+UseG1GC -jar purpur-swm.jar nogui
```

**配置文件位置**:
- `plugins/PartyGameCore/config.yml` - 遊戲核心配置
- `swm_worlds/` - SWM 世界目錄

## 配對系統 (Matchmaking Plugin)

### 功能特色

- [x] 通用配對系統支援多種遊戲類型
- [x] 隊友系統整合 (Party System)
- [x] 自訂房間管理 (Custom Room)
- [x] 後端負載均衡
- [x] 即時狀態監控
- [x] 統計數據顯示
- [x] 權限控制 (基於 LP)

### 權限設定

使用 LuckPerms 設定權限 (不要硬編碼權限):

```bash
# 基本權限
lp user <playername> permission set partygame.match.join true
lp user <playername> permission set partygame.match.custom_room true

# 遊戲類型權限
lp group create vip
lp group setpermission vip partygame.match.custom_room true
lp group setpermission vip partygame.game.party true  # 遊戲類型權限
lp group setpermission vip partygame.game.bedwars true  # 其他遊戲
```

**完整權限列表**: (請查看 `permission-table.md`)

### 後端管理

Lobby 伺服器會自動偵測後端狀態並進行負載均衡。

**後端狀態監控**:
```
/lobby_backend_list  # 列出所有後端及狀態
/lobby_backend_info <server>  # 查看後端詳細資訊
```

**後端自動管理**:
- 自動偵測後端上線/離線
- 負載均衡路由 (最少玩家優先)
- 健康檢查 (每 30 秒)
- 故障自動重連

## 世界轉換工具 (World Converter)

### 使用方式

```bash
# 基本轉換
java -jar world-converter.jar \
  --input /path/to/vanilla/world \
  --output /output/directory \
  --name myworld

# 指定參數
java -jar world-converter.jar \
  --input="./worlds/party_map" \
  --output="./swm_worlds" \
  --name="party_world_01" \
  --data-version=4438
```

### 輸出格式

轉換後輸出:
```
output/
├── party_world_01/          # 每個世界一個目錄
│   ├── world.nbt
│   ├── region/
│   ├── entities/
│   └── data.json
```

### 注意事項

- 支援 Minecraft 1.15 至 1.21 世界格式 (注意高度偏移)
- 需要足夠的磁碟空間 (約 2-3x 原始世界)
- 處理大型世界可能需 5-30 分鐘

## 遊戲開發指南

### 新增遊戲步驟

#### 1. 建立遊戲配置

```yaml
# 在 games/ 目錄下建立 YAML 文件
game_id: "new_game"
name: "New Game"
min_players: 4
max_players: 8
default_template: "party_obby_default"
custom_options:
  rounds: 5  # 可選參數
  time_limit: 300  # 可選參數
  custom_rule: "fast_mode"  # 可選參數
```

#### 2. 實現遊戲介面

```java
// 實現 IGamePlugin 介面
public class NewGamePlugin implements IGamePlugin {
    @Override
    public String getId() { return "new_game"; }

    @Override
    public int getMinPlayers() { return 4; }

    @Override
    public int getMaxPlayers() { return 8; }

    @Override
    public String getDefaultWorldTemplate() { return "new_game_template"; }

    @Override
    public void onGameStart(Session session, List<Player> players) {
        // 遊戲開始邏輯
    }

    @Override
    public void onGameEnd(Session session, List<Player> players) {
        // 遊戲結束邏輯
    }

    @Override
    public Map<String, Object> getDefaultCustomOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("rounds", 5);
        return options;
    }

    @Override
    public List<GameResult> calculateResults(Session session) {
        // 返回計分結果
    }
}
```

#### 3. 註冊遊戲

在 `PartyGameCore` 插件的 `onEnable()` 方法中:

```java
public void onEnable() {
    // 掃描遊戲配置目錄
    File gamesDir = new File(getDataFolder(), "games");

    for (File config : gamesDir.listFiles()) {
        if (config.getName().endsWith(".yml")) {
            // 讀取配置文件建立遊戲實體
            GameConfig gameConfig = loadGameConfig(config);

            // 建立對應的 IGamePlugin 實例
            IGamePlugin newPlugin = new NewGamePlugin();

            // 註冊遊戲
            gameRegistry.register(newPlugin);
        }
    }
}
```

#### 4. 測試遊戲

```
/test_match <game_id> <players>  # 測試配對
/test_world <world_template>     # 測試世界模板
```

### 世界管理

使用 `swm_worlds` 目錄管理 SWM 世界。

**世界模板**: 使用預設模板創建新遊戲實例
**世界池管理**: 自動管理世界加載/卸載
**世界模板定義**: 定義在 `config.yml` 的 `games` 區塊

## 配置詳解

### 1. 資料庫配置

資料庫配置 (`config.yml`):

```yaml
database:
  host: "localhost"
  port: 3306
  database: "partygame"
  username: "partygame"
  password: "your_password"
  schema: "partygame"  # 資料庫 schema (非必需)
  pool:
    size: 5
    max_lifetime: 1800000  # 連接生存 (ms)
    idle_timeout: 600000   # 空閒Timeout (ms)
```

### 2. Redis 配置

Redis 配置 (`config.yml`):

```yaml
redis:
  host: "localhost"
  port: 6379
  password: ""  # 如果有的話
  database: 0
  channel: "partygame:match"
  pool:
    size: 5
```

### 3. 匹配系統配置

匹配配置 (`config.yml`):

```yaml
matchmaking:
  queue_ttl: 300  # 隊列存活時間 (秒)
  max_matches: 8  # 最大匹配
  cooldown: 5     # 匹配冷却 (秒)
  backend:
    strategy: "round_robin"  # 負載均衡策略
  gui:
    refresh_interval: 10  # GUI 秒刷新
```

### 4. 遊戲配置

遊戲配置 (`config.yml`):

```yaml
games:
  - id: "survival"
    name: "Survival"
    min_players: 4
    max_players: 8
    round: 5
    time_per_round: 300
    custom_options:
      rounds:  # 遊戲自訂選項
        type: "int"
        default: 5
        min: 1
        max: 10
```

### 5. 世界管理配置

世界配置 (`config.yml`):

```yaml
worlds:
  world_dir: "swm_worlds"  # SWM 世界目錄
  max_load: 50  # 最大同時載入世界
  auto_unload:  # 自動卸載超時
    enabled: true
    after_seconds: 300  # 超時時間 (秒)
```

## 插件開發指南

### 1. 添加新插件

1. 創建新的 Gradle 模組目錄

2. 創建 `build.gradle.kts`:

```kotlin
plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven(url = "https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(fileTree("dir" to "${projectDir}/../04_plugin-suite/04_04_common-lib/build/libs"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}
```

3. 創建插件主類:

```java
package myplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import co.partygame.common.config.ConfigManager;
import co.partygame.common.redis.RedisManager;

public class MyPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private RedisManager redisManager;

    @Override
    public void onEnable() {
        // 初始化配置
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // 初始化 Redis
        redisManager = new RedisManager(this);
        redisManager.connect();
        // 初始化其他組件
        // ...
    }

    @Override
    public void onDisable() {
        // 清理資源
        if (redisManager != null) {
            redisManager.disconnect();
        }
        // 其他清理
    }
}
```

4. 創建 `plugin.yml`:

```yaml
name: MyPlugin
version: 1.0.0
author: YourName
main: myplugin.MyPlugin
api-version: 1.21
```

### 2. 插件通信

通過 BungeeCord Plugin Messaging 進行伺服器間通信。

**發送消息**:

```java
// 發送匹配請求到後端
ByteArrayDataOutput out = ByteStreams.newDataOutput();
out.writeUTF("MatchRequest");
out.writeUTF(sessionId);
out.writeUTF(gameType);
// ... 其他數據

ByteArrayDataInput in = ByteStreams.newDataInput(data);
String sub = in.readUTF();
// 處理回應...
```

**接收消息**:

```java
@Subscribe
public void onMessage(PluginMessageEvent event) {
    if (event.getTag().equals("BungeeCord")) {
        if (event.getMessage().equals("MatchResponse")) {
            // 處理回應
        }
    }
}
```

**配置消息通道**:

在 `config.yml`:

```yaml
bungeecord:
  enabled: true
  channel: "partygame"
  server_ids: ["lobby1", "backend1", "backend2"]
```

### 3. 插件配置

使用 `ConfigManager` 管理 YAML 配置文件。

```java
// 初始化配置管理器
ConfigManager config = new ConfigManager(this);

// 載入配置
config.loadConfig();

// 設置值
config.set("key", "value");

// 獲取值
String value = config.getString("key");
int intValue = config.getInteger("key");
boolean bool = config.getBoolean("key");

// 保存配置
config.saveConfig();

// 重载配置
config.reloadConfig();
```

### 4. 插件事件监听

使用 Bukkit 事件 API 監聽遊戲事件。

```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    // 處理玩家加入邏輯
    // ...

    // 發送消息到 Lobby
    BungeeMessenger.broadcast(
        "player_joined",
        player.getName()
    );
}
```

## 監控與除錯

### 1. 日誌系統

- **Lobby 日誌**: `/logs/matchmaking.log`
- **Backend 日誌**: `/logs/partgamecore.log`
- **世界轉換日誌**: `/logs/converter.log`

日誌級別配置 (`logback.xml`):

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.classic.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss} [%thread] %-5level %logger - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="co.partygame" level="INFO" />

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

### 2. 狀態監控

- **Lobby 狀態**: `/lobby_status`
- **Backend 狀態**: `/backend_status`
- **世界池狀態**: `/worldpool_status`
- **隊列狀態**: `/queue_stats`
- **遊戲狀態**: `/game_stats`

**監控 API** (用於外部監控工具):

```
GET /api/monit
```

### 3. 除錯工具

**Lobby 除錯命令**:
```
/match debug  # 顯示配對除錯資訊
/match verbose true  # 開啟除錯模式
```

**Backend 除錯命令**:
```
/partygame debug session <session_id>  # 會話除錯
/partygame debug world <world_name>    # 世界除錯
```

## 部署建議

### 1. 硬體建議

- **CPU**: 4+ Core (Lobby / 每個 Backend)
- **RAM**: 8GB+ (Lobby) / 16GB+ (Backend)
- **Disk**: 100GB+ SSD

### 2. 網路拓撲

```
Internet ──► Velocity Proxy ──► Lobby Servers ──► Backend Servers ──► SWM Worlds
```

### 3. 負載均衡

使用 Velocity Proxy 進行負載均衡 (配置參見 `velocity.toml`).

### 4. 安全建議

- 防火牆限制 Redis MySQL 埠
- 使用強密碼
- 啟用 SSL 加密 (生產環境)
- 定期備份