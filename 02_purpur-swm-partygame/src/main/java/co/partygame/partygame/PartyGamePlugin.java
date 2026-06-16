package co.partygame.partygame;

import co.partygame.partygame.config.PartyGameConfig;
import co.partygame.partygame.partyframework.GameDispatcher;
import co.partygame.partygame.partyframework.GameSession;
import co.partygame.partygame.partyframework.MatchAcceptor;
import co.partygame.partygame.partyframework.MatchRequest;
import co.partygame.partygame.partyframework.ScoreKeeper;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Main plugin class for the PartyGame Framework.
 * <p>
 * This plugin provides:
 * <ul>
 *   <li>Game session management (create, start, end, cleanup)</li>
 *   <li>Round lifecycle (setup -> playing -> countdown -> end -> cleanup)</li>
 *   <li>Score tracking and leaderboards</li>
 *   <li>BungeeCord matchmaking integration (receives game requests from Lobby)</li>
 *   <li>World allocation from the SWM framework</li>
 *   <li>Player teleportation management</li>
 * </ul>
 * <p>
 * The framework is designed to be game-agnostic — the PartyGame layer provides
 * the core session/round/score machinery, while specific game types (BedWars,
 * SkyWars, etc.) are implemented by registering {@link GameDispatcher.GameHandler}
 * instances.
 */
public class PartyGamePlugin extends JavaPlugin {

    private PartyGameConfig config;
    private GameDispatcher dispatcher;
    private MatchAcceptor matchAcceptor;
    private ScoreKeeper scoreKeeper;
    private Object worldManager;
    private Object worldPool;

    /**
     * Get the singleton plugin instance for internal access.
     */
    public JavaPlugin getPluginInstance() { return this; }

    /**
     * Get the WorldPool from the SWM Framework.
     */
    public Object getWorldPool() { return worldPool; }

    @Override
    public void onEnable() {
        getLogger().info("=".repeat(50));
        getLogger().info("PartyGame Framework v" + getDescription().getVersion() + " is enabling...");
        getLogger().info("=".repeat(50));

        // Save default config
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        // Load configuration
        config = new PartyGameConfig(this);
        config.load();

        // Get SWM Framework references
        Object swmPlugin = getServer().getPluginManager().getPlugin("SwmPurpurFramework");
        if (swmPlugin != null) {
            try {
                java.lang.reflect.Method getWorldMgr = swmPlugin.getClass().getMethod("getWorldManager");
                worldManager = getWorldMgr.invoke(swmPlugin);

                java.lang.reflect.Method getWorldPool = swmPlugin.getClass().getMethod("getWorldPool");
                worldPool = getWorldPool.invoke(swmPlugin);
            } catch (Exception e) {
                getLogger().warning("Failed to access SWM Framework internals: " + e.getMessage());
            }
        }

        // Initialize game dispatcher
        dispatcher = new GameDispatcher(this, config);

        // Initialize score keeper
        scoreKeeper = new ScoreKeeper(config);
        scoreKeeper.start();

        // Initialize match acceptor
        matchAcceptor = new MatchAcceptor(this, config, dispatcher);
        if (config.isBungeeCordEnabled()) {
            matchAcceptor.registerChannel();
        }

        // Register event listeners
        getServer().getPluginManager().registerEvents(new GameEventListener(), this);

        // Register commands
        registerCommands();

        getLogger().info("PartyGame Framework enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("PartyGame Framework disabling...");

        // End all active sessions
        dispatcher.endAllSessions();

        // Stop score keeper
        scoreKeeper.stop();

        // Unregister channels
        if (config.isBungeeCordEnabled()) {
            String channel = config.getBungeeChannel();
            try {
                Bukkit.getMessenger().unregisterOutgoingPluginChannel(this, channel);
                Bukkit.getMessenger().unregisterIncomingPluginChannel(this, channel, matchAcceptor);
            } catch (Exception ignored) { }
        }

        getLogger().info("PartyGame Framework disabled.");
    }

    private void registerCommands() {
        // /game
        getCommand("game").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                return onGameCommand(sender, args);
            }
        });
        getCommand("game").setTabCompleter(new TabCompleter() {
            @Override
            public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
                List<String> completions = new ArrayList<>();
                for (String sub : new String[]{"status", "list"}) {
                    if (sub.startsWith(args[args.length - 1].toLowerCase()) || args.length < 2) {
                        completions.add(sub);
                    }
                }
                return completions;
            }
        });

        // /gamestatus
        getCommand("gamestatus").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                onGameStatus(sender);
                return true;
            }
        });

        // /join
        getCommand("join").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This command is for players only.");
                    return true;
                }
                return onJoin(player, args);
            }
        });
        getCommand("join").setTabCompleter(new TabCompleter() {
            @Override
            public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
                List<String> completions = new ArrayList<>();
                for (GameSession s : dispatcher.getActiveSessions()) {
                    if (s.getState() == GameSession.SessionState.WAITING) {
                        if (s.toSummary().toLowerCase().startsWith(args[args.length - 1].toLowerCase())) {
                            completions.add(s.getId());
                        }
                    }
                }
                return completions;
            }
        });

        // /leave
        getCommand("leave").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                if (!(sender instanceof Player player)) return true;
                onLeave(player);
                return true;
            }
        });
    }

    private boolean onGameCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.DARK_AQUA + "=== PartyGame Commands ===");
            sender.sendMessage("  /game status — Show all active sessions");
            sender.sendMessage("  /game list   — Show available sessions to join");
            sender.sendMessage("  /gamehelp    — Show game help");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status" -> onGameStatus(sender);
            case "list" -> {
                Collection<GameSession> sessions = dispatcher.getActiveSessions();
                if (sessions.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "No active game sessions.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "=== Active Sessions ===");
                    for (GameSession s : sessions) {
                        sender.sendMessage(s.toSummary());
                    }
                }
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + sub);
                sender.sendMessage("Available: status, list");
            }
        }
        return true;
    }

    private void onGameStatus(CommandSender sender) {
        int active = dispatcher.getActiveSessionCount();
        int waiting = 0;
        int playing = 0;

        for (GameSession s : dispatcher.getActiveSessions()) {
            switch (s.getState()) {
                case WAITING, STARTING -> waiting++;
                case ACTIVE -> playing++;
            }
        }

        int totalPlayers = 0;
        for (GameSession s : dispatcher.getActiveSessions()) {
            totalPlayers += s.getPlayerCount();
        }

        sender.sendMessage(ChatColor.DARK_AQUA + "=== PartyGame Status ===");
        sender.sendMessage(ChatColor.GREEN + "Active sessions: " + active
                + " (waiting: " + waiting + ", playing: " + playing + ")");
        sender.sendMessage(ChatColor.GREEN + "Total players in games: " + totalPlayers);
        sender.sendMessage(ChatColor.GREEN + "Registered game types: " + dispatcher.getRegisteredGameTypes());
    }

    private boolean onJoin(Player player, String[] args) {
        // Find a waiting session to join
        String targetSession = args.length >= 1 ? args[0] : null;
        GameSession session = null;

        if (targetSession != null) {
            session = dispatcher.getSessionById(targetSession);
        }
        if (session == null || session.getState() != GameSession.SessionState.WAITING) {
            // Find any waiting session
            for (GameSession s : dispatcher.getActiveSessions()) {
                if (s.getState() == GameSession.SessionState.WAITING && s.getActivePlayerCount() < s.getScores().size()
                        || session == null) {
                    // Prefer sessions where player is not yet enrolled
                    if (!s.hasPlayer(player.getName())) {
                        session = s;
                        break;
                    }
                }
            }
        }

        if (session == null) {
            player.sendMessage(ChatColor.RED + "No available sessions (or all full).");
            return true;
        }

        if (session.hasPlayer(player.getName())) {
            player.sendMessage(ChatColor.YELLOW + "You are already in a game session.");
            return true;
        }

        if (!session.addPlayer(player.getName())) {
            player.sendMessage(ChatColor.RED + "Failed to join session (full or not accepting).");
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "Joined session '" + session.getId()
                + "' (" + session.getGameType() + ").");

        // If session now has enough players, auto-start
        if (session.getPlayerCount() == config.getMinPlayersPerGame()
                && session.getState() == GameSession.SessionState.WAITING) {
            autoStartSession(session);
        }

        return true;
    }

    private void onLeave(Player player) {
        for (GameSession session : dispatcher.getActiveSessions()) {
            if (session.hasPlayer(player.getName())) {
                session.leavePlayer(player.getName());
                // Teleport player to default world
                sendToDefaultWorld(player);
                player.sendMessage(ChatColor.YELLOW + "Left game session '" + session.getId() + "'.");
                return;
            }
        }
        player.sendMessage(ChatColor.YELLOW + "You are not in any active game session.");
    }

    private void autoStartSession(GameSession session) {
        session.start(playerList(session));
        if (config.isLogSessionEvents()) {
            getLogger().info("Auto-started session '" + session.getId()
                    + "' (" + session.getGameType() + ") with " + session.getPlayers().size() + " players");
        }
    }

    private List<Player> playerList(GameSession session) {
        return session.getPlayers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void sendToDefaultWorld(Player player) {
        String defaultWorld = "world";
        World world = Bukkit.getWorld(defaultWorld);
        if (world == null) {
            world = Bukkit.getWorlds().stream().findFirst().orElse(null);
        }
        if (world != null) {
            player.teleportAsync(world.getSpawnLocation());
        }
    }

    // ============================================================
    // Event listener
    // ============================================================

    /**
     * Handles player lifecycle events for active game sessions.
     */
    private class GameEventListener implements Listener {

        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            // Check if player was previously in an active session
            String playerName = event.getPlayer().getName();

            // Quick check if the player has an active score (was in a game recently)
            if (scoreKeeper.hasPlayer(playerName)) {
                event.setJoinMessage(
                        ChatColor.GRAY + playerName + " re-joined the server (was playing)");
            }
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            String playerName = event.getPlayer().getName();

            for (GameSession session : dispatcher.getActiveSessions()) {
                if (session.isActivePlayer(playerName)) {
                    session.removePlayer(playerName);
                    session.leavePlayer(playerName);

                    event.setQuitMessage(
                            ChatColor.GRAY + ChatColor.RED + playerName + " disconnected from game");

                    if (config.isLogSessionEvents()) {
                        getLogger().info("Player '" + playerName + "' disconnected from session '"
                                + session.getId() + "'");
                    }

                    // Check if game can continue
                    if (session.getActivePlayerCount() <= 0
                            && session.getState() == GameSession.SessionState.ACTIVE) {
                        session.end();
                    }
                    break;
                }
            }
        }

        @EventHandler
        public void onPlayerKick(PlayerKickEvent event) {
            // Similar to quit but with a reason
            onPlayerQuit(new PlayerQuitEvent(event.getPlayer(), event.getReason()));
        }

        @EventHandler
        public void onPlayerSneak(PlayerToggleSneakEvent event) {
            // Sneak + left-click in-game could be used for spectators to
            // toggle between watching game and being in lobby.
            // This is extensible — game-specific handlers can override.
            if (event.isSneaking()) {
                event.setCancelled(true); // Prevent sneak to toggle spectator mode
            }
        }
    }
}
