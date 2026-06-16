# 部署指南 (Deployment Guide)

## 1. 環境需求

### Java
- **Java 21** (Paper/Purpur 1.21 系列要求)

### 必要軟體
- Velocity (Proxy)
- Purpur 1.21.7 (Lobby 伺服器)
- MySQL 8.0 (數據資料庫)
- Redis 7.0 (隊列/會話)
- LuckPerms (權限管理)

### 伺服器建議配置
- CPU: 4+ cores
- RAM: 8GB+
- SSD: Required
- 網路: 低延遲

## 2. Lobby 伺服器部署

### 2.1 安裝 Purpur

```bash
# 1. 創建目錄
mkdir lobby && cd lobby

# 2. 下載 Purpur 1.21.7
wget https://github.com/PurpurMC/Purpur/releases/download/1.21.7/purpur-1.21.7.jar

# 3. 啟動一次以生成配置文件
java -Xmx4G -Xms4G -jar purpur-1.21.7.jar nogui

# 4. 接受 EULA
echo "eula=true" > eula.txt
```

### 2.2 安裝必要插件

```bash
# 將插件複製到 plugins/ 目錄
mkdir -p plugins
cp path/to/common-lib.jar plugins/McCommon.jar
cp path/to/auth.jar plugins/McAuth.jar
cp path/to/matchmaking.jar plugins/McMatchmaking.jar
cp path/to/friend.jar plugins/McFriend.jar
```

**注意**: Lobby 伺服器只需要以下插件：
- `\04_04_common-lib/`
- `\04_04_auth/`
- `\04_01_matchmaking/`
- `\04_03_friend/`

不需要 `partygame-core` (那是後端的插件)。

### 2.3 配置 Lobby

編輯 `plugins/McMatchmaking/config.yml`:

```yaml
# Matchmaking 配置
database:
  host: "127.0.0.1"
  port: 3306
  database: "partygame"
  username: "root"
  password: "password"
  pool:
    size: 5
    max_lifetime: 1800000

redis:
  host: "127.0.0.1"
  port: 6379
  database: 0
  password: ""
  channel: "partygame:lobby"
  pool:
    size: 5

backend:
  # 後端伺服器列表 (Redis 中動態更新)
  # 這裡只需要配置 Redis
  servers: []
  
gui:
  theme: dark
  refresh_interval: 10  # 秒
```

### 2.4 配置 LuckPerms

```bash
# 1. 安裝 LuckPerms (如果還沒安裝)
wget -O plugins/LuckPerms.jar https://minecraft.luckperms.net/download/5.4.102

# 2. 配置 Group Inheritance
lp group create vip
lp group setinheritance vip add default
lp group setpermission vip partygame.match.priority
lp group setpermission vip partygame.match.custom_room

lp group create mvp
lp group setinheritance mvp add vip
lp group setpermission mvp partygame.game.advanced

lp group create mvp_plus
lp group setinheritance mvp_plus add mvp
lp group setpermission mvp_plus partygame.game.premium
```

## 3. Backend 伺服器部署

### 3.1 構建 SWM Purpur

```bash
# 1. 克隆專案
git clone <your repo>
cd purswm

# 2. 構建 02_purpur-swm-partygame
cd 02_purpur-swm-partygame
../gradlew build

# 3. 複製構建好的 jar 到伺服器
cp build/libs/purpur-swm-partygame.jar /path/to/lobby-server/
```

**或**：

```bash
# 1. 直接複製 02_purpur-swm-partygame 構建
cp 02_purpur-swm-partygame/build/libs/purpur-swm-partygame.jar /path/to/backend/

# 2. 啟動
java -Xmx4G -Xms4G -jar purpur-swm-partygame.jar nogui
```

### 3.2 配置 Backend

```yaml
# 在 02_purpur-swm-partygame 的配置中
swm:
  world_folder: ./swm_worlds/  # 存放 .slimeworld 的目錄
  
game:
  worlds_per_game: 3  # 每個遊戲 3 個世界 (輪流使用)
  max_players_per_world: 8
  world_timeout: 300  # 秒 (空閒世界自動卸載)

backend:
  id: "backend1"  # 唯一 ID
  redis:
    host: "127.0.0.1"
    port: 6379
    channel: "partygame:backend"
  lobby:
    protocol:
      enabled: true
      message_format: bungeecord  # 或 velocity
    servers: ["lobby1"]  # 連接的 Lobby 伺服器
  hotreload:
    enabled: true
    interval: 5  # 秒 (檢測文件變更)
    plugins: ["custom", "games", "config"]  # 可熱重載的組件

# 遊戲配置
games:
  - id: "survival"
    name: "Survival Game"
    worlds: ["survival_world_1.slimeworld", "survival_world_2.slimeworld"]
    players: 4
    rounds: 5
  - id: "obby"
    name: "Obby"
    worlds: ["obby_world_1.slimeworld", "obby_world_2.slimeworld"]
    players: 8
    rounds: 3
  # ... 其他遊戲
```

## 4. World Converter 部署

```bash
# 1. 構建 world-converter
cd 03_world-converter
../gradlew build

# 2. 運行轉換
java -jar world-converter.jar \
  --input /path/to/worlds/ \
  --output ./swm_worlds/ \
  --format 1.21.7

# 或直接使用 JAR (如果打包了 main class)
java -jar world-converter.jar
```

## 5. Redis 配置

```bash
# 安裝 Redis
sudo apt update
sudo apt install redis-server

# 啟動
systemctl start redis
systemctl enable redis

# 測試連線
redis-cli ping
> PONG

# 測試 Pub/Sub
redis-cli subscribe partygame:lobby
redis-cli publish partygame:lobby '{"test":"data"}'
```

## 6. MySQL 配置

```sql
-- 創建資料庫
CREATE DATABASE partygame CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 創建用戶
CREATE USER 'partygame'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON partygame.* TO 'partygame'@'localhost';

-- 應用 SQL (如果有 .sql 文件)
USE partygame;
SOURCE /path/to/schema.sql;
```

## 7. Velocity 配置

```toml
# velocity.toml
[proxies]
ping = { pass_through = false }
compression_threshold = 256
compression_throttle_limit = 256

# 定義伺服器
[servers]
lobby1 = ["127.0.0.1:25565"]
backend1 = ["127.0.0.1:25566"]
backend2 = ["127.0.0.1:25567"]
backend3 = ["127.0.0.1:25568"]

# 路由表
[forced_hosts]
lobby.example.com = ["lobby1"]
backend.example.com = ["backend1","backend2","backend3"]

# 转发配置
[forwarding]
mode = "LEGACY"  # 或 "MODERN" (Velocity to Velocity)
secret = "your-secret-here"
```

## 8. 開機腳本

```bash
#!/bin/bash
# start-backend1.sh

cd /path/to/backend1

# 設置 JVM 參數
JVM_ARGS="-Xmx4G -Xms4G -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxGCPauseMillis=200"

# 啟動
java $JVM_ARGS -jar purpur-swm-partygame.jar nogui
```

## 9. systemd 服務 (Linux)

### Lobby 服務

```systemd
# /etc/systemd/system/lobby.service
[Unit]
Description=Lobby Server
Wants=network.target
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/java -Xmx4G -Xms4G -XX:+UseG1GC -XX:+UseStringDeduplication -jar /path/to/lobby/purpur-1.21.7.jar nogui
WorkingDirectory=/path/to/lobby
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

### Backend 服務

```systemd
# /etc/systemd/system/backend1.service
[Unit]
Description=Backend 1 (Party Game)
Wants=network.target
After=redis.service mysqld.service

[Service]
Type=simple
ExecStart=/usr/bin/java -Xmx4G -Xms4G -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxGCPauseMillis=200 -jar /path/to/backend1/purpur-swm-partygame.jar nogui
WorkingDirectory=/path/to/backend1
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

啟動：

```bash
systemctl daemon-reload
systemctl start lobby
systemctl start backend1
systemctl enable lobby
systemctl enable backend1
```

## 10. 常見問題

### 問題: LuaError in world

```
問題：SWM 世界加載失敗
解決：
- 確保 .slimeworld 檔案在 swm_worlds/ 目錄下
- 檢查 SWM 版本與 Minecraft 版本匹配
- 確保世界文件格式正確 (使用 world-converter 重新轉換)
```

### 問題: Redis 連線失敗

```
問題：無法連線到 Redis
解決：
- 檢查 Redis 是否運行 (redis-cli ping)
- 檢查配置文件中的 host/port 是否正確
- 防火牆規則是否阻擋 6379 port
```

### 問題: MySQL 連線失敗

```
問題：無法連線到 MySQL
解決：
- 檢查 MySQL 服務是否運行
- 檢查配置中的 username/password 是否正確
- 檢查 MySQL 用戶是否允許連線 (GRANT)
```

### 問題: 權限檢查不生效

```
問題：玩家無法使用配對功能
解決：
- 檢查 LuckPerms 是否正確安裝
- 檢查玩家是否被賦予對應的 group
- 使用 /lp user <name> info 檢查權限
- 確認沒有權限衝突 (例如 negative permission)
```
