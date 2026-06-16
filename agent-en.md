# Agent English Documentation (AI-Friendly Version)

## 1. Project Overview

This project consists of:
- **Lobby Servers**: Standard Purpur 1.21.7 with plugins for matchmaking, party, and friends
- **Backend Servers**: Modified Purpur (SWMPurpur) that disables vanilla world creation and only loads Slime World Manager (SWM) worlds
- **World Converter**: Tool to convert vanilla world folders to .slimeworld format
- **Common Library**: Redis/MySQL protocol abstraction shared by both Lobby and Backend

## 2. Architecture

### 2.1 Server Layout

```
Velocity Proxy
├── Lobby 1 (Standard Purpur 1.21.7)
├── Lobby 2 (Standard Purpur 1.21.7)
├── Backend 1 (SWM Purpur 1.21.7)
├── Backend 2 (SWM Purpur 1.21.7)
└── Backend N (SWM Purpur 1.21.7)
```

### 2.2 Key Differences

| Component | Lobby | Backend |
|-----------|-------|---------|
| Core | Purpur 1.21.7 (unmodified) | SWMPurpur (vanilla world creation disabled) |
| World Loading | Vanilla worlds only | SWM (.slimeworld) only |
| Plugins | Matchmaking, Friend, Party | Party Game Core |
| Database | MySQL, Redis | MySQL, Redis |

## 3. Plugin Structure

### 3.1 Lobby Plugins
Located in `\04_plugin-suite/`
- `04_01_matchmaking/` - Matchmaking system
- `04_03_friend/` - Friend system
- `04_04_common-lib/` - Common utilities (shared with Backend)

### 3.2 Backend Frameworks
Located in `01_purpur-swm-framework/` and `02_purpur-swm-partygame/`
- `01_purpur-swm-framework/` - Generic SWM Purpur framework
- `02_purpur-swm-partygame/` - Party Game specific framework
- `03_world-converter/` - World conversion tool

## 4. Backend Architecture

### 4.1 World Pool Management

Backend server manages multiple SWM worlds concurrently.

```java
// 01_purpur-swm-framework/src/main/java/co/partygameframework/swm/
public class SwmWorldManager {
    // Manages loading/unloading SWM worlds
    public void loadWorld(String worldName);
    public void unloadWorld(String worldName);
    public boolean worldExists(String worldName);
}
```

### 4.2 World Pool

```java
// 01_purpur-swm-framework/src/main/java/co/partygameframework/swm/
public class WorldPool {
    private final List<SlimeWorld> availableWorlds = new ArrayList<>();
    
    // Get a world from the pool
    public SlimeWorld getAvailableWorld(GameType gameType);
    
    // Return a world to the pool after game ends
    public void returnWorld(SlimeWorld world, GameType gameType);
}
```

### 4.3 World Allocation

When matchmaking succeeds:
1. Lobby sends `MatchAccepted` to backend via Redis
2. Backend selects an available world from the world pool
3. Backend teleports players to the world
4. Game starts
5. After game ends, world is returned to the pool

### 4.4 Hot Reload

Backend supports hot reloading of certain components:
- Game logic (custom plugins)
- World templates
- Game configurations

```yaml
# Backend configuration
hotreload:
  enabled: true
  interval: 5  # seconds (check for file changes)
  plugins:
    - custom   # Custom game plugins
    - games    # Game config files
    - config   # General configuration
```

## 5. Communication Protocol

### 5.1 Lobby → Backend (BungeeCord)

```java
// When lobby receives match request from player
public class LobbyProtocolHandler {
    // Send match request to backend
    public void sendMatchRequest(Backend backend, MatchRequest request) {
        // Use BungeeCord/Velocity API to send message
        // to backend via proxy
    }
}
```

**Match Request Packet**:
```json
{
    "type": "MATCH_REQUEST",
    "session_id": "uuid-v4",
    "game_type": "survival",
    "players": [
        {"uuid": "...", "name": "Player1"},
        {"uuid": "...", "name": "Player2"},
        ...
    ],
    "party_id": null,  // 如果以 Party 身份匹配，此值為 Party ID
    "custom_options": {},  // 自訂選項 (例如遊戲設定)
    "source_server": "lobby1"
}
```

### 5.2 Backend → Lobby (BungeeCord)

**Match Accepted**:
```json
{
    "type": "MATCH_ACCEPTED",
    "session_id": "uuid-v4",
    "game_type": "survival",
    "world": "partygame_survival_001",
    "server": "backend1",
    "players": [
        {"uuid": "...", "name": "Player1"},
        {"uuid": "...", "name": "Player2"},
        ...
    ]
}
```

**Game Start**:
```json
{
    "type": "GAME_START",
    "session_id": "uuid-v4",
    "world": "partygame_survival_001",
    "countdown": 10  // seconds
}
```

**Game End**:
```json
{
    "type": "GAME_END",
    "session_id": "uuid-v4",
    "results": [
        {"uuid": "...", "score": 100},
        {"uuid": "...", "score": 80},
        ...
    ],
    "statistics": {
        "rounds_played": 5,
        "total_time": 300,
        "worlds_used": ["partygame_survival_001"]
    }
}
```

**Game Notification** (during game):
```json
{
    "type": "GAME_NOTIFICATION",
    "session_id": "uuid-v4",
    "notification_type": "round_start",  // round_start, game_over, next_round, etc.
    "message": "Next round starting...",
    "data": {
        "round": 2,
        "total_rounds": 5,
        "time_limit": 120
    }
}
```

## 6. Matchmaking Flow

### 6.1 Lobby Side

```
1. Player clicks match button (GUI)
2. Lobby validates permission (LP check)
3. Lobby adds player to queue (Redis)
4. Party matches together (if applicable)
5. Lobby receives MatchAccepted
6. Lobby notifies player teleport
```

### 6.2 Backend Side

```
1. Backend receives MatchRequest from Lobby
2. Backend checks world pool (available worlds)
3. Backend assigns world to session
4. Backend teleports players to world
5. Backend starts game session
6. Backend receives game end from session
7. Backend sends GameEnd to Lobby
8. Backend recycles world to pool
```

## 7. Permission System

### 7.1 Permission Design Principles

1. No hardcoded rank - all rank control via LuckPerms
2. Permission-based access
3. Context-aware (server world, etc.)
4. TTL support (time-limited permissions)

### 7.2 Permission Example

```yaml
# Example LP ladder configuration
partygame ladder:
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
```

### 7.3 Permission Check Implementation

```java
// NoRankChecker.java
public class NoRankChecker {
    private final PermissionManager permissionManager;
    
    public boolean hasPermission(Player player, String permission) {
        return permissionManager.hasPermission(player, permission);
    }
}
```

## 8. Party System

### 8.1 Party Matching

Player creates party → Members are matched together → Players assigned to backend world.

```java
public class PartyMatchValidator {
    // Validate all party members have required permissions
    public boolean validateForMatch(List<Player> members, GameType gameType) {
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
    
    // Check party leader has permission to select game type
    public boolean validateLeader(Player leader, GameType gameType) {
        return permissionChecker.hasPermission(leader, "partygame.party.match");
    }
}
```

### 8.2 Party State Flow

```
Party Created → [WAITING] → [JOINING PARTY] → [READY TO MATCH]
    ↓
[IN MATCHING] → [MATCHED] → [IN GAME] → [RETURNING TO LOBBY]
```

## 9. World Converter Architecture

### 9.1 Conversion Process

```
World Folder
├── region/  (mca files) → read chunk NBT → 1.21.7 chunk format → Slime format
├── entities/ (*.dat) → read entity NBT → 1.21.7 entity format → Slime format
├── data/ → read global data → convert format → Slime format
└── level.dat → read world metadata → convert format → Slime format
```

### 9.2 Key Conversion Steps

1. Read region files (.mca) and parse chunk NBT
2. Convert coordinates (for 1.21.7)
3. Convert block states (to 1.21.7 NMS format)
4. Convert entities (1.21.7 NMS format)
5. Write Slime format (.slimeworld)
6. Compress output

## 10. Backend World Pool Flow

### 10.1 Startup

When backend server starts:

```
1. Scan swm_worlds/ directory
2. Read all .slimeworld files
3. Register with SwmWorldManager
4. Mark as available in WorldPoolManager
```

### 10.2 Startup

When backend server starts:

```
1. Scan swm_worlds/ directory
2. Read all .slimeworld files
3. Register with SwmWorldManager
4. Mark as available in WorldPoolManager
```

### 10.3 Match Allocation

When match is ready:

```
1. WorldPoolManager.getAvailableWorld(GameType)
2. Choose a world
3. Allocate to session
```

### 10.3 Post-Game

After game ends:

```
1. WorldPoolManager.returnWorld(world, GameType)
2. Optionally reset world state (teleport all players back to lobby)
3. Mark as available
```

## 11. Hot Reload Details

### 11.1 What Can Be Hot Reloaded

| Component | Hot Reload | Notes |
|-----------|------------|-------|
| Game logic (custom plugins) | Yes | No restart needed |
| World templates | Yes | Reload from disk |
| Game configuration | Yes | Reload from disk |
| Matchmaking logic | No | Restart needed |

### 11.2 Hot Reload Implementation

```java
// Hot Reload Manager
public class HotReloadManager {
    private final ScheduledExecutorService scheduler;
    
    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            // Check for file changes
            checkForChanges();
            // Reload if changed
            if (hasChanges) {
                reloadPlugins();
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
}
```

## 12. Redis Pub/Sub Channels

| Channel | Purpose |
|---------|---------|
| `partygame:lobby` | Lobby to Lobby communication |
| `partygame:backend` | Backend to Backend communication |
| `partygame:backend1` | Messages to specific backend |
| `partygame:backend2` | Messages to specific backend |
| `partygame:backendN` | Messages to specific backend |
| `partygame:match` | Matchmaking queue |
| `partygame:party` | Party system |

## 13. MySQL Schema

### 13.1 Users Table

```sql
CREATE TABLE users (
    uuid CHAR(36),
    name VARCHAR(16),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (uuid)
);
```

### 13.2 Friends Table

```sql
CREATE TABLE friends (
    user_uuid CHAR(36),
    friend_uuid CHAR(36),
    status ENUM('FRIEND', 'BLOCKED', 'IGNORED') DEFAULT 'FRIEND',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_uuid, friend_uuid)
);
```

### 13.3 Custom Options Table

```sql
CREATE TABLE custom_options (
    game_id VARCHAR(50),
    key VARCHAR(100),
    value TEXT,
    type ENUM('boolean', 'integer', 'string', 'array') DEFAULT 'string',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (game_id, key)
);
```

### 13.4 Match Records Table

```sql
CREATE TABLE match_records (
    id INT AUTO_INCREMENT,
    session_id CHAR(36),
    game_id VARCHAR(50),
    backend_id VARCHAR(50),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    PRIMARY KEY (id)
);
```

## 14. Startup Sequence

### 14.1 Lobby Server

```
1. Purpur 1.21.7 loads
2. Plugins load (common-lib, auth, matchmaking, friend)
3. Lua permissions system (LuckPerms) loaded
4. Redis/MySQL connections established
5. Lobby starts matchmaking loop
6. Lobby joins backend pool via Redis
```

### 14.2 Backend Server

```
1. SWMPurpur 1.21.7 loads
2. Vanilla world creation disabled
3. SWM world loading enabled
4. Party Game Core loads
5. World pool scanned and loaded from swm_worlds/ directory
6. Backend joins pool via Redis
7. Backend begins accepting match requests
```

## 15. Debugging

### 15.1 Common Issues

| Issue | Solution |
|-------|----------|
| LuaError in world | Ensure .slimeworld exists in swm_worlds/ directory |
| Redis connection error | Check Redis is running, check config |
| Match request timeout | Check Redis/BungeeCord connection health |
| Permission denied during matching | Verify player has `partygame.match.join` permission |

### 15.2 Logs

All plugins use SLF4J for logging. Check the following logs for debugging:

```
plugins/McMatchmatching/logs/matchmaking.log
plugins/McFriend/logs/friend.log
backend05_partygame_core/logs/partygame.log
```
