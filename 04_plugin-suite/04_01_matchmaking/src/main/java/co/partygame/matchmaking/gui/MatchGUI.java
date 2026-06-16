package co.partygame.matchmaking.gui;

import co.partygame.common.config.ConfigManager;
import co.partygame.common.util.ChatUtils;
import co.partygame.common.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Main matchmaking GUI (chests/inventories).
 * Shows available game types, queue positions, wait times.
 * Each game type is placed in a specific slot shown by gameType.getName() or defaultSlot.
 */
public class MatchGUI implements Listener {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(MatchGUI.class.getName());

    private final Plugin plugin;
    private final WaitingGUI waitingGUI;
    private final co.partygame.matchmaking.auth.LobbyPermissionChecker permissionChecker;
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private final ConfigManager config;

    /**
     * Creates MatchGUI with dependencies.
     *
     * @param plugin                the main plugin instance
     * @param waitingGUI            the waiting GUI instance
     * @param permissionChecker     the permission checker
     * @param config                the configuration manager
     */
    public MatchGUI(Plugin plugin, WaitingGUI waitingGUI,
                    co.partygame.matchmaking.auth.LobbyPermissionChecker permissionChecker,
                    ConfigManager config) {
        this.plugin = Objects.requireNonNull(plugin);
        this.waitingGUI = waitingGUI;
        this.permissionChecker = permissionChecker;
        this.config = config;
    }

    /**
     * Opens the matchmaking GUI for a player.
     *
     * @param player the player to open the GUI for
     */
    public void openMatchGUI(Player player) {
        Objects.requireNonNull(player);

        int size = 5 * 9;
        Inventory inv = Bukkit.createInventory(null, size,
                ChatUtils.colorize("&b&lMatches with Game Types"));

        Map<String, Integer> gameTypes = getGameTypeSlots();
        for (Map.Entry<String, Integer> entry : gameTypes.entrySet()) {
            String gameType = entry.getKey();
            int slot = entry.getValue();
            if (slot >= 0 && slot < size) {
                boolean hasPerm = player.hasPermission("partygame.game." + gameType);
                ItemStack item;
                if (hasPerm) {
                    item = new ItemBuilder(Material.PISTON)
                            .displayName("&a&l" + capitalize(gameType))
                            .lore(
                                    "&7Click to join queue for " + gameType,
                                    "",
                                    "&7Status: &aAvailable"
                            )
                            .build();
                } else {
                    item = new ItemBuilder(Material.BARRIER)
                            .displayName("&c&l" + capitalize(gameType))
                            .lore(
                                    "&7Permission Denied",
                                    "",
                                    "&eYou do not have access to " + gameType + "."
                            )
                            .build();
                }
                inv.setItem(slot, item);
            }
        }

        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    /**
     * Gets known game type slots from configuration.
     *
     * @return map of game type name to recommended slot number
     */
    protected Map<String, Integer> getGameTypeSlots() {
        Map<String, Integer> slots = new LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection section = config.getConfig().getConfigurationSection("gui.game_types");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                if (value instanceof Number) {
                    slots.put(key, ((Number) value).intValue());
                }
            }
        }
        if (slots.isEmpty()) {
            slots.put("partygame", 10);
            slots.put("survival", 12);
            slots.put("bedwars", 14);
            slots.put("skirmish", 16);
        }
        return slots;
    }

    /**
     * Creates game type item for display.
     *
     * @param gameType the game type name
     * @return the configured item
     */
    public static ItemStack createGameItem(String gameType) {
        return new ItemBuilder(Material.PISTON)
                .displayName("&a&l" + capitalize(gameType))
                .lore("&7Join " + gameType + " queue", "", "&7Status: &aAvailable")
                .build();
    }

    /**
     * Creates disabled/gray game type item.
     *
     * @param gameType the game type name
     * @return barrier item with denial message
     */
    public static ItemStack createDisabledItem(String gameType) {
        return new ItemBuilder(Material.BARRIER)
                .displayName("&c&l" + capitalize(gameType))
                .lore("&7Permission Denied")
                .build();
    }

    /**
     * Handles inventory click events for this GUI.
     *
     * @param event the click event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory() == null || !event.getInventory().getTitle().contains("Matches")) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        event.setCancelled(true);

        if (event.getClick() == ClickType.LEFT
                || event.getClick() == ClickType.RIGHT
                || event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            String gameType = extractGameType(clicked);
            if (gameType != null && permissionChecker != null) {
                if (permissionChecker.canJoinMatch(player, gameType)) {
                    co.partygame.matchmaking.MatchQueue q = co.partygame.matchmaking.MatchmakingPlugin.getInstance().getMatchQueue();
                    if (q != null && !q.isInQueue(player)) {
                        q.addToQueue(player, gameType, gameType, null);
                        player.sendMessage(ChatUtils.colorize("&aAdded to queue for &e" + gameType));
                        player.closeInventory();
                        if (waitingGUI != null) waitingGUI.openWaitingGUI(player);
                    }
                } else {
                    player.sendMessage(ChatUtils.colorize("&cYou do not have permission for " + gameType));
                }
            }
        }
    }

    /**
     * Handles inventory close events.
     *
     * @param event the close event
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            openInventories.remove(((Player) event.getPlayer()).getUniqueId());
        }
    }

    /**
     * Extracts the game type from an inventory item's display name.
     *
     * @param item the clicked item
     * @return the game type name, or null
     */
    protected String extractGameType(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return null;
        String name = item.getItemMeta().getDisplayName().replaceAll("[&[a-f0-9]]", "").trim().toLowerCase();
        for (String gameType : config.getConfig().getStringList("gui.game_types")) {
            if (name.contains(gameType.toLowerCase()) || gameType.toLowerCase().contains(name)) {
                return gameType;
            }
        }
        return null;
    }

    /**
     * Closes inventory for all viewers.
     *
     * @param inv the inventory to close
     */
    public static void closeForAll(Inventory inv) {
        if (inv == null) return;
        for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
            if (viewer instanceof Player) ((Player) viewer).closeInventory();
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
