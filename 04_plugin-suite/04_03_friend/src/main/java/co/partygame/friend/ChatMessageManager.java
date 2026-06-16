package co.partygame.friend;

import co.partygame.common.util.ChatUtils;
import co.partygame.friend.config.FriendConfig;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Objects;

/**
 * Handles chat message filtering based on block and ignore lists.
 *
 * Listens to AsyncPlayerChatEvent and filters out messages to/from
 * blocked and ignored players. Messages from blocked players are suppressed entirely,
 * and messages to blocked/ignored players are excluded from the broadcast.
 */
public class ChatMessageManager implements Listener {

    private final FriendManager friendManager;
    private final FriendConfig config;

    /**
     * Creates a ChatMessageManager instance.
     *
     * @param friendManager the instance managing friend data
     * @param config the config instance
     */
    public ChatMessageManager(FriendManager friendManager, FriendConfig config) {
        this.friendManager = Objects.requireNonNull(friendManager);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Filters chat messages based on block and ignore lists.
     * 
     * If the sender is blocked by any recipients, the message is hidden from those players.
     * If the sender has blocked any recipients, those recipients don't see the message.
     * Same logic applies for ignore lists.
     *
     * @param event the async player chat event
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();

        // Create a set of recipients to filter
        var recipients = event.getRecipients().toList();
        var validRecipients = recipients.stream()
                .filter(recipient -> !recipient.equals(sender))
                .filter(recipient -> friendManager.canMessage(sender.getUniqueId(), recipient.getUniqueId()))
                .toList();

        // If no valid recipients, cancel the event entirely
        if (validRecipients.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        // Update recipients
        event.getRecipients().clear();
        event.getRecipients().addAll(validRecipients);
    }
}
