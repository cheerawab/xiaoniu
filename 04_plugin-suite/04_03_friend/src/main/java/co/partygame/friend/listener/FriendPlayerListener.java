package co.partygame.friend.listener;

import co.partygame.friend.FriendPlugin;
import co.partygame.friend.FriendManager;
import co.partygame.friend.config.FriendConfig;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * Handles Bukkit player events (join/quit) related to the friend system.
 *
 * Broadcasts player join/leave messages to all online friends, and updates
 * online status for cross-server synchronization.
 */
public class FriendPlayerListener implements Listener {

    private final FriendPlugin plugin;
    private final FriendManager friendManager;
    private final FriendConfig configManager;

    /**
     * Creates a FriendListener instance.
     *
     * @param plugin the main plugin instance
     */
    public FriendPlayerListener(FriendPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.friendManager = plugin.getFriendManager();
        this.configManager = plugin.getFriendConfig();
    }

    /**
     * Handles player join events - broadcast to friends and update online status.
     *
     * @param event the player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Broadcast join message to all online friends
        if (configManager.isOnlineBroadcast()) {
            String joinMsg = configManager.getJoinMessage()
                    .replace("{display_name}", player.getDisplayName());

            for (UUID friendId : friendManager.getAllFriends(player.getUniqueId())) {
                Player friend = Bukkit.getPlayer(friendId);
                if (friend != null && friend.isOnline()) {
                    co.partygame.common.util.ChatUtils.msg(friend, "&a[Friend] " + joinMsg);
                }
            }
        }
    }

    /**
     * Handles player quit events - save data, broadcast to friends, update status.
     *
     * @param event the player quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Save all friend data for this player on quit
        try {
            friendManager.saveChanges(player.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save friend data for " + player.getName());
        }

        // Broadcast quit message to all online friends
        if (configManager.isOnlineBroadcast()) {
            String quitMsg = configManager.getLeaveMessage()
                    .replace("{display_name}", player.getDisplayName());

            for (UUID friendId : friendManager.getAllFriends(player.getUniqueId())) {
                Player friend = Bukkit.getPlayer(friendId);
                if (friend != null && friend.isOnline()) {
                    co.partygame.common.util.ChatUtils.msg(friend, "&a[Friend] " + quitMsg);
                }
            }
        }
    }

    /**
     * Plays a notification sound for friend events.
     *
     * @param player the player to play the sound for
     */
    public void playNotificationSound(Player player) {
        try {
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.valueOf(configManager.getNotificationSound()), 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }
}
