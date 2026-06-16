package co.partygame.matchmaking.gui.customoptions;

import co.partygame.common.util.ChatUtils;
import co.partygame.common.util.ItemBuilder;
import co.partygame.matchmaking.MatchQueue;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * DYNAMIC custom options GUI - reads from backend's game config.
 * Each option from schema defined as a slot/item in the GUI.
 * Click handler cycles values, increments/decrements, toggles booleans.
 * Options fetched dynamically from backend (not hardcoded).
 * Uses placeholder to show current value of each option.
 * Finalizes when player clicks confirm -> sends back to MatchGUI.
 */
public class CustomOptionsGUI implements Listener {

    private final Plugin plugin;
    private final Map<UUID, CustomOptionsState> openGuis = new HashMap<>();

    public CustomOptionsGUI(Plugin plugin, MatchQueue matchQueue) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    /**
     * Opens the custom options GUI for a player.
     *
     * @param player           the player to open for
     * @param gameId           the game identifier
     * @param currentOptions   current option values
     */
    public void openCustomOptionsGUI(Player player, String gameId, Map<String, Object> currentOptions) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(gameId);
        closeCustomOptionsGUI(player);

        int size = 5 * 9;
        Inventory inv = Bukkit.createInventory(null, size, ChatUtils.colorize("&b&l" + gameId + " - Options"));

        if (currentOptions == null || currentOptions.isEmpty()) {
            inv.setItem(11, new ItemBuilder(Material.GREEN_STAINED_GLASS_PANE)
                    .displayName(ChatUtils.colorize("&a&lNo Options"))
                    .lore(ChatUtils.colorize("&7No custom options available", "&7Proceed with defaults"))
                    .build());
        } else {
            buildOptionsList(inv, currentOptions, gameId);
        }

        inv.setItem(3, new ItemBuilder(Material.LIME_DYE)
                .displayName(ChatUtils.colorize("&a&lConfirm"))
                .lore(ChatUtils.colorize("&7Apply these options and join"))
                .build());

        inv.setItem(5, new ItemBuilder(Material.RED_DYE)
                .displayName(ChatUtils.colorize("&c&lCancel"))
                .lore(ChatUtils.colorize("&7Return to game selection"))
                .build());

        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        CustomOptionsState state = new CustomOptionsState(gameId, currentOptions, inv);
        openGuis.put(player.getUniqueId(), state);
        player.openInventory(inv);
    }

    /**
     * Builds the options list from schema definitions.
     * Each option from schema defined as a slot/item in the GUI.
     *
     * @param inv            the inventory to populate
     * @param options        the current options
     * @param gameId         the game id for reference
     */
    public void buildOptionsList(Inventory inv, Map<String, Object> options, String gameId) {
        Map<String, Object> schema = getSchema(gameId);
        int slot = 8;

        for (Map.Entry<String, Object> entry : options.entrySet()) {
            if (slot >= 17) break;
            String key = entry.getKey();
            Object val = entry.getValue();

            Object fieldSchema = schema.get(key);
            String type = "string";
            Map<String, Object> schemaMap = new LinkedHashMap<>();
            if (fieldSchema instanceof Map) {
                schemaMap = (Map<String, Object>) fieldSchema;
                type = (String) schemaMap.getOrDefault("type", "string");
            }

            String displayName = key.replace("_", " ").replace("-", " ");
            String displayVal = formatValue(type, val);

            ItemStack item;
            switch (type) {
                case "boolean":
                    boolean currentBool = val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(val.toString());
                    item = new ItemBuilder(currentBool ? Material.LIME_DYE : Material.RED_DYE)
                            .displayName(ChatUtils.colorize("&b&l" + capitalize(displayName)))
                            .lore(ChatUtils.colorize("&7Current: " + (currentBool ? "&a&lTRUE" : "&c&lFALSE")))
                            .build();
                    break;
                case "enum":
                    @SuppressWarnings("unchecked")
                    List<String> enumValues = (List<String>) schemaMap.getOrDefault("enum", Collections.singletonList(val.toString()));
                    int idx = enumValues.indexOf(val != null ? val.toString() : "");
                    item = new ItemBuilder(Material.PAPER)
                            .displayName(ChatUtils.colorize("&b&l" + capitalize(displayName)))
                            .lore(ChatUtils.colorize("&7Value: &e" + displayVal),
                                    "&7Click to cycle")
                            .build();
                    break;
                case "int":
                    int minVal = schemaMap.containsKey("min")
                            ? ((Number) schemaMap.get("min")).intValue() : 1;
                    int maxVal = schemaMap.containsKey("max")
                            ? ((Number) schemaMap.get("max")).intValue() : 10;
                    item = new ItemBuilder(Material.NOTE_BLOCK)
                            .displayName(ChatUtils.colorize("&b&l" + capitalize(displayName)))
                            .lore(ChatUtils.colorize("&7Value: &e" + displayVal),
                                    "&7Min: " + minVal + " / Max: " + maxVal,
                                    "&7Click to increment / shift - decrement")
                            .build();
                    break;
                default:
                    item = new ItemBuilder(Material.PAPER)
                            .displayName(ChatUtils.colorize("&b&l" + capitalize(displayName)))
                            .lore(ChatUtils.colorize("&7Value: &e" + displayVal))
                            .build();
            }
            inv.setItem(slot, item);
            slot++;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() == null) return;
        if (!event.getInventory().getTitle().contains("Options")) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        CustomOptionsState state = openGuis.get(player.getUniqueId());
        if (state == null) return;

        if (slot == 3) {
            player.closeInventory();
            player.sendMessage(ChatUtils.colorize("&aOptions applied!"));
        } else if (slot == 5) {
            player.sendMessage(ChatUtils.colorize("&cOptions cancelled"));
        } else if (slot >= 8 && slot <= 16) {
            int idx = slot - 8;
            handleOptionClick(player, state, event.getClick(), idx);
        }
    }

    /**
     * Closes the custom options GUI for a player.
     *
     * @param player the player to close for
     */
    public void closeCustomOptionsGUI(Player player) {
        if (player != null) {
            player.closeInventory();
            openGuis.remove(player.getUniqueId());
        }
    }

    /**
     * Closes all custom options GUIs.
     */
    public void closeAll() {
        openGuis.clear();
    }

    private Map<String, Object> getSchema(String gameId) {
        try {
            co.partygame.matchmaking.MatchmakingPlugin inst =
                    (co.partygame.matchmaking.MatchmakingPlugin) plugin;
            co.partygame.matchmaking.GamePayloadBuilder builder = inst.getPayloadBuilder();
            if (builder != null) {
                co.partygame.matchmaking.GamePayloadBuilder.GameTypeConfig config =
                        builder.getGameTypeConfig(gameId);
                if (config != null) {
                    return config.schema;
                }
            }
        } catch (Exception ignored) {
        }
        return getDefaultSchema();
    }

    private Map<String, Object> getDefaultSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("difficulty", Map.of("type", "enum", "enum", Arrays.asList("easy", "normal", "hard"),
                "default", "normal", "description", "Game difficulty"));
        schema.put("rounds", Map.of("type", "int", "min", 1, "max", 10,
                "default", 5, "description", "Number of rounds"));
        schema.put("pvp", Map.of("type", "boolean", "default", true,
                "description", "Enable player vs player"));
        return schema;
    }

    private void handleOptionClick(Player player, CustomOptionsState state, ClickType click, int idx) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        Map<String, Object> schema = getSchema(state.gameId);
        int slot = idx + 8;
        ItemStack item = inv.getItem(slot);
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        String displayName = item.getItemMeta().getDisplayName().replaceAll("[&[a-f0-9]]", "").trim();

        List<String> keys = new ArrayList<>(schema.keySet());
        if (keys.size() > idx) {
            String key = keys.get(idx);
            Object val = state.options.getOrDefault(key, schema.containsKey(key)
                    ? ((Map<String, Object>) schema.get(key)).getOrDefault("default", "") : "");

            String type = "string";
            if (schema.get(key) instanceof Map) {
                type = (String) ((Map<String, Object>) schema.get(key)).getOrDefault("type", "string");
            }

            switch (type) {
                case "boolean":
                    boolean current = val instanceof Boolean ? (Boolean) val : Boolean.TRUE.equals(val);
                    state.options.put(key, !current);
                    break;
                case "enum":
                    Object schemaObj = schema.get(key);
                    if (schemaObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        List<String> vals = (List<String>) ((Map<String, Object>) schemaObj).getOrDefault("enum", List.of(val != null ? val.toString() : ""));
                        int ci = vals.indexOf(val != null ? val.toString() : "");
                        int ni = (ci + (click == ClickType.RIGHT || click == ClickType.LEFT ? 1 : vals.size() - 1)) % vals.size();
                        state.options.put(key, vals.get(ni));
                    }
                    break;
                case "int":
                    int iv = val instanceof Number ? ((Number) val).intValue() : 1;
                    int step = click.isShiftClick() ? -1 : 1;
                    state.options.put(key, iv + step);
                    break;
            }
            buildOptionsList(inv, state.options, state.gameId);
        }
    }

    private String formatValue(String type, Object val) {
        if (val == null) return "None";
        return val.toString();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /**
     * Holds temporary options state for a player's open GUI.
     */
    public static class CustomOptionsState {
        public final String gameId;
        public final Map<String, Object> options;
        public final Inventory inventory;

        public CustomOptionsState(String gameId, Map<String, Object> options, Inventory inventory) {
            this.gameId = Objects.requireNonNull(gameId);
            this.options = new LinkedHashMap<>(options != null ? options : Map.of());
            this.inventory = inventory;
        }
    }
}
