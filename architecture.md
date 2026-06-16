# 專案架構總說明 (Architecture Documentation)

## 1. 專案總覽

```
                                    ┌─────────────────────────────────────┐
                                    │           Velocity             │
                                    │          (Proxy Router)             │
                                    └──────────┬──────────────────────────┘
                                               │
                    ┌──────────────────────────┼────────────────────────┐
                    │                          │                        │
                    ▼                          ▼                        ▼
            ┌───────────────┐         ┌───────────────┐        ┌───────────────┐
            │   Lobby 1   │         │   Lobby 2   │        │   Lobby N   │
            └─────────┬─────┘         └─────────┬─────┘        └─────────┬─────┘
                      │                          │                        │
          ┌───────────┴──────────┐   ┌──────────┴──────────┐   ┌────────┴────────┐
          │  ┌───────────────┐  │   │ ┌─────────────┐ │   │ ┌─────────────┐ │
          │  │  Matchmaking  │  │   │ │  Matchmaking │ │   │ │  Matchmaking│ │
          │  │   +  Friend   │  │   │ │   +  Friend  │ │   │ │   +  Friend │ │
          │  │    +  Party   │  │   │ │    +  Party  │ │   │ │    +  Party │ │
          │  └───────────────┘  │   │ └─────────────┘ │   │ └─────────────┘ │
          └──────────┬─────────┘   └──────┬──────────┘   └────────┬────────┘
                      │                     │                       │
                      └─────────────────────┼───────────────────────┘
                                             │
                                      ┌─────▼─────┐
                                      │    Redis   │
                                      │ (隊列/會話) │
                                      └────────────┘
                                             │
                                      ┌─────▼─────┐
                                      │    MySQL   │
                                      │ (用戶/好友/ │
                                      │   統計)      │
                                      └────────────┘
                                             │
                    ┌───────────────────────┼────────────────────────┐
                    │                          │                        │
                    ▼                          ▼                        ▼
            ┌───────────────┐         ┌───────────────┐        ┌───────────────┐
            │  Backend 1  │         │  Backend 2  │        │  Backend N  │
            │ (Party Game)│         │ (Party Game)│        │ (Party Game)│
            └───────┬─────┘         └───────┬─────┘        └───────┬─────┘
                      │                          │                        │
            ┌───────┴──────┐          ┌─────────┴─────────┐      ┌──────┴──────┐
            │  Multiple     │          │  Multiple         │      │Multiple   │
            │  SWM Worlds  │          │  SWM Worlds      │      │SWM Worlds │
            │  (每場遊戲獨   │          │  (每場遊戲獨       │      │(每場遊戲獨  │
            │   享世界)     │          │   享世界)         │      │  享世界)   │
            └──────────────┘          └───────────────────┘      └─────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                               專案目錄結構                                      │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  伺服器架構                                                                     │
│  ──────────────────                                                            │
│  Lobby 伺服器 (標準 Purpur 1.21.7)                                             │
│  ├── 04_01_matchmaking/    # 通用配對系統                                       │
│  ├── 04_03_friend/         # 好友系統                                           │
│  └── 04_04_common-lib/     # 共享基礎 (Redis/MySQL/協議)                         │
│                                                                              │
│  Backend 伺服器 (改版 SWM Purpur)                                              │
│  ├── 01_purpur-swm-framework/ (通用 SWM 框架，未來給 BedWars 等用)               │
│  ├── 02_purpur-swm-partygame/  (Party Game 專用框架)                          │
│  ├── 03_world-converter/     (World → .slimeworld 轉換工具)                    │
│  └── 04_02_partygame-core/     (Party Game 核心)                              │
│                                                                              │
│  Lobby vs Backend 差別                                                         │
│  ────────────────────────────                                                   │
│  ├── Lobby (標準 Purpur 1.21.7) → 只有插件沒有修改核心                          │
│  └── Backend (SWM Purpur 1.21.7) → 修改核心禁用原生世界                         │
│                                                                              │
│  Lobby 端 插件清單                                                              │
│  ──────────────────                                                            │
│  ├── 04_0_matchmaking/                                                       │
│  │   ├── MatchmakingPlugin.java         # 主插件類                             │
│  │   ├── MatchQueue.java                # 配對隊列管理                          │
│  │   ├── MatchRouter.java               # 路由到後端 (BungeeCord 傳輸)             │
│  │   ├── MatchStrategy.java             # 匹配策略 (快速/隊友模式)            │
│  │   ├── PlayerState.java                # 玩家狀態                            │
│  │   ├── GamePayloadBuilder.java       # 遊戲自訂欄位打包                       │
│  │   ├── party/                            # ── 隊友系統整合                     │
│  │   │   ├── PartyMatchQueue.java      # 隊友配對隊列 (優先匹配整個隊友)          │
│  │   │   ├── PartyMatchValidator.java  # 配對驗證 (檢查隊伍權限/組隊限制)        │
│  │   │   └── CustomRoomCreator.java    # 自訂房間創建者                          │
│  │   ├── backend/                          # 後端管理                           │
│  │   │   ├── BackendManager.java       # 後端管理                             │
│  │   │   ├── BackendHealth.java         # 後端健康檢查                          │
│  │   │   └── BackendSelector.java      # 後端選擇 (最少玩家/輪流分配)             │
│  │   ├── gui/                              # 配對 GUI                           │
│  │   │   ├── MatchGUI.java               # 配對主界面                           │
│  │   │   ├── WaitingGUI.java             # 等待界面(隊列位置/預估時間)            │
│  │   │   ├── StatsGUI.java               # 個人統計                             │
│  │   │   └── CustomOptionsGUI.java       # 自訂欄位界面(動態)                     │
│  │   ├── auth/                             # ── 權限檢查 (Lobby 端)              │
│  │   │   ├── LobbyPermissionChecker.java # 配對前權限檢查                         │
│  │   │   ├── RoomPermissionChecker.java  # 自訂房間權限/加入                      │
│  │   │   └── RankGateChecker.java        # Rank/Gate 檢查 (由 LP 控制)              │
│  │   └── storage/                          # 持久化                             │
│  │       ├── MatchRecord.java            # 配對記錄                            │
│  │       └── QueueStats.java             # 隊列統計                            │
│  │                                                                              │
│  ├── 04_03_friend/                                                               │
│  │   ├── FriendPlugin.java               # 主插件                               │
│  │   ├── FriendManager.java              # 好友管理                             │
│  │   ├── FriendStorage.java              # MySQL 存儲                            │
│  │   ├── party/PartyInvite.java          # 隊友邀請                             │
│  │   ├── gui/                              # 好友 GUI                            │
│  │   │   ├── FriendListGUI.java          # 好友列表                             │
│  │   │   ├── FriendRequestGUI.java       # 好友請求                             │
│  │   │   └── BlockListGUI.java           # 黑名單                               │
│  │   ├── command/                          # /friend, /block, /ignore             │
│  │   └── storage/FriendRecord.java       # 好友記錄                             │
│  │                                                                              │
│  └── 04_04_common-lib/ 共享基礎 (所有 Lobby 插件依賴)                           │
│      ├── redis/                                                               │
│      │   ├── RedisManager.java               # 連接池管理                       │
│      │   └── RedisChannels.java              # Pub/Sub 通道定義                   │
│      ├── mysql/                                                               │
│      │   ├── MySQLManager.java               # 連接池管理                        │
│      │   └── tables/                                                         │
│      │       ├── UserTable.java              # 用戶資料                         │
│      │       ├── FriendTable.java            # 好友關係                          │
│      │       ├── CustomOptionsTable.java     # 遊戲自訂欄位                      │
│      │       └── MatchRecordTable.java       # 配對記錄                           │
│      ├── protocol/                                                           │
│      │   ├── PacketType.java                 # 包類型定義                        │
│      │   ├── PacketCodec.java                # 編碼/解碼                         │
│      │   └── packets/                                                        │
│      │       ├── lobby/                      # Lobby→Backend 消息                │
│      │       │   ├── MatchRequest.java       # 配對請求包                        │
│      │       │   └── MatchResult.java        # 配對結果                           │
│      │       └── backend/                    # Backend→Lobby 消息                │
│      │           ├── MatchAccepted.java      # 配對成功包                        │
│      │           ├── GameStart.java          # 遊戲開始包                        │
│      │           ├── GameEnd.java            # 遊戲結束包                        │
│      │           └── GameNotification.java   # 遊戲通知包                        │
│      ├── auth/                                                               │
│      │   ├── PermissionManager.java          # 權限管理器 (Abstract)           │
│      │   ├── LuckPermsBridge.java            # LuckPerms 橋接                     │
│      │   ├── PermissionChecker.java          # 權限檢查                         │
│      │   ├── CustomRoomChecker.java          # 自訂房間檢查                      │
│      │   └── NoRankChecker.java              # 無硬編碼 rank，純粹檢查 LP 權限    │
│      ├── config/ConfigManager.java           # 統一 YAML 配置                    │
│      └── util/                                                               │
│          ├── BungeeMessenger.java            # BungeeCord 消息                   │
│          └── PlaceholderAPIHook.java         # PlaceholderAPI 擴展                 │
│                                                                              │
│  Backend 端 插件/框架清單                                                       │
│  ────────────────────────────                                                   │
│  ├── 01_purpur-swm-framework/ (通用框架，BedWars 等未來專案的基礎)              │
│  │   ├── build.gradle.kts                                                   │
│  │   ├── src/main/java/co/partygame/framework/                               │
│  │   │   ├── swm/                                                            │
│  │   │   │   ├── SwmWorldManager.java       世界管理核心介面                  │
│  │   │   │   ├── WorldPool.java               世界池                          │
│  │   │   │   ├── WorldTemplate.java           世界模板                         │
│  │   │   │   └── SlimeWorldLoader.java       Slime 世界加載器                  │
│  │   │   └── player/                                                         │
│  │   │       └── PlayerTransfer.java         玩家傳輸工具                      │
│  │   └── patches/                          # 對 Purpur 的修改禁用原生世界       │
│  │       ├── minecraft-patches/             # Minecraft 層修改                  │
│  │       ├── paper-patches/                 # Paper 層修改                      │
│  │       └── purpur-patches/               # Purpur 層修改                      │
│  │                                                                              │
│  ├── 02_purpur-swm-partygame/  (Party Game 專用框架)                           │
│  │   ├── build.gradle.kts                                                       │
│  │   ├── src/main/java/co/partygame/partygame/                                 │
│  │   │   ├── partyframework/                                                  │
│  │   │   │   ├── GameDispatcher.java          遊戲路由                         │
│  │   │   │   ├── IGamePlugin.java             遊戲插件介面                      │
│  │   │   │   ├── GameSession.java              遊戲會話狀態                      │
│  │   │   │   └── MatchAcceptor.java           接收 Lobby 配對請求               │
│  │   │   └── games/                               # 遊戲邏輯 (由你補充)         │
│  │   └── patches/                            # 基於 01 的修改                    │
│  │                                                                              │
│  ├── 03_world-converter/ (World → .slimeworld 轉換工具)                        │
│  │   ├── build.gradle.kts                                                       │
│  │   ├── src/main/java/co/partygame/converter/                                 │
│  │   │   ├── WorldReader.java                # 解析原生 world/ 資料夾           │
│  │   │   ├── ChunkConverter.java            # 1.21.7 chunk 轉換                 │
│  │   │   ├── SlimeWriter.java               # 寫入 .slimeworld                 │
│      │   │   ├── EntityConverter.java         # 實體轉換                          │
│      │   │   └── config/ConverterConfig.java  # 轉換配置                          │
│      │                                                                              │
│      └── 04_02_partygame-core/  (Party Game 核心)                                │
│        ├── PartyGamePlugin.java          # 主插件                                   │
│        ├── session/                        # 會話管理                               │
│        │   ├── SessionManager.java       # 會話管理器                               │
│        │   └── PlayerRoom.java             # 玩家房間 (4-8 人)                       │
│        ├── game/                           # 遊戲管理                               │
│        │   ├── GameRegistry.java           # 遊戲註冊中心                           │
│        │   ├── GameSession.java            # 遊戲會話                               │
│        │   └── custom/                     # 自定義遊戲邏輯入口                       │
│        │       └── CustomGamePlugin.java   # 遊戲邏輯接口                           │
│        ├── world/                          # 世界管理 (使用 02_purpur-swm-partygame) │
│        │   ├── WorldPoolManager.java       # 世界池管理                               │
│        │   ├── WorldAllocator.java         # 世界分配                                   │
│        │   └── WorldTemplate.java          # 世界模板                             │
│        ├── protocol/                       # 協議處理                                │
│        │   ├── LobbyProtocolHandler.java   # 處理 Lobby 發送的配對請求           │
│        │   └── backend/LobbyBackendProtocol.java # Lobby→Backend 協議               │
│        ├── command/                        # 伺服器指令                             │
│        └── hotreload/                      # 熱重載                                 │
│            ├── HotReloadManager.java       # 熱重載管理器                           │
│            └── PluginReloader.java         # 插件重載器                              │
│                                                                              │
│  部署架構                                                                      │
│  ──────────────                                                                │
│  Velocity Proxy                                                        │
│  ├── lobby1 (標準 Purpur 1.21.7)                                           │
│  ├── lobby2 (標準 Purpur 1.21.7)                                           │
│  ├── backend1 (SWM Purpur 1.21.7)                                          │
│  ├── backend2 (SWM Purpur 1.21.7)                                          │
│  ├── backend3 (SWM Purpur 1.21.7)                                          │
│  └── backendN (SWM Purpur 1.21.7)                                          │
│                                                                              │
│  Backend 伺服器內運行多個 SWM 世界：                                           │
│  ├── Backend 1                                                              │
│  │   └── swm_worlds/                                                        │
│  │       ├── partygame_001/    # 遊戲 1 的世界                               │
│  │       ├── partygame_002/    # 遊戲 2 的世界                               │
│  │       └── ...                                                            │
│  │                                                                              │
│  ├── Backend 2                                                          │
│  │   └── swm_worlds/                                                        │
│  │       ├── partygame_003/    # 遊戲 3 的世界                               │
│  │       └── ...                                                            │
│  │                                                                              │
│  └── Backend N                                                              │
│      └── swm_worlds/                                                        │
│          ├── partygame_00N/    # 遊戲 N 的世界                               │
│          └── ...                                                            │
│                                                                              │
│  啟動流程                                                                    │
│  ──────────────                                                              │
│  1. 每個 Back 啟動時會掃描 swm_worlds/ 目錄，自動加載所有 .slimeworld           │
│  2. 每個 Back 會註冊到 Redis，告知自己支援哪些遊戲類型                           │
│  3. Lobby 的 Matchmaking 插件監聽 Redis 事件                                   │
│  4. 玩家點擊配對 → Lobby 檢查權限 → 發送 MatchRequest 到後端                     │
│  5. 後端回應 MatchAccepted → 玩家被傳送到後端伺服器                              │
│  6. 後端分配 SWM 世界 → 玩家開始遊戲                                              │
│                                                                              │
│  通信流程                                                                    │
│  ──────────────                                                              │
│  Lobby → Backend:                                                        │
│  ├── MatchRequest (配對請求)                                               │
│  ├── MatchCancel (取消配對)                                                │
│  └── CustomRoomRequest (自訂房間請求)                                       │
│                                                                              │
│  Backend → Lobby:                                                          │
│  ├── MatchAccepted (配對成功)                                              │
│  ├── MatchFailed (配對失敗)                                                │
│  ├── GameStart (遊戲開始，通知玩家 teleport)                                 │
│  ├── GameEnd (遊戲結束)                                                    │
│  └── GameNotification (遊戲通知:計時、計分等)                               │
│                                                                              │
│  配對流程                                                                    │
│  ──────────────                                                              │
│  1. 玩家點擊配對按鈕 (Match GUI)                                            │
│  2. Lobby 檢查玩家權限 (LP 權限)                                             │
│  ├── 有權限: 發送 MatchRequest 到後端                                       │
│  ├── 無權限: 顯示權限不足 (LP 權限不足)                                    │
│  │                                                                              │
│  3. 後端收到 MatchRequest                                                     │
│  ├── 檢查遊戲類型                                                             │
│  ├── 檢查世界池                                                               │
│  ├── 分配 SWM 世界                                                            │
│  └── 回應 MatchAccepted 到 Lobby                                             │
│                                                                              │
│  4. Lobby 接收到 MatchAccepted                                               │
│  ├── 通知玩家 teleport 到後端                                                 │
│  └── Lobby 的 MatchGUI 關閉                                                  │
│                                                                              │
│  5. 玩家被傳送到後端                                                          │
│  ├── 後端將玩家分配到 SWM 世界                                                  │
│  └── 遊戲開始                                                                │
│                                                                              │
│  ────────────────────────────                                                   │
│  Party 配對流程:                                                        │
│  1. 玩家創建 Party (Party GUI)                                              │
│  2. 隊友邀請隊友加入                                                          │
│  3. 整個 Party 作為一個整體進行匹配                                            │
│  └── 所有隊員一起被傳送到同一個後端伺服器                                        │
│                                                                              │
│  自訂房間流程:                                                        │
│  1. 玩家創建自訂房間 (CustomRoom GUI)                                       │
│  ├── 選擇遊戲類型，設定自訂選項 (rounds, time, map, etc.)                     │
│  ├── 設定密碼 (如有)                                                            │
│  └── 設定最大人數                                                              │
│                                                                              │
│  2. 後端收到 CustomRoomRequest                                                │
│  ├── 檢查自訂房間權限                                                         │
│  ├── 創建 SWM 世界 (從模板克隆)                                               │
│  ├── 設定密碼限制                                                             │
│  └── 回應 CustomRoomCreated 到 Lobby                                          │
│                                                                              │
│  3. Lobby 通知玩家                                                            │
│  ├── 玩家邀請隊友加入                                                          │
│  └── 所有玩家被傳送到後端                                                      │
│                                                                              │
│  ──────────────────────────────                                                  │
│  世界管理流程 (Backend):                                                    │
│  1. 伺服器啟動時掃描 swm_worlds/ 目錄                                         │
│  ├── 讀取所有 .slimeworld 文件                                                 │
│  └── 註冊到 SwmWorldManager                                                     │
│                                                                              │
│  2. 配對時分配世界                                                              │
│  ├── 從 WorldPoolManager 檢查可用世界                                           │
│  ├── 選擇一個空閒的世界                                                         │
│  └── 分配給該遊戲的會話                                                         │
│                                                                              │
│  3. 遊戲結束後                                                                  │
│  ├── 清空 SWM 世界的玩家                                                       │
│  ├── 清空世界狀態 (重置方块位置等)                                               │
│  └── 回收到 WorldPool (可供下一场使用)                                         │
└──────────────────────────────────────────────────────────────────────────────┘
