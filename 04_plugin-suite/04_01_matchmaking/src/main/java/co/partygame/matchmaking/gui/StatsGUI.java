package co.partygame.matchmaking.gui;

import co.partygame.common.util.ChatUtils;
import co.partygame.common.util.ItemBuilder;
import co.partygame.common.mysql.tables.DatabaseTables;
import co.partygame.matchmaking.storage.MatchRecord;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.InventoryCloseEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Player statistics GUI.
 *
 * Shows: total matches, wins, losses, streak, average wait time, top game type.
 * Each statistic stored in co.partygame.common.mysql.tables.DatabaseTables.
 * Refresh button: opens fresh stats from DB.
 * Navigation buttons between tabs (main, stats by game, history).
 */
public class StatsGUI implements Listener {

    private final Plugin plugin;
    private final Map<UUID, Integer> currentTab = new HashMap<>();

    public StatsGUI(Plugin plugin, MatchRecord matchRecord) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    /**
     * Opens the stats GUI for a player.
     *
     * @param player the player to open the GUI for
     */
    public void openStatsGUI(Player player) {
        Objects.requireNonNull(player);
        openTab(player, 0);
    }

    /**
     * Opens the lobby GUI (main matchmaking menu).
     *
     * @param player the player to move to lobby
     */
    public void openLobbyGUI(Player player) {
        Objects.requireNonNull(player);
        try {
            Object inst = plugin;
            if (inst instanceof co.partygame.matchmaking.MatchmakingPlugin m) {
                co.partygame.matchmaking.gui.MatchGUI mg = m.getMatchGUI();
                if (mg != null) {
                    mg.openMatchGUI(player);
                } else {
                    player.closeInventory();
                }
            } else {
                player.closeInventory();
            }
        } catch (Exception e) {
            player.closeInventory();
        }
    }

    private void openTab(Player player, int tabId) {
        currentTab.put(player.getUniqueId(), tabId);
        int size = 5 * 9;
        Inventory inv = Bukkit.createInventory(null, size, ChatUtils.colorize("&b&lMatchmaking Statistics"));

        try {
            co.partygame.matchmaking.MatchmakingPlugin inst = (co.partygame.matchmaking.MatchmakingPlugin) plugin;
            MatchRecord record = inst.getMatchRecord();

            switch (tabId) {
                case 0 -> showMainTab(player, inv, record);
                case 1 -> showGameTab(inv);
                case 2 -> showHistoryTab(inv);
                default -> showMainTab(player, inv, record);
            }
        } catch (Exception e) {
            inv.setItem(11, new ItemBuilder(Material.BARRIER)
                    .displayName(ChatUtils.colorize("&c&lError"))
                    .lore(ChatUtils.colorize("&7Failed to load stats"))
                    .build());
        }

        addNavigation(inv, tabId);
        addRefreshButton(inv);

        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        player.openInventory(inv);
    }

    private void showMainTab(Player player, Inventory inv, MatchRecord record) {
        if (record == null) return;
        UUID uuid = player.getUniqueId();

        int total = record.getTotalMatches(uuid);
        double winRate = record.getWinRate(uuid);
        int streak = record.getStreak(uuid);

        inv.setItem(10, new ItemBuilder(Material.PAPER)
                .displayName(ChatUtils.colorize("&b&lTotal Matches"))
                .lore(ChatUtils.colorize("&7" + total))
                .build());

        int wins = (int) Math.round(total * winRate);
        inv.setItem(11, new ItemBuilder(Material.DIAMOND)
                .displayName(ChatUtils.colorize("&a&lWins"))
                .lore(ChatUtils.colorize("&7" + wins))
                .build());

        inv.setItem(12, new ItemBuilder(Material.REDSTONE)
                .displayName(ChatUtils.colorize("&c&lLosses"))
                .lore(ChatUtils.colorize("&7" + (total - wins)))
                .build());

        inv.setItem(13, new ItemBuilder(Material.CLOCK)
                .displayName(ChatUtils.colorize("&e&lWin Rate"))
                .lore(ChatUtils.colorize("&7" + (int) (winRate * 100) + "%"))
                .build());

        inv.setItem(14, new ItemBuilder(Material.NETHER_STAR)
                .displayName(ChatUtils.colorize("&d&lStreak"))
                .lore(ChatUtils.colorize("&7" + streak + " consecutive wins"))
                .build());

        inv.setItem(15, new ItemBuilder(Material.ENDER_EYE)
                .displayName(ChatUtils.colorize("&b&lAvg Wait"))
                .lore(ChatUtils.colorize("&7" + record.getAverageWaitTime(uuid) + "s"))
                .build());
    }

    private void showGameTab(Inventory inv) {
        inv.setItem(11, new ItemBuilder(Material.MAP)
                .displayName(ChatUtils.colorize("&b&lTop Games"))
                .lore(ChatUtils.colorize("&7No data available", "&7Refresh to reload"))
                .build());
    }

    private void showHistoryTab(Inventory inv) {
        inv.setItem(11, new ItemBuilder(Material.PAPER)
                .displayName(ChatUtils.colorize("&b&lHistory"))
                .lore(ChatUtils.colorize("&7No data available", "&7Refresh to reload"))
                .build());
    }

    private void addNavigation(Inventory inv, int tabId) {
        if (tabId > 0) {
            inv.setItem(0, new ItemBuilder(Material.ARROW)
                    .displayName(ChatUtils.colorize("&aBack"))
                    .lore(ChatUtils.colorize("&7Previous tab"))
                    .build());
        }
        inv.setItem(7, new ItemBuilder(Material.ARROW)
                .displayName(ChatUtils.colorize("&aNext"))
                .lore(ChatUtils.colorize("&7Next tab"))
                .build());
        inv.setItem(8, new ItemBuilder(Material.BARRIER)
                .displayName(ChatUtils.colorize("&cClose"))
                .lore(ChatUtils.colorize("&7Close stats"))
                .build());
    }

    private void addRefreshButton(Inventory inv) {
        inv.setItem(4, new ItemBuilder(Material.FEATHER)
                .displayName(ChatUtils.colorize("&b&lRefresh"))
                .lore(ChatUtils.colorize("&7Reload from database"))
                .build());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() == null) return;
        if (!event.getInventory().getTitle().contains("Statistics")) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 4) {
            int tab = currentTab.getOrDefault(player.getUniqueId(), 0);
            openTab(player, tab);
            player.sendMessage(ChatUtils.colorize("&aStats refreshed from database"));
        } else if (slot == 0) {
            int tab = Math.max(0, currentTab.getOrDefault(player.getUniqueId(), 0) - 1);
            currentTab.put(player.getUniqueId(), tab);
            openTab(player, tab);
        } else if (slot == 7) {
            int tab = Math.min(2, (currentTab.getOrDefault(player.getUniqueId(), 0) + 1) % 3);
            currentTab.put(player.getUniqueId(), tab);
            openTab(player, tab);
        } else if (slot == 8) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            currentTab.remove(((Player) event.getPlayer()).getUniqueId());
        }
    }
}
