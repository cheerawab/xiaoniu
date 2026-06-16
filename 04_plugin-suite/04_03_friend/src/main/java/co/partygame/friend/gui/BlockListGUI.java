package co.partygame.friend.gui;

import co.partygame.common.util.ChatUtils;
import co.partygame.common.util.ItemBuilder;
import co.partygame.friend.FriendManager;
import co.partygame.friend.config.FriendConfig;
import co.partygame.friend.storage.FriendRecord;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * GUI for viewing and managing blocked players.
 *
 * Shows all blocked players with unblock buttons.
 */
public class BlockListGUI implements Listener {

    private static final int GUI_SIZE = 27; // 3 rows
    private static final int ITEMS_PER_ROW = 9;

    private final FriendManager friendManager;
    private final FriendConfig config;

    /**
     * Creates a BlockListGUI instance.
     *
     * @param friendManager the instance managing friend data
     * @param config the config instance
     */
    public BlockListGUI(FriendManager friendManager, FriendConfig config) {
        this.friendManager = friendManager;
        this.config = config;
    }

    /**
     * Opens the block list GUI for a player.
     *
     * @param player the player to open the GUI for
     */
    public void openGUI(Player player) {
        Inventory inv = org.bukkit.Bukkit.createInventory(null, GUI_SIZE,
                ChatUtils.colorize("&c&lBlocked Players"));

        List<FriendRecord> blocked = friendManager.getBlockList(player.getUniqueId());

        if (blocked.isEmpty()) {
            inv.setItem(4, new ItemBuilder(Material.LIME_WOOL)
                    .displayName(ChatUtils.colorize("&aNo Blocked Players"))
                    .lore(
                            "&fYou haven't blocked anyone.",
                            "&fUse &c/friend block <player>&f to block someone.")
                    .build());
        } else {
            int i = 0;
            for (FriendRecord record : blocked) {
                if (i >= ITEMS_PER_ROW) break;
                int baseSlot = i * 3;

                // Player name item
                inv.setItem(baseSlot, new ItemBuilder(Material.BARRIER)
                        .displayName(ChatUtils.colorize("&c" + record.getName()))
                        .lore("&fStatus: &cBlocked", "&7Click to unblock")
                        .build());

                // Unblock button
                inv.setItem(baseSlot + 1, new ItemBuilder(Material.LIME_WOOL)
                        .displayName(ChatUtils.colorize("&a&lUnblock"))
                        .lore("&7Click to remove from block list")
                        .build());
            }
        }

        // Close button
        inv.setItem(8, new ItemBuilder(Material.BARRIER)
                .displayName(ChatUtils.colorize("&cClose"))
                .build());

        player.openInventory(inv);
    }

    /**
     * Handles inventory click events in the block list GUI.
     *
     * @param event the inventory click event
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView() == null || event.getView().getTitle() == null) return;

        if (!event.getView().getTitle().equals(ChatUtils.colorize("&c&lBlocked Players"))) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Close button
        if (slot == 8) {
            player.closeInventory();
            return;
        }

        // Unblock buttons (slot % 3 == 1)
        // Also click on player names (slot % 3 == 0)
        if (slot % 3 >= 0 && slot % 3 <= 1 && slot < 24) {
            List<FriendRecord> blocked = friendManager.getBlockList(player.getUniqueId());
            int index = slot / 3;

            if (index >= 0 && index < blocked.size()) {
                FriendRecord record = blocked.get(index);
                friendManager.unblockPlayer(player.getUniqueId(), record.getUuid());
                player.sendMessage(ChatUtils.colorize("&aUnblocked &e" + record.getName()));
            }

            // Refresh
            openGUI(player);
        }
    }
}
