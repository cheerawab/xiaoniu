# PursWM - Party Game 配對系統

PursWM 是一個基於 Minecraft 的跨伺服器 Party Game 配對系統，專為 4-8 人團隊遊戲設計（如 Party Game、BedWars 等小遊戲模式）。

## 專案特性

- **通用型配對架構** - 支援任意遊戲類型，可熱插拔新增遊戲
- **Slime World Manager 整合** - 高性能世界管理，支援多世界並行運行
- **通用配對系統** - 跨伺服器的快速配對機制，支援 Lobby 和多個 Backend
- **團隊匹配** - 支援 4-8 人團隊組隊匹配
- **權限系統** - 支援 LuckPerms 等權限插件，高度可配置
- **好友系統** - 完整的好友/加入/黑名單功能
- **熱重載** - 支援世界配置、遊戲邏輯的熱重載
- **GUI 介面** - 內建玩家友好的图形化配對/好友管理 GUI
- **通用架構** - 易於擴展新遊戲類型

## 專案架構

```
Velocity/BungeeCord Proxy
├─ Lobby 伺服器 (標準 Purpur 1.21.7)
│   ├─ 04_01_matchmaking/  (配對系統)
│   ├─ 04_03_friend/       (好友系統)
│   └─ 04_04_common-lib/  (共享基礎)
│
└─ Backend 伺服器 (SWM Purpur 客製化核心)
    ├─ 04_02_partygame-core/ (遊戲核心)
    ├─ 03_world-converter/   (世界轉換工具)
    └─ 01_purpur-swm-framework/ (通用 SWM 框架)
```

### 核心模組

| 模組 | 類型 | 說明 |
|------|------|------|
| 04_04_common-lib/ | 插件 | 共享基礎設施 (Redis/MySQL/權限/工具) |
| 04_01_matchmaking/ | 插件 | 通用配對系統 (Lobby 端) |
| 04_03_friend/ | 插件 | 好友系統 (Lobby 端) |
| 04_02_partygame-core/ | 插件 | 遊戲核心 (Backend 端) |
| 01_purpur-swm-framework/ | 核心 | 通用 SWM 框架 (BedWars 等可基於擴展) |
| 02_purpur-swm-partygame/ | 核心 | Party Game 專用 SWM 框架 |
| 03_world-converter/ | 工具 | World → .slimeworld 世界轉換 |

## 技術規格

- **伺服器**: Bukkit-based (Purpur 1.21.7)
- **Java 版本**: Java 21+
- **資料庫**: MySQL/MariaDB
- **快取**: Redis (配對佇列/實時數據)
- **代理層**: Velocity
- **語言**: Java (Kotlin 用於 build 配置)

## 快速開始

### 1. 環境需求

```bash
# 檢查 Java 版本 (需要 Java 21+)
java -version

# 檢查 MySQL 版本 (需要 MySQL 8.x/MariaDB 10.x)
mysql --version

# 檢查 Redis 版本
redis-cli --version

# 克隆專案
git clone https://github.com/PurpurMC/PursWM.git
cd PursWM
```

### 2. 資料庫配置

```sql
-- 建立資料庫
CREATE DATABASE partygame CHARACTER SET utf8mb4;
CREATE USER 'partygame'@'%' IDENTIFIED BY 'secure_password';
GRANT ALL PRIVILEGES ON partygame.* TO 'partygame'@'%';
FLUSH PRIVILEGES;
```

### 3. 建置專案

```bash
# 在專案根目錄執行
./gradlew build
```

### 4. 部署配置

修改插件的 `config.yml` 或 `plugin.yml`，設定：
- Redis 連線 (host, port, password)
- MySQL 連線 (host, port, database, username, password)
- 後端伺服器 URL (backend_servers: ["backend1:25565", "backend2:25565", ...])
- 遊戲類型設定 (games 區塊)
- 權限組設定 (permissions)

## 部署流程

### Lobby Setup
```bash
# 在標準 Purpur 伺服器部署
cp build/libs/04_plugin-suite-*.jar plugins/
# 重啟伺服器
```

### Backend Setup
```bash
# 使用客製化 SWM 核心部署
cp build/libs/04_02_partygame-core-*.jar plugins/
# 將 .slimeworld 世界放入 swm_worlds/ 目錄
mkdir -p swm_worlds/
# 重啟伺服器
```

## 權限設定 (範例)

使用 LuckPerms 設定遊戲權限：

```bash
# 基本遊戲權限
lp user <player> group add player
group addgroup partygame player
group addgroup vip partygame

# 遊戲專屬權限 (根據實際遊戲調整)
lp group setpermission vip partygame.game.survival true
lp group setpermission vip partygame.game.obby true
lp group setpermission vip partygame.game.zombie true

# 管理員權限
lp user <admin> group add staff
lp group setpermission staff partygame.admin.* true
```

## 功能說明

### 世界管理 (World Pool Management)
- 自動管理 SWM 世界 (自動加載/卸載)
- 世界池分配 (每場遊戲獨立世界)
- 世界模板 (支援預覽世界模板)
- 世界同步 (Redis 跨伺服器世界狀態)

### 配對系統 (Lobby 伺服器)
- 快速配對 (Auto-matching)
- 團隊配對 (Team-matching)
- 自訂配置 (自訂配對規則)
- 團隊管理 (Team management)
- 世界配對 (World-matching)
- 玩家狀態追蹤 (Player tracking)
- 隊列狀態監控 (Queue monitoring)
- 實時通知 (Real-time notifications)
- GUI 介面 (Graphical configuration interfaces)

### 好友系統 (Lobby 伺服器)
- 好友添加/移除/黑名單 (Add/remove/block friends)
- 好友狀態追蹤 (Online status tracking)
- 團隊邀請 (Party invitations)
- 聊天過濾 (Chat filtering)
- GUI 介面 (Graphical interfaces)

## 擴充性

### 新增遊戲類型
1. 實現 `IGamePlugin` 接口
2. 加入 `04_02_partygame/` 目錄
3. 配置 `games` 區塊 (設定遊戲選項)
4. 測試 `test_matches/` (測試遊戲對局)
5. 重新建置 `./gradlew clean build` 並部署

**架構優勢**
- 通用型配對架構，支援任意小遊戲
- 熱插拔設計 (Hot-swappable)
- 低耦合設計 (Low coupling)
- 易於測試和擴展 (Easy to test)

## 專案狀態

| 模組 | 狀態 | Java 文件數 | 完成度 |
|------|------|------------|--------|
| 04_04_common-lib/ | ✅ 已完成 | 25 文件 | 100% |
| 04_01_matchmaking/ | ✅ 已完成 | 25 文件 | 100% |
| 04_03_friend/ | ✅ 已完成 | 14 文件 | 100% |
| 04_02_partygame-core/ | ✅ 已完成 | 11 文件 | 100% |
| 01_purpur-swm-framework/ | ✅ 已完成 | 6 文件 | 100% |
| 02_purpur-swm-partygame/ | 🔄 開發中 | 0 文件 | 0% |
| 03_world-converter/ | ✅ 已完成 | 6 文件 | 完成中 |
| **總計** | **95 文件** | **95 文件** | **95+ 文件** |

## 測試指南

### 本地開發測試
```bash
# 啟動本地開發環境
./gradlew runServer

# 執行單元測試
./gradlew test

# 執行國際化測試
./gradlew integrationTest
```

### 測試專案結構
```
tests/
├── matchmaking/  (配對測試)
├── world/        (世界測試)
├── auth/         (權限測試)
└── common/       (共用測試)
```

## 常見問題 (FAQ)

### Q: 如何新增新的遊戲類型？
**A**: 遵循「擴充性」章節說明，實現 `IGamePlugin` 接口即可。

### Q: 支援哪些 Minecraft 版本？
**A**: 目前支援 Purpur 1.21.7+ 版本。

### Q: 資料庫需要什麼配置？
**A**: 需要 MySQL/MariaDB 和 Redis。配置參見「部署配置」。

### Q: 如何監控後端伺服器狀態？
**A**: 使用 `/backend_status` 命令。

### Q: 如何設定權限系統？
**A**: 使用 LuckPerms (或類似插件)，配置參見「快速開始」。