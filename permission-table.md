# 完整權限表 (Permission Table)

## 1. 權限設計原則

1. **不硬編碼 Rank**：所有 Rank 判斷基於 LP 設定
2. **Permission-based**：管理員透過 LP 的 Group/Inheritance 控制權限
3. **Context-Aware**：支援伺服器 context (`server=lobby1`)
4. **TTL Support**：支援限時權限 (LP TTL)

## 2. 完整權限清單

### 2.1 配對權限 (Matchmaking)

| 權限 | 說明 | 預設 |
|------|------|------|
| `partygame.match.join` | 允許加入配對佇列 | 所有人 |
| `partygame.match.quick_join` | 允許快速配對 | 所有人 |
| `partygame.match.custom_room` | 允許創建自訂房間 | 未定 |
| `partygame.match.custom_room_join` | 允許加入自訂房間 | 所有人 |
| `partygame.match.solo` | 允許 solo 配對 | 所有人 |
| `partygame.match.priority` | 允許進入優先佇列 | 未定 |
| `partygame.match.bypass_queue` | 允許跳過佇列 (管理員) | 管理員 |
| `partygame.match.create_private` | 允許創建私密配對房間 | 未定 |
| `partygame.match.view_history` | 允許查看配對歷史 | 所有人 |
| `partygame.match.game.<gameType>` | 允許加入特定遊戲類型配對 | 未定 |
| `partygame.match.game.*` | 通配符，允許所有遊戲類型 | 未定 |

**說明**：
- `partygame.match.game.*` 是通配符，不會自動授予 `partygame.match.game.survival` 等具體權限
- 管理員需為每個遊戲類型設定對應權限 (例如 `partygame.match.game.survival`)
- LP 可使用 `partygame.match.game.*` 授予所有遊戲類型權限

### 2.2 遊戲類型權限 (Game Types)

> 管理員需根據實際遊戲設定對應權限

| 權限 | 說明 | 預設 |
|------|------|------|
| `partygame.game.survival` | 允許加入生存遊戲 | 未定 |
| `partygame.game.obby` | 允許加入障礙遊戲 | 未定 |
| `partygame.game.zombie` | 允許加入殭屍遊戲 | 未定 |
| `partygame.game.racing` | 允許參加賽車遊戲 | 未定 |
| `partygame.game.capture_flag` | 允許參加奪旗遊戲 | 未定 |
| `partygame.game.bedwars` | 允許參加 BedWars (未來) | 未定 |

**範例 LP 配置**：
```yaml
# ladder: 等級 ladder
partygame_ladder:
  free:
    group: free
    permissions:
      - partygame.match.join
      - partygame.match.solo
      - partygame.game.survival
      - partygame.game.obby
  vip:
    group: vip
    inheritance: free
    permissions:
      - partygame.match.priority
      - partygame.match.custom_room
      - partygame.game.zombie
      - partygame.game.racing
  mvp:
    group: mvp
    inheritance: vip
    permissions:
      - partygame.game.capture_flag
  mvp_plus:
    group: mvp_plus
    inheritance: mvp
    permissions:
      - partygame.game.bedwars
      - partygame.game.survival_hard

# ladder: 遊戲 ladder (專門控制遊戲類型權限)
partygame_games:
  free:
    group: partygame_free
    permissions:
      - partygame.game.survival
      - partygame.game.obby
  vip:
    group: partygame_vip
    inheritance: partygame_free
    permissions:
      - partygame.game.survival_hard
      - partygame.game.zombie
  mvp:
    group: partygame_mvp
    inheritance: partygame_vip
    permissions:
      - partygame.game.racing
      - partygame.game.capture_flag
  mvp_plus:
    group: partygame_mvp_plus
    inheritance: partygame_mvp
    permissions:
      - partygame.game.bedwars
```

### 2.3 自訂房間權限 (Custom Room)

| 權限 | 說明 | 預設 |
|------|------|------|
| `partygame.custom_room.create` | 允許創建自訂房間 | 未定 |
| `partygame.custom_room.settings` | 允許自訂房間設定 | 未定 |
| `partygame.custom_room.map_select` | 允許自訂房間選擇地圖 | 未定 |
| `partygame.custom_room.max_players` | 允許自訂人數上限 | 未定 |
| `partygame.custom_room.time_limit` | 允許自訂時間限制 | 未定 |
| `partygame.custom_room.password` | 允許設置密碼 | 未定 |
| `partygame.custom_room.spectator` | 允許觀眾設定 | 未定 |
| `partygame.custom_room.allow_guests` | 允許非玩家加入 | 管理員 |

**範例 LP 配置**：
```yaml
partygame_custom_room:
  free:
    group: partygame_free
    permissions:
      - partygame.custom_room.create
      - partygame.custom_room.settings
  vip:
    group: partygame_vip
    inheritance: partygame_free
    permissions:
      - partygame.custom_room.max_players
      - partygame.custom_room.time_limit
  mvp_plus:
    group: partygame_mvp_plus
    inheritance: partygame_vip
    permissions:
      - partygame.custom_room.password
      - partygame.custom_room.allow_guests
```

### 2.4 Party 系統權限 (Party)

| 權限 | 說明 | 預設 |
|------|------|------|
| `partygame.party.create` | 允許創建 Party | 所有人 |
| `partygame.party.invite` | 允許邀請加入 Party | 所有人 |
| `partygame.party.kick` | 允許踢出成員 | 所有人 |
| `partygame.party.leader` | 允許擔任領導者 | 所有人 |
| `partygame.party.max_size` | 允許自訂 Party 人數上限 | 未定 |
| `partygame.party.join` | 允許加入 Party | 所有人 |
| `partygame.party.chat` | 允許 Party 內部聊天 | 所有人 |
| `partygame.party.transfer` | 允許轉移領導權 | 所有人 |
| `partygame.party.disband` | 允許解散 Party | 領導者 |
| `partygame.party.leave` | 允許離開 Party | 所有人 |
| `partygame.party.match` | 允許以 Party 身份匹配 | 所有人 |
| `partygame.party.match_split` | 允許成員單獨匹配 | 所有人 |

**範例 LP 配置**：
```yaml
partygame_party:
  free:
    group: partygame_free
    permissions:
      - partygame.party.match
      - partygame.party.match_split
      - partygame.party.max_members: 2
  vip:
    group: partygame_vip
    inheritance: partygame_free
    permissions:
      - partygame.party.max_members: 4
  mvp_plus:
    group: partygame_mvp_plus
    inheritance: partygame_vip
    permissions:
      - partygame.party.max_members: 8
```

### 2.5 好友系統權限 (Friend)

| 權限 | 說明 | 預設 |
|------|------|------|
| `partygame.friend.add` | 允許添加好友 | 所有人 |
| `partygame.friend.remove` | 允許移除好友 | 所有人 |
| `partygame.friend.block` | 允許加入黑名單 | 所有人 |
| `partygame.friend.ignore` | 允許忽略消息 | 所有人 |
| `partygame.friend.invite_party` | 允許邀請加入 Party | 所有人 |
| `partygame.friend.invite_game` | 允許邀請參加遊戲 | 所有人 |
| `partygame.friend.view_online` | 允許查看線上狀態 | 所有人 |
| `partygame.friend.view_history` | 允許查看歷史記錄 | 所有人 |

### 2.6 Backend 管理權限 (Admin)

| 權限 | 說明 | 預設 |
|------|------|------|
| `partygame.admin.reload` | 允許重新載入配置 | 管理員 |
| `partygame.admin.reload_game` | 允許重新載入遊戲 | 管理員 |
| `partygame.admin.reload_world` | 允許重新載入世界模板 | 管理員 |
| `partygame.admin.player.teleport` | 允許傳送到玩家位置 | 管理員 |
| `partygame.admin.player.ban` | 允許封禁玩家 | 管理員 |
| `partygame.admin.player.kick` | 允許踢出玩家 | 管理員 |
| `partygame.admin.server.status` | 允許查看伺服器狀態 | 管理員 |
| `partygame.admin.server.restart` | 允許重啟後端 | 管理員 |
| `partygame.admin.match.force_match` | 允許強制配對 | 管理員 |
| `partygame.admin.match.cancel` | 允許取消配對 | 管理員 |
| `partygame.admin.game.create` | 允許創建遊戲實例 | 管理員 |
| `partygame.admin.game.delete` | 允許刪除遊戲實例 | 管理員 |
| `partygame.admin.game.list` | 允許列出現有遊戲 | 管理員 |
| `partygame.admin.world.create` | 允許創建世界 | 管理員 |
| `partygame.admin.world.delete` | 允許刪除世界 | 管理員 |
| `partygame.admin.world.template` | 允許使用世界模板 | 管理員 |
| `partygame.admin.world.load` | 允許加載世界 | 管理員 |
| `partygame.admin.world.unload` | 允許卸載世界 | 管理員 |
| `partygame.admin.world.save` | 允許保存世界 | 管理員 |

### 2.7 GUI 權限

| 權限 | 說明 | 預設 |
|------|------|------|
| `partygame.gui.match` | 允許打開配對 GUI | 所有人 |
| `partygame.gui.waiting` | 允許打開等待界面 GUI | 所有人 |
| `partygame.gui.stats` | 允許打開統計界面 GUI | 所有人 |
| `partygame.gui.custom_options` | 允許打開自訂欄位 GUI | 所有人 |
| `partygame.gui.party` | 允許打開 Party 界面 GUI | 所有人 |
| `partygame.gui.friend` | 允許打開好友界面 GUI | 所有人 |
| `partygame.gui.game_select` | 允許打開遊戲選擇界面 | 所有人 |

### 2.8 命令權限 (Commands)

| 權限 | 說明 | 預設 |
|------|------|------|
| `partygame.command.help` | 允許使用 /help 命令 | 所有人 |
| `partygame.command.status` | 允許使用 /status 命令 | 所有人 |
| `partygame.command.party` | 允許使用 /party 命令 | 所有人 |
| `partygame.command.friend` | 允許使用 /friend 命令 | 所有人 |
| `partygame.command.match` | 允許使用 /match 命令 | 所有人 |
| `partygame.command.game` | 允許使用 /game 命令 | 所有人 |

## 3. 預設 Group 結構範例

```yaml
# 預設 group 結構 (供管理員參考)
partygame_groups:
  free:
    default: true
    permissions:
      - partygame.match.join
      - partygame.match.solo
      - partygame.game.base   # 假設 base 包含基本遊戲
      - partygame.party.*
      - partygame.friend.*
      - partygame.gui.*
      - partygame.command.*
  vip:
    inheritance: free
    permissions:
      - partygame.match.priority
      - partygame.match.custom_room
      - partygame.game.advanced  # 包含更多遊戲
  mvp:
    inheritance: vip
    permissions:
      - partygame.game.premium  # 更多遊戲權限
  mvp_plus:
    inheritance: mvp
    permissions:
      - partygame.game.all      # 所有遊戲
```

## 4. 權限檢查流程

```
玩家嘗試加入配對 (Matchrequest)
    │
    ▼
┌───────────────────────────────────────────────────────────┐
│ Lobby 端 (Matchmaking Plugin)                            │
│  1. LobbyPermissionChecker.checkJoinPermission(player)   │
│     ├── hasPermission(player, "partygame.match.join")     │
│     └── hasPermission(player, "partygame.game.<gameType>")│
│  2. 檢查 Party 權限 (如有)                                 │
│     ├── hasPermission(player, "partygame.party.*")       │
│     └── hasPermission(leader, "partygame.party.match")    │
│  3. 檢查自訂房間權限 (如有)                                 │
│     ├── hasPermission(player, "partygame.custom_room.*") │
│     └── hasPermission(player, "partygame.match.custom_room")│
│  4. 所有檢查通過 → 加入配對佇列                             │
│  5. 任何檢查失敗 → 拒絕，顯示 "Access Denied"               │
└───────────────────────────────────────────────────────────┘
    │
    ▼
發送 MatchRequest 到 Backend
    │
    ▼
Backend (PartyGame Core)
    └── backend 端不檢查權限 (由 Lobby 端預先檢查)
```

## 5. 範例 LP 設置

### 5.1 設置 VIP 等級

```
# 在 LP 控制台 (或透過 config)
lp group create vip
lp group setinheritance vip add free
lp group setpermission vip partygame.match.priority
lp group setpermission vip partygame.match.custom_room
lp group setpermission vip partygame.game.advanced

# 或使用 ladder
lp ladder create partygame_ladder
lp ladder addnode partygame_ladder free vip
lp ladder addnode partygame_ladder vip mvp
lp ladder addnode partygame_ladder mvp mvp_plus

lp user <playerName> ladder set partygame_ladder vip
```

### 5.2 設置限時權限

```
lp user <playerName> permission set partygame.game.premium 30d
lp user <playerName> permission set partygame.game.advanced 7d
```

### 5.3 設置特定伺服器權限

```
lp user <playerName> permission set partygame.admin.reload server=backend1
lp user <playerName> permission set partygame.admin.reload server=backend2
```

## 6. LuckPerms 權限檢查範例

```java
// NoRankChecker.java - 純粹檢查 LP 權限，不硬編碼 rank
public class NoRankChecker {
    
    private final PermissionManager permissionManager;
    
    public boolean hasPermission(Player player, String permission) {
        return permissionManager.hasPermission(player, permission);
    }
    
    public boolean hasAnyPermission(Player player, String... permissions) {
        for (String perm : permissions) {
            if (permissionManager.hasPermission(player, perm)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean hasAllPermissions(Player player, String... permissions) {
        for (String perm : permissions) {
            if (!permissionManager.hasPermission(player, perm)) {
                return false;
            }
        }
        return true;
    }
}
```

```java
// 使用範例
public class LobbyPermissionChecker {
    
    private final NoRankChecker rankChecker;
    
    public boolean canJoinMatch(Player player, String gameType) {
        // 檢查基本配對權限
        if (!player.hasPermission("partygame.match.join")) {
            return false;
        }
        
        // 檢查遊戲類型權限
        if (!player.hasPermission("partygame.match.game." + gameType)) {
            return false;
        }
        
        // 可以透過 lp 通配符
        if (player.hasPermission("partygame.match.game.*")) {
            return true; // 繞過所有單一權限檢查
        }
        
        return true;
    }
    
    public boolean canCreateCustomRoom(Player player) {
        return rankChecker.hasAllPermissions(player,
            "partygame.match.custom_room",
            "partygame.custom_room.create",
            "partygame.custom_room.settings"
        );
    }
}
```

## 7. 權限依賴關係

```
partygame.*                           # 權限根節點
│
├── partygame.match.*                 # 配對權限
│   ├── partygame.match.join
│   ├── partygame.match.custom_room
│   ├── partygame.match.game.*        # 遊戲類型權限
│   │   ├── partygame.match.game.survival
│   │   ├── partygame.match.game.obby
│   │   └── ...
│   └── ...
│
├── partygame.party.*                 # Party 權限
│   ├── partygame.party.create
│   ├── partygame.party.match
│   └── ...
│
├── partygame.friend.*                # 好友權限
│   ├── partygame.friend.add
│   ├── partygame.friend.invite_party
│   └── ...
│
├── partygame.game.*                  # 遊戲類型權限
│   ├── partygame.game.survival
│   ├── partygame.game.obby
│   └── ...
│
├── partygame.custom_room.*           # 自訂房間權限
│   ├── partygame.custom_room.create
│   ├── partygame.custom_room.settings
│   └── ...
│
├── partygame.admin.*                 # 管理員權限
│   ├── partygame.admin.reload
│   ├── partygame.admin.player.kick
│   └── ...
│
├── partygame.gui.*                   # GUI 權限
│   ├── partygame.gui.match
│   ├── partygame.gui.party
│   └── ...
│
├── partygame.command.*               # 命令權限
│   ├── partygame.command.help
│   ├── partygame.command.match
│   └── ...
│
└── partygame.world.*                 # 世界管理權限
    ├── partygame.world.create
    ├── partygame.world.delete
    └── ...
```
