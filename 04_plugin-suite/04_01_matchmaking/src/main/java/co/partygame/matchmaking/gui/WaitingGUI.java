package co.partygame.matchmaking.gui;

import co.partygame.common.util.ChatUtils;
import co.partygame.common.util.ItemBuilder;
import co.partygame.matchmaking.MatchQueue;
import co.partygame.matchmaking.MatchRouter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Waiting for match GUI with countdown/timer UI.
 * Shows queue position, estimated time, cancel button.
 * Auto-refreshes every 10 ticks.
 * If match accepted -> closes and redirects to backend via teleport.
 */
public class WaitingGUI implements Listener {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(WaitingGUI.class.getName());
    private final Plugin plugin;
    private final MatchQueue matchQueue;
    private final MatchRouter matchRouter;
    private final Map<UUID, BukkitTask> taskMap = new HashMap<>();

    public WaitingGUI(Plugin plugin, MatchQueue matchQueue, MatchRouter matchRouter) {
        this.plugin = Objects.requireNonNull(plugin);
        this.matchQueue = Objects.requireNonNull(matchQueue);
        this.matchRouter = Objects.requireNonNull(matchRouter);
    }

    /**
     * Opens the waiting GUI for a player.
     *
     * @param player the player to open for
     */
    public void openWaitingGUI(Player player) {
        Objects.requireNonNull(player);
        closeWaitingGUI(player);

        int size = 3 * 9;
        Inventory inv = Bukkit.createInventory(null, size, ChatUtils.colorize("&b&lWaiting for Match"));

        int queuePos = matchQueue.getQueuePosition(player);
        int queueSize = 0;
        String gameType = "unknown";
        if (queuePos >= 0) {
            MatchQueue.QueueEntry entry = matchQueue.getEntry(player.getUniqueId());
            if (entry != null) gameType = entry.gameType;
        }
        if (!gameType.equals("unknown")) {
            queueSize = matchQueue.getQueueSize(gameType);
        }
        int estTime = matchQueue.getEstimatedWaitTime(queueSize, 30);

        inv.setItem(10, new ItemBuilder(Material.PAPER)
                .displayName("&b&lQueue Position")
                .lore("&7Position: &e&l" + (queuePos >= 0 ? queuePos + 1 : "?"),
                        "&7Size: &e" + queueSize, "&7Game: &e" + gameType)
                .build());

        inv.setItem(12, new ItemBuilder(Material.CLOCK)
                .displayName("&b&lEstimated Wait")
                .lore("&7Time: &e" + formatTime(estTime))
                .build());

        inv.setItem(14, new ItemBuilder(Material.BARRIER)
                .displayName("&c&lCancel Queue")
                .lore("&7Remove yourself from the queue")
                .build());

        inv.setItem(16, new ItemBuilder(Material.ARROW)
                .displayName("&a&lBack to Menu")
                .lore("&7Return to game selection")
                .build());

        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        player.openInventory(inv);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inv) {
                task.cancel();
                taskMap.remove(player.getUniqueId());
                return;
            }
            int qPos = matchQueue.getQueuePosition(player);
            int qSize = !gameType.equals("unknown") ? matchQueue.getQueueSize(gameType) : 0;
            int eTime = matchQueue.getEstimatedWaitTime(qSize, 30);

            inv.setItem(10, new ItemBuilder(Material.PAPER)
                    .displayName("&b&lQueue Position")
                    .lore("&7Position: &e&l" + (qPos >= 0 ? qPos + 1 : "?"),
                            "&7Size: &e" + qSize, "&7Game: &e" + gameType)
                    .build());
            inv.setItem(12, new ItemBuilder(Material.CLOCK)
                    .displayName("&b&lEstimated Wait")
                    .lore("&7Time: &e" + formatTime(eTime))
                    .build());

            if (qPos < 0 && !matchQueue.isInQueue(player)) {
                player.closeInventory();
                MatchGUI mg = ((co.partygame.matchmaking.MatchmakingPlugin) plugin).getMatchGUI();
                if (mg != null) mg.openMatchGUI(player);
                task.cancel();
                taskMap.remove(player.getUniqueId());
            }
        }, 0L, 200L);

        taskMap.put(player.getUniqueId(), task);
    }

    /**
     * Updates the GUI with current queue stats.
     *
     * @param player        the player
     * @param queuePosition the queue position
     * @param waitTime      wait time (deprecated, use estimatedTime)
     * @param estimatedTime the estimated wait time
     */
    public void updateGUI(Player player, int queuePosition, int waitTime, int estimatedTime) {
        updateGUI(player, queuePosition, estimatedTime);
    }

    /**
     * Updates the GUI with current queue stats.
     *
     * @param player        the player
     * @param queuePosition the queue position
     * @param estimatedTime the estimated wait time
     */
    @Deprecated
    public void updateGUI(Player player, int queuePosition, int estimatedTime) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (inv == null || !inv.getTitle().contains("Waiting")) return;

        int qSize = 0;
        if (queuePosition >= 0) {
            MatchQueue.QueueEntry e = matchQueue.getEntry(player.getUniqueId());
            if (e != null) qSize = matchQueue.getQueueSize(e.gameType);
        }

        inv.setItem(10, new ItemBuilder(Material.PAPER)
                .displayName("&b&lQueue Position")
                .lore("&7Position: &e&l" + (queuePosition >= 0 ? queuePosition + 1 : "?"),
                        "&7Size: &e" + qSize)
                .build());
        inv.setItem(12, new ItemBuilder(Material.CLOCK)
                .displayName("&b&lEstimated Wait")
                .lore("&7Time: &e" + formatTime(estimatedTime))
                .build());
    }

    /**
     * Cancels matchmaking for player.
     *
     * @param player the player
     */
    public void cancelMatch(Player player) {
        if (!matchQueue.isInQueue(player)) return;
        matchQueue.removeFromQueue(player);
        player.sendMessage(ChatUtils.colorize("&cCancelled matchmaking"));
        closeWaitingGUI(player);
    }

    /**
     * Checks if player has waiting GUI open.
     *
     * @param player the player to check
     * @return true if waiting
     */
    public boolean isInWaiting(Player player) {
        if (player == null) return false;
        return player.getOpenInventory().getTopInventory() != null
                && player.getOpenInventory().getTopInventory().getTitle().contains("Waiting");
    }

    /**
     * Closes the waiting GUI for a player.
     *
     * @param player the player
     */
    public void closeWaitingGUI(Player player) {
        if (player == null) return;
        player.closeInventory();
        BukkitTask t = taskMap.remove(player.getUniqueId());
        if (t != null) t.cancel();
    }

    /**
     * Refreshes all players' waiting GUIs.
     */
    public void refreshAllPlayers() {
        for (UUID uuid : new ArrayList<>(taskMap.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && isInWaiting(p)) p.updateInventory();
        }
    }

    @EventHandler
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getInventory() == null || !event.getInventory().getTitle().contains("Waiting")) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
            int slot = event.getSlot();
            if (slot == 14) {
                cancelMatch(player);
                co.partygame.matchmaking.MatchGUI mg = ((co.partygame.matchmaking.MatchmakingPlugin) plugin).getMatchGUI();
                if (mg != null) mg.openMatchGUI(player);
            } else if (slot == 16) {
                closeWaitingGUI(player);
                co.partygame.matchmaking.MatchGUI mg = ((co.partygame.matchmaking.MatchmakingPlugin) plugin).getMatchGUI();
                if (mg != null) mg.openMatchGUI(player);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            taskMap.remove(((Player) event.getPlayer()).getUniqueId());
        }
    }

    private String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return (m > 0 ? m + "m " : "") + s + "s";
    }
}
