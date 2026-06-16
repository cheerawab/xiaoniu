package co.partygame.framework;

import co.partygame.framework.player.PlayerTransfer;
import co.partygame.framework.swm.SlimeWorldLoader;
import co.partygame.framework.swm.SwmWorldManager;
import co.partygame.framework.swm.SwmWorldManager.WorldLoadResult;
import co.partygame.framework.swm.SwmWorldManager.Status;
import co.partygame.framework.swm.SwmWorldManager.WorldEntry;
import co.partygame.framework.swm.WorldPool;
import co.partygame.framework.swm.WorldTemplate;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Main plugin class for the SWM Purpur Framework.
 * <p>
 * This plugin:
 * <ul>
 *   <li>Replaces Purpur's default world creation with SWM-based loading</li>
 *   <li>Manages a pool of worlds loaded from .slimeworld files</li>
 *   <li>Provides teleportation utilities for moving players between worlds</li>
 *   <li>Exposes commands for managing worlds and templates</li>
 * </ul>
 * <p>
 * Vanilla world creation is disabled — only worlds loaded from .slimeworld files
 * (or created from templates) may exist on the server.
 */
public class SwmPurpurPlugin extends JavaPlugin {

    private SwmWorldManager worldManager;
    private WorldPool worldPool;
    private SlimeWorldLoader worldLoader;
    private PlayerTransfer playerTransfer;
    private Path worldsDirectory;
    private Map<String, WorldTemplate> templates;
    private volatile boolean swmPluginDetected;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getLogger().info("=".repeat(50));
        getLogger().info("SWM Framework v" + getDescription().getVersion() + " starting...");
        getLogger().info("=".repeat(50));

        // Check for external SlimeWorldManager plugin
        swmPluginDetected = Bukkit.getPluginManager().getPlugin("SlimeWorldManager") != null;

        worldsDirectory = getDataFolder().toPath().resolve("swm_worlds");
        try {
            if (!Files.exists(worldsDirectory)) {
                Files.createDirectories(worldsDirectory);
            }
        } catch (IOException e) {
            getLogger().severe("Failed to create worlds directory: " + worldsDirectory);
        }

        // Initialize subsystems
        worldManager = new SwmWorldManager(this);
        worldLoader = new SlimeWorldLoader(this, worldsDirectory);
        worldPool = new WorldPool(this, worldManager, worldLoader);
        playerTransfer = new PlayerTransfer(this);
        templates = new HashMap<>();

        // Register subsystems' event listeners
        getServer().getPluginManager().registerEvents(worldManager, this);
        getServer().getPluginManager().registerEvents(playerTransfer, this);

        // Setup commands and tab-completers
        setupCommands();

        // Load templates from config
        loadTemplates();

        // Auto-load worlds directory contents
        autoLoadWorlds();

        getLogger().info("SWM Framework v" + getDescription().getVersion()
                + " enabled. SPM plugin available: " + swmPluginDetected);
        getLogger().info("Worlds directory: " + worldsDirectory);
    }

    @Override
    public void onDisable() {
        getLogger().info("SWM Framework disabling...");

        // Save all managed worlds
        for (WorldEntry entry : worldManager.getWorlds()) {
            try {
                WorldLoadResult result = worldManager.saveWorld(entry.name());
                if (result.status() != Status.WORLD_SAVED) {
                    getLogger().severe("Failed to save world: " + entry.name());
                }
            } catch (Exception e) {
                getLogger().log(java.util.logging.Level.SEVERE,
                        "Error saving world '" + entry.name() + "'", e);
            }
        }

        worldPool.shutdown();
        playerTransfer.cleanup();
        templates.clear();

        getLogger().info("SWM Framework disabled.");
    }

    private void setupCommands() {
        // /worlds
        getCommand("worlds").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                return onCommandWorlds(sender);
            }
        });
        getCommand("worlds").setTabCompleter(new TabCompleter() {
            @Override
            public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
                return SwmPurpurPlugin.this.onTabCommand(sender, "worlds", args);
            }
        });

        // /worldlist
        getCommand("worldlist").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                return onCommandWorldList(sender, args);
            }
        });
        getCommand("worldlist").setTabCompleter(new TabCompleter() {
            @Override
            public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
                return SwmPurpurPlugin.this.onTabCommand(sender, "worldlist", args);
            }
        });

        // /worldload
        getCommand("worldload").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                if (args.length < 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /worldload <name>");
                    return true;
                }
                WorldLoadResult result = worldPool.loadWorld(args[0]);
                sendLoadResult(sender, result);
                return result.status() == Status.SUCCESS;
            }
        });
        getCommand("worldload").setTabCompleter(new TabCompleter() {
            @Override
            public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
                return SwmPurpurPlugin.this.onTabCommand(sender, "worldload", args);
            }
        });

        // /worldunload
        getCommand("worldunload").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                if (args.length < 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /worldunload <name>");
                    return true;
                }
                boolean success = worldPool.removeWorld(args[0]);
                sender.sendMessage(success ? ChatColor.GREEN + "World removed from pool." :
                        ChatColor.RED + "World not found in pool.");
                return success;
            }
        });
        getCommand("worldunload").setTabCompleter(new TabCompleter() {
            @Override
            public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
                return SwmPurpurPlugin.this.onTabCommand(sender, "worldunload", args);
            }
        });

        // /worldtemplate
        getCommand("worldtemplate").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                return onCommandTemplate(sender, args);
            }
        });
        getCommand("worldtemplate").setTabCompleter(new TabCompleter() {
            @Override
            public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
                return SwmPurpurPlugin.this.onTabTemplate(sender, args);
            }
        });

        // /transferplayers
        getCommand("transferplayers").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                if (args.length < 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /transferplayers <worldName>");
                    return true;
                }
                World targetWorld = Bukkit.getWorld(args[0]);
                if (targetWorld == null) {
                    sender.sendMessage(ChatColor.RED + "World '" + args[0]
                            + "' is not currently loaded.");
                    return false;
                }
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                sender.sendMessage(ChatColor.GOLD + "Transferring " + players.size()
                        + " players to '" + args[0] + "'...");
                playerTransfer.teleportGroup(players, args[0]);
                return true;
            }
        });
    }

    private void sendLoadResult(CommandSender sender, WorldLoadResult result) {
        switch (result.status()) {
            case SUCCESS -> sender.sendMessage(ChatColor.GREEN + "World '" + result.worldName()
                    + "' loaded successfully.");
            case WORLD_ALREADY_LOADED -> sender.sendMessage(ChatColor.YELLOW + "World '"
                    + result.worldName() + "' is already loaded.");
            case FILE_NOT_FOUND -> sender.sendMessage(ChatColor.RED + "World file not found: "
                    + result.worldName());
            case WORLD_EXISTS_OUTSIDE_POOL -> sender.sendMessage(ChatColor.RED + "World '"
                    + result.worldName() + "' exists but is not managed by this framework.");
            case LOAD_FAILED -> sender.sendMessage(ChatColor.RED + "Failed to load world '"
                    + result.worldName() + "'. Check logs.");
            default -> sender.sendMessage(ChatColor.RED + "Unknown status: " + result.status());
        }
    }

    private boolean onCommandWorlds(CommandSender sender) {
        List<String> loaded = new ArrayList<>(worldManager.getLoadedWorldNames());
        if (loaded.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No SWM worlds currently loaded.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== SWM Worlds (" + loaded.size() + ") ===");
        for (String name : loaded) {
            World world = Bukkit.getWorld(name);
            int playerCount = world != null ? world.getPlayers().size() : 0;
            sender.sendMessage(ChatColor.WHITE + "  - " + name
                    + (playerCount > 0 ? " [" + ChatColor.RED + playerCount + "]" : " [" + ChatColor.GREEN + "idle]"));
        }
        return true;
    }

    private boolean onCommandWorldList(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }

        List<String> allWorlds = Bukkit.getWorlds().stream()
                .map(World::getName)
                .sorted()
                .toList();

        int perPage = 10;
        int totalPages = Math.max(1, (allWorlds.size() + perPage - 1) / perPage);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int from = (page - 1) * perPage;
        int to = Math.min(from + perPage, allWorlds.size());

        sender.sendMessage(ChatColor.GOLD + "Worlds (Page " + page + "/" + totalPages + ")");
        for (int i = from; i < to; i++) {
            String worldName = allWorlds.get(i);
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                sender.sendMessage(ChatColor.GREEN + String.format("%3d.", i + 1) + " " + allWorlds.get(i)
                        + " seed=" + w.getSeed()
                        + " env=" + w.getEnvironment()
                        + " type=" + w.getWorldType().name()
                        + " players=" + w.getPlayers().size());
            }
        }
        return true;
    }

    private boolean onCommandTemplate(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /worldtemplate <list|create|delete> [name]");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> {
                if (templates.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "No world templates defined.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "=== Templates ===");
                    templates.forEach((n, t) -> sender.sendMessage(
                            ChatColor.WHITE + "  - " + n + " env=" + t.getEnvironment()
                            + " type=" + t.getWorldType() + " seed=" + t.getSeed().orElse(-1L)));
                }
            }
            case "create" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /worldtemplate create <name>");
                    return true;
                }
                templates.put(args[1], WorldTemplate.builder(args[1]).build());
                sender.sendMessage(ChatColor.GREEN + "Template '" + args[1]
                        + "' created with default settings.");
            }
            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /worldtemplate delete <name>");
                    return true;
                }
                if (templates.remove(args[1]) != null) {
                    sender.sendMessage(ChatColor.GREEN + "Template '" + args[1] + "' deleted.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Template '" + args[1] + "' not found.");
                }
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + sub);
        }
        return true;
    }

    private void loadTemplates() {
        List<String> templateNames = getConfig().getStringList("swm.world-pool.templates");
        for (String name : templateNames) {
            if (!name.isEmpty()) {
                var b = WorldTemplate.builder(name);
                templates.put(name, b.build());
            }
        }
    }

    private void autoLoadWorlds() {
        boolean autoLoad = getConfig().getBoolean("swm.auto-load-worlds", true);
        if (!autoLoad) {
            getLogger().info("Auto-load disabled. Worlds will be loaded on demand.");
            return;
        }

        List<String> preloadList = getConfig().getStringList("swm.world-pool.preload-worlds");

        Path wd = worldsDirectory;
        if (!Files.exists(wd)) {
            try {
                Files.createDirectories(wd);
            } catch (IOException ignored) { }
            return;
        }

        int loaded = 0;
        int skipped = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(wd, "*.slimeworld")) {
            for (Path file : stream) {
                String name = file.getFileName().toString().replace(".slimeworld", "");

                // If a preload list is specified, only load those
                if (!preloadList.isEmpty() && !preloadList.contains(name)) {
                    skipped++;
                    continue;
                }

                WorldLoadResult result = worldPool.loadWorld(name);
                if (result.status() == Status.SUCCESS) {
                    loaded++;
                }
            }
        } catch (IOException e) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "Failed to scan worlds directory for .slimeworld files", e);
        }

        getLogger().info("Auto-loaded " + loaded + " worlds (skipped " + skipped + ").");
    }

    // ============================================================
    // Tab completion helpers
    // ============================================================

    public List<String> onTabCommand(CommandSender sender, String command, String[] args) {
        List<String> completions = new ArrayList<>();
        String lastArg = args.length > 0 ? args[args.length - 1] : "";
        String lower = lastArg.toLowerCase();

        // Collect known world names
        worldManager.getLoadedWorldNames().stream()
                .filter(w -> w.toLowerCase().startsWith(lower))
                .forEach(completions::add);

        // File listing
        try {
            Files.list(worldsDirectory)
                    .filter(p -> p.toString().endsWith(".slimeworld"))
                    .map(p -> p.getFileName().toString().replace(".slimeworld", ""))
                    .filter(w -> w.toLowerCase().startsWith(lower))
                    .forEach(completions::add);
        } catch (IOException ignored) { }

        // Templates
        templates.keySet().stream()
                .filter(t -> t.toLowerCase().startsWith(lower))
                .forEach(completions::add);

        return completions.stream().distinct().toList();
    }

    public List<String> onTabTemplate(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        String lower = args.length >= 2 ? args[1].toLowerCase() : "";

        if (args.length >= 2) {
            // Second arg: match against template names for delete, etc.
            templates.keySet().stream()
                    .filter(t -> t.toLowerCase().startsWith(lower))
                    .forEach(completions::add);
            return completions;
        }

        // First arg: subcommands
        for (String sub : List.of("list", "create", "delete")) {
            if (sub.startsWith(lower)) completions.add(sub);
        }
        return completions;
    }

    // ============================================================
    // Public API — access to framework subsystems
    // ============================================================

    public SwmWorldManager getWorldManager() {
        return worldManager;
    }

    public WorldPool getWorldPool() {
        return worldPool;
    }

    public SlimeWorldLoader getWorldLoader() {
        return worldLoader;
    }

    public PlayerTransfer getPlayerTransfer() {
        return playerTransfer;
    }

    public Path getWorldsDirectory() {
        return worldsDirectory;
    }

    public Map<String, WorldTemplate> getTemplates() {
        return templates;
    }

    public boolean isSWMPluginDetected() {
        return swmPluginDetected;
    }

    /**
     * Block a world name from being auto-created by the server.
     */
    public void blockWorld(String name) {
        worldManager.blockWorldName(name);
    }

    /**
     * Create a world template with common settings for game worlds.
     */
    public WorldTemplate createGameWorldTemplate(String name) {
        return templates.computeIfAbsent(name, n -> WorldTemplate.builder(n)
                .worldType(WorldType.NORMAL)
                .environment(World.Environment.NORMAL)
                .pvp(true)
                .build());
    }
}
