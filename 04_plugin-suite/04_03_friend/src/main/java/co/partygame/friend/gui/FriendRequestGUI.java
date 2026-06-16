package co.partygame.friend.gui;

import co.partygame.common.util.ChatUtils;
import co.partygame.common.util.ItemBuilder;
import co.partygame.friend.FriendManager;
import co.partygame.friend.config.FriendConfig;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI for viewing and managing incoming friend requests.
 *
 * Shows all pending friend invites with accept/reject buttons.
 * Only the receiving player's invites are displayed.
 */
public class FriendRequestGUI implements Listener {

    private static final int GUI_SIZE = 27; // 3 rows
    private static final int ITEMS_PER_ROW = 9;

    private final FriendManager friendManager;
    private final FriendConfig config;

    /**
     * Creates a FriendRequestGUI instance.
     *
     * @param friendManager the instance managing friend data
     * @param config the config instance
     */
    public FriendRequestGUI(FriendManager friendManager, FriendConfig config) {
        this.friendManager = friendManager;
        this.config = config;
    }

    /**
     * Opens the friend requests GUI for a player.
     *
     * @param player the player to open the GUI for
     */
    public void openGUI(Player player) {
        Inventory inv = org.bukkit.Bukkit.createInventory(null, GUI_SIZE,
                ChatUtils.colorize("&6&lFriend Requests"));

        Map<FriendManager.InviteInfo> invites = friendManager.getPendingInvites(player.getUniqueId());
        List<FriendManager.InviteInfo> inviteList = new ArrayList<>(invites.values());

        if (inviteList.isEmpty()) {
            inv.setItem(4, new ItemBuilder(Material.PAPER)
                    .displayName(ChatUtils.colorize("&7No Pending Requests"))
                    .lore(
                            "&fYou have no friend requests right now.",
                            "&fAsk your friends to send you a request!")
                    .build());
        } else {
            int i = 0;
            for (FriendManager.InviteInfo info : inviteList) {
                if (i >= ITEMS_PER_ROW) break;
                int baseSlot = i * 3;

                // Inviter name
                inv.setItem(baseSlot, new ItemBuilder(Material.PLAYER_HEAD)
                        .displayName(ChatUtils.colorize("&e" + info.inviterName))
                        .lore("&fSent a friend request")
                        .build());

                // Accept button
                inv.setItem(baseSlot + 1, new ItemBuilder(Material.LIME_WOOL)
                        .displayName(ChatUtils.colorize("&a&lAccept"))
                        .lore("&7Click to accept this request")
                        .build());

                // Reject button
                inv.setItem(baseSlot + 2, new ItemBuilder(Material.RED_WOOL)
                        .displayName(ChatUtils.colorize("&c&lReject"))
                        .lore("&7Click to reject this request")
                        .build());

                i++;
            }
        }

        // Close button
        inv.setItem(8, new ItemBuilder(Material.BARRIER)
                .displayName(ChatUtils.colorize("&cClose"))
                .build());

        player.openInventory(inv);
    }

    /**
     * Handles inventory click events in the requests GUI.
     *
     * @param event the inventory click event
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView() == null || event.getView().getTitle() == null) return;

        if (!event.getView().getTitle().equals(ChatUtils.colorize("&6&lFriend Requests"))) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Close button
        if (slot == 8) {
            player.closeInventory();
            return;
        }

        Map<FriendManager.InviteInfo> invites = friendManager.getPendingInvites(player.getUniqueId());
        List<FriendManager.InviteInfo> inviteList = new ArrayList<>(invites.values());
        int inviteIndex = slot / 3;

        if (inviteIndex < 0 || inviteIndex >= inviteList.size()) return;

        FriendManager.InviteInfo info = inviteList.get(inviteIndex);
        int relativeSlot = slot % 3;

        if (relativeSlot == 1) {
            // Accept the request
            friendManager.acceptInvite(info.inviter, info.inviterName, info.partyId, player.getUniqueId());
            player.sendMessage(ChatUtils.colorize("&aYou accepted the friend request from &e" + info.inviterName));
        } else if (relativeSlot == 2) {
            // Reject
            friendManager.rejectInvite(info.inviter, player.getUniqueId());
            player.sendMessage(ChatUtils.colorize("&cYou rejected the friend request from &e" + info.inviterName));
        }

        // Refresh
        if (!invites.isEmpty()) {
            openGUI(player);
        }
    }
}
