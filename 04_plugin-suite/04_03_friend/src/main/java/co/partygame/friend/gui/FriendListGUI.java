package co.partygame.friend.gui;

import co.partygame.common.util.ChatUtils;
import co.partygame.common.util.ItemBuilder;
import co.partygame.friend.FriendManager;
import co.partygame.friend.config.FriendConfig;
import co.partygame.friend.storage.FriendRecord;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * GUI for displaying the friend list with block and ignore tabs.
 *
 * Uses a single 54-slot inventory with tab items for navigation
 * between Friends, Blocks, and Ignore lists.
 */
public class FriendListGUI implements Listener {

    private static final int GUI_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 27; // 3 rows of 9

    private final FriendManager friendManager;
    private final FriendConfig config;

    /**
     * Creates a FriendListGUI instance.
     *
     * @param friendManager the instance managing friend data
     * @param config the config instance
     */
    public FriendListGUI(FriendManager friendManager, FriendConfig config) {
        this.friendManager = friendManager;
        this.config = config;
    }

    /**
     * Opens the friend list GUI and displays the friends tab.
     *
     * @param player the player to open the GUI for
     */
    public void openGUI(Player player) {
        setTab(player, 0);
        displayFriends(player, 0);
    }

    /**
     * Sets the active tab for a player's GUI.
     *
     * @param player the player
     * @param tab the tab index (0=friends, 1=block, 2=ignore)
     */
    private void setTab(Player player, int tab) {
        player.getPersistentDataContainer().set(
                co.partygame.common.util.JsonUtils.NAMESPACE,
                "friend_tab",
                org.bukkit.persistence.PersistentDataType.INTEGER,
                tab);
    }

    private int getTab(Player player) {
        if (player.getPersistentDataContainer().has(
                co.partygame.common.util.JsonUtils.NAMESPACE,
                org.bukkit.persistence.PersistentDataType.INTEGER)) {
            return player.getPersistentDataContainer().get(
                    co.partygame.common.util.JsonUtils.NAMESPACE,
                    org.bukkit.persistence.PersistentDataType.INTEGER);
        }
        return 0;
    }

    /**
     * Displays the friends tab with pagination controls.
     *
     * @param player the player viewing the list
     * @param currentPage the current page (0-based)
     */
    public void displayFriends(Player player, int currentPage) {
        Inventory inv = org.bukkit.Bukkit.createInventory(null, GUI_SIZE,
                ChatUtils.colorize("&7&lFriends"));

        List<FriendManager.FriendInfo> friends = friendManager.getPlayerFriendList(player.getUniqueId());
        int startSlot = (currentPage * ITEMS_PER_PAGE);
        int endSlot = Math.min(startSlot + ITEMS_PER_PAGE, friends.size());

        for (int i = startSlot; i < endSlot; i++) {
            FriendManager.FriendInfo info = friends.get(i);
            int slot = i - startSlot;

            Material woolType = info.online ? Material.LIME_WOOL : Material.GRAY_WOOL;
            org.bukkit.inventory.ItemStack item = new ItemBuilder(woolType)
                    .displayName(ChatUtils.colorize(info.online ? "&a" : "&7") + info.name)
                    .lore(
                            "&fStatus: &aFriend",
                            "&fOnline: " + (info.online ? "&aYes" : "&cNo")
                    )
                    .build();

            inv.setItem(slot, item);

            // Right-click to teleport (if online and has permission)
            if (info.online && player.hasPermission("partygame.friend.teleport")) {
                // Store teleport target
                // In production, this uses BungeeCord or a teleport module
            }
        }

        // Navigation items
        if (currentPage > 0) {
            inv.setItem(46, new ItemBuilder(Material.ARROW)
                    .displayName("&7&lPrevious Page")
                    .build());
        }

        inv.setItem(49, new ItemBuilder(Material.BOOK)
                .displayName("&aFriends: " + friends.size())
                .build());
        inv.setItem(50, new ItemBuilder(Material.REDSTONE)
                .displayName("&cBlocks: " + friendManager.getPlayerBlockedCount(player.getUniqueId()))
                .build());
        inv.setItem(51, new ItemBuilder(Material.BARRIER)
                .displayName("&7Ignore: " + friendManager.getPlayerIgnoreCount(player.getUniqueId()))
                .build());

        if (endSlot < friends.size()) {
            inv.setItem(52, new ItemBuilder(Material.ARROW)
                    .displayName("&7&lNext Page")
                    .build());
        }

        // Tab navigation: 38=Friends, 39=Blocks, 40=Ignore
        inv.setItem(38, new ItemBuilder(Material.LIME_WOOL)
                .displayName("&aFriends")
                .lore(currentPage == 0 ? "&7Currently viewing" : "")
                .build());
        inv.setItem(39, new ItemBuilder(Material.REDSTONE_BLOCK)
                .displayName("&cBlocks")
                .build());
        inv.setItem(40, new ItemBuilder(Material.GRAY_WOOL)
                .displayName("&7Ignore")
                .build());

        inv.setItem(44, new ItemBuilder(Material.BARRIER)
                .displayName("&cClose")
                .lore("&7Close GUI")
                .build());

        player.openInventory(inv);
        playNotificationSound(player);
    }

    /**
     * Displays the blocked players tab.
     *
     * @param player the player viewing the list
     * @param currentPage the current page (0-based)
     */
    public void displayBlockList(Player player, int currentPage) {
        Inventory inv = org.bukkit.Bukkit.createInventory(null, GUI_SIZE,
                ChatUtils.colorize("&c&lBlocked Players"));

        List<FriendRecord> blocked = friendManager.getBlockList(player.getUniqueId());

        for (int i = 0; i < 27 && i < blocked.size(); i++) {
            FriendRecord record = blocked.get(i);
            int slot = i;

            Material woolType = record.isOnline() ? Material.REDSTONE_LAMP : Material.STAINED_CLAY;

            inv.setItem(slot, new ItemBuilder(record.isOnline() ? Material.REDSTONE_LAMP : Material.STONE)
                    .displayName(ChatUtils.colorize("&c" + record.getName()))
                    .lore("&fStatus: &cBlocked", "&7Click to unblock")
                    .build());

            // Unblock button
            inv.setItem(slot + 9, new ItemBuilder(Material.LIME_WOOL)
                    .displayName("&a&lUnblock")
                    .build());
        }

        // Tab navigation
        inv.setItem(38, new ItemBuilder(Material.LIME_WOOL)
                .displayName("&aFriends")
                .build());
        inv.setItem(39, new ItemBuilder(Material.REDSTONE_BLOCK)
                .displayName("&cBlocks")
                .lore("&7Currently viewing")
                .build());
        inv.setItem(40, new ItemBuilder(Material.GRAY_WOOL)
                .displayName("&7Ignore")
                .build());

        inv.setItem(44, new ItemBuilder(Material.BARRIER)
                .displayName("&cClose")
                .build());

        player.openInventory(inv);
        playNotificationSound(player);
    }

    /**
     * Displays the ignored players tab.
     *
     * @param player the player viewing the list
     * @param currentPage the current page (0-based)
     */
    public void displayIgnoreList(Player player, int currentPage) {
        Inventory inv = org.bukkit.Bukkit.createInventory(null, GUI_SIZE,
                ChatUtils.colorize("&7&lIgnored Players"));

        List<FriendRecord> ignored = friendManager.getIgnoreList(player.getUniqueId());

        for (int i = 0; i < 27 && i < ignored.size(); i++) {
            FriendRecord record = ignored.get(i);
            int slot = i;

            inv.setItem(slot, new ItemBuilder(Material.GRAY_WOOL)
                    .displayName(ChatUtils.colorize("&7" + record.getName()))
                    .lore("&fStatus: &7Ignored", "&7Click to unignore")
                    .build());

            // Unignore button
            inv.setItem(slot + 9, new ItemBuilder(Material.GREEN_WOOL)
                    .displayName("&a&lUnignore")
                    .build());
        }

        // Tab navigation
        inv.setItem(38, new ItemBuilder(Material.LIME_WOOL)
                .displayName("&aFriends")
                .build());
        inv.setItem(39, new ItemBuilder(Material.REDSTONE_BLOCK)
                .displayName("&cBlocks")
                .build());
        inv.setItem(40, new ItemBuilder(Material.GRAY_WOOL)
                .displayName("&7Ignore")
                .lore("&7Currently viewing")
                .build());

        inv.setItem(44, new ItemBuilder(Material.BARRIER)
                .displayName("&cClose")
                .build());

        player.openInventory(inv);
        playNotificationSound(player);
    }

    private void playNotificationSound(Player player) {
        try {
            player.playSound(player.getLocation(),
                    Sound.valueOf(config.getNotificationSound()), 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    /**
     * Handles inventory click events.
     *
     * @param event the inventory click event
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        String title = inv.getTitle();

        if (!title.contains("Friends") && !title.contains("Blocked") && !title.contains("Ignored")) {
            return;
        }

        if (event.getWhoClicked() instanceof Player) {
            event.setCancelled(true);
            handleGuiClick((Player) event.getWhoClicked(), title, event.getSlot(), event.getClick());
        }
    }

    private void handleGuiClick(Player player, String title, int slot, ClickType click) {
        int tab = getTab(player);

        // Close button
        if (slot == 44) {
            player.closeInventory();
            return;
        }

        // Friend tab navigation
        if (slot == 38) {
            displayFriends(player, getTabFromTitle(title, player));
            return;
        }
        if (slot == 39) {
            displayBlockList(player, 0);
            return;
        }
        if (slot == 40) {
            displayIgnoreList(player, 0);
            return;
        }

        // Previous page
        if (slot == 46 && tab == 0) {
            int page = getTabFromTitle(title, player);
            if (page > 0) displayFriends(player, page - 1);
            return;
        }

        // Next page
        if (slot == 52 && tab == 0) {
            int page = getTabFromTitle(title, player);
            int maxPage = friendManager.getPlayerFriendList(player.getUniqueId()).size() / ITEMS_PER_PAGE;
            if (page < maxPage) displayFriends(player, page + 1);
            return;
        }

        // Friend items (slots 0-26)
        if (slot < 27 && tab == 0) {
            List<FriendManager.FriendInfo> friends = friendManager.getPlayerFriendList(player.getUniqueId());
            int startSlot = getTabFromTitle(title, player) * ITEMS_PER_PAGE;
            int index = slot + startSlot;
            if (index >= 0 && index < friends.size()) {
                FriendManager.FriendInfo info = friends.get(index);
                if (click.isRightClick() && info.online && player.hasPermission("partygame.friend.teleport")) {
                    // Teleport action
                } else {
                    // Left click - show friend info
                    player.sendMessage(ChatUtils.colorize("&aFriend: " + info.name +
                            (info.online ? " &7[Online]" : " &7[Offline]")));
                }
            }
            return;
        }

        // Unignore buttons (slot + 9)
        if (slot >= 9 && slot <= 26 && slot % 3 == 0 && tab == 2) {
            List<FriendRecord> ignored = friendManager.getIgnoreList(player.getUniqueId());
            int index = slot / 3;
            if (index >= 0 && index < ignored.size()) {
                FriendRecord record = ignored.get(index);
                friendManager.unignorePlayer(player.getUniqueId(), record.getUuid());
                player.sendMessage(ChatUtils.colorize("&aUnignored " + record.getName()));
                displayIgnoreList(player, 0);
            }
            return;
        }

        // Unblock buttons (slot + 9)
        if (slot >= 9 && slot <= 26 && slot % 3 == 0 && tab == 1) {
            List<FriendRecord> blocked = friendManager.getBlockList(player.getUniqueId());
            int index = slot / 3;
            if (index >= 0 && index < blocked.size()) {
                FriendRecord record = blocked.get(index);
                friendManager.unblockPlayer(player.getUniqueId(), record.getUuid());
                player.sendMessage(ChatUtils.colorize("&aUnblocked " + record.getName()));
                displayBlockList(player, 0);
            }
            return;
        }
    }

    private int getTabFromTitle(String title, Player player) {
        return getTab(player);
    }

    /**
     * Handles inventory close events.
     *
     * @param event the inventory close event
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // No cleanup needed
    }
}