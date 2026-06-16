package co.partygame.friend.config;

import co.partygame.common.config.ConfigManager;

import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the Friend plugin's configuration loaded from config.yml.
 *
 * Wraps ConfigManager to provide type-safe access to friend-specific settings
 * including message templates, limits, and feature toggles.
 */
public class FriendConfig {

    private static final Logger LOGGER = Logger.getLogger(FriendConfig.class.getName());

    private final ConfigManager configManager;

    // Message templates
    private String joinMessage;
    private String leaveMessage;
    private String addMessage;
    private String removeMessage;
    private String blockMessage;
    private String inviteMessage;
    private String gameInviteMessage;
    private String ignoredBlocksMessage;

    // Feature settings
    private boolean onlineBroadcast;
    private boolean blocksOnJoin;
    private int maxFriends;
    private String notificationSound;

    // Config keys
    private static final String KEY_JOIN_MESSAGE = "messages.join";
    private static final String KEY_LEAVE_MESSAGE = "messages.leave";
    private static final String KEY_ADD_MESSAGE = "messages.add";
    private static final String KEY_REMOVE_MESSAGE = "messages.remove";
    private static final String KEY_BLOCK_MESSAGE = "messages.block";
    private static final String KEY_IGNORE_BLOCKS_MESSAGE = "messages.ignored_blocks";
    private static final String KEY_INVITE_MESSAGE = "messages.invite";
    private static final String KEY_GAME_INVITE_MESSAGE = "messages.game_invite";
    private static final String KEY_BLOCKS_ON_JOIN = "blocks_on_join";
    private static final String KEY_MAX_FRIENDS = "max_friends";
    private static final String KEY_ONLINE_BROADCAST = "online_broadcast";
    private static final String KEY_NOTIFICATION_SOUND = "notification_sound";

    /**
     * Creates a new FriendConfig instance.
     *
     * @param plugin the owning plugin instance
     */
    public FriendConfig(Plugin plugin) {
        this.configManager = new ConfigManager(plugin, "config.yml");
    }

    /**
     * Loads the configuration file.
     * Must be called before accessing any values.
     */
    public void load() {
        configManager.loadConfig();
        refreshValues();
        LOGGER.info("Friend config loaded successfully");
    }

    /**
     * Reloads the configuration from disk and refreshes values.
     */
    public void reload() {
        configManager.reloadConfig();
        refreshValues();
        LOGGER.info("Friend config reloaded");
    }

    private void refreshValues() {
        joinMessage = configManager.getString(KEY_JOIN_MESSAGE, "&a{display_name} &7has joined the game");
        leaveMessage = configManager.getString(KEY_LEAVE_MESSAGE, "&c{display_name} &7has left the game");
        addMessage = configManager.getString(KEY_ADD_MESSAGE, "&aYou have added &e{display_name}&a as a friend");
        removeMessage = configManager.getString(KEY_REMOVE_MESSAGE, "&cYou have removed &e{display_name}&c as a friend");
        blockMessage = configManager.getString(KEY_BLOCK_MESSAGE, "&cYou have blocked &e{display_name}&c");
        inviteMessage = configManager.getString(KEY_INVITE_MESSAGE, "&a{display_name} &7invited you to join their {game_name} game! /friend game_invite");
        gameInviteMessage = configManager.getString(KEY_GAME_INVITE_MESSAGE, "&a{display_name} &7invited you to join a game!");
        ignoredBlocksMessage = configManager.getString(KEY_IGNORE_BLOCKS_MESSAGE, "&6{display_name}&7's messages are now ignored (friend is on block/ignore list)");

        blocksOnJoin = configManager.getBoolean(KEY_BLOCKS_ON_JOIN, false);
        maxFriends = configManager.getInt(KEY_MAX_FRIENDS, 50);
        onlineBroadcast = configManager.getBoolean(KEY_ONLINE_BROADCAST, true);
        notificationSound = configManager.getString(KEY_NOTIFICATION_SOUND, "ui.button.click");
    }

    /**
     * Gets the friend join message template.
     *
     * @return the join message string with {display_name} placeholder
     */
    public String getJoinMessage() { return joinMessage; }

    /**
     * Gets the friend leave message template.
     *
     * @return the leave message string with {display_name} placeholder
     */
    public String getLeaveMessage() { return leaveMessage; }

    /**
     * Gets the friend added message template.
     *
     * @return the add message string with {display_name} placeholder
     */
    public String getAddMessage() { return addMessage; }

    /**
     * Gets the friend removed message template.
     *
     * @return the remove message string with {display_name} placeholder
     */
    public String getRemoveMessage() { return removeMessage; }

    /**
     * Gets the block message template.
     *
     * @return the block message string with {display_name} placeholder
     */
    public String getBlockMessage() { return blockMessage; }

    /**
     * Gets the ignored/blocked message template.
     *
     * @return the blocked message string with {display_name} placeholder
     */
    public String getIgnoredBlocksMessage() { return ignoredBlocksMessage; }

    /**
     * Gets the party invite message template.
     *
     * @return the invite message string with {display_name} and {game_name} placeholders
     */
    public String getInviteMessage() { return inviteMessage; }

    /**
     * Gets the game invite message template.
     *
     * @return the game invite message string
     */
    public String getGameInviteMessage() { return gameInviteMessage; }

    /**
     * Gets whether blocks should be cleared on player logout.
     *
     * @return true if blocks are removed on logout
     */
    public boolean isBlocksOnJoin() { return blocksOnJoin; }

    /**
     * Gets the maximum number of friends a player can have.
     *
     * @return max friend count
     */
    public int getMaxFriends() { return maxFriends; }

    /**
     * Gets whether friend online status join/leave is broadcast to all friends.
     *
     * @return true if online status is broadcast
     */
    public boolean isOnlineBroadcast() { return onlineBroadcast; }

    /**
     * Gets the sound played for friend events.
     *
     * @return the notification sound (e.g., "ui.button.click")
     */
    public String getNotificationSound() { return notificationSound; }

    /**
     * Gets a string value from the underlying config.
     *
     * @param key the config key (dot notation)
     * @param defaultValue fallback value if key not found
     * @return the string value
     */
    public String getString(String key, String defaultValue) {
        return configManager.getString(key, defaultValue);
    }

    /**
     * Gets an integer value from the underlying config.
     *
     * @param key the config key
     * @param defaultValue fallback value
     * @return the integer value
     */
    public int getInt(String key, int defaultValue) {
        return configManager.getInt(key, defaultValue);
    }

    /**
     * Gets a boolean value from the underlying config.
     *
     * @param key the config key
     * @param defaultValue fallback value
     * @return the boolean value
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return configManager.getBoolean(key, defaultValue);
    }

    /**
     * Gets a string list from the underlying config.
     *
     * @param key the config key
     * @param defaultValue fallback list
     * @return the string list
     */
    public List<String> getStringList(String key, List<String> defaultValue) {
        return configManager.getStringList(key, defaultValue);
    }

    /**
     * Sets a string value in the config.
     *
     * @param key the config key
     * @param value the string value
     */
    public void setString(String key, String value) {
        configManager.setValue(key, value);
    }

    /**
     * Sets a boolean value in the config.
     *
     * @param key the config key
     * @param value the boolean value
     */
    public void setBoolean(String key, boolean value) {
        configManager.setValue(key, value);
    }

    /**
     * Gets the raw FileConfiguration for advanced access.
     *
     * @return the underlying config
     */
    public co.bukkit.configuration.file.FileConfiguration getConfig() {
        return configManager.getConfig();
    }

    /**
     * Saves the configuration to disk.
     *
     * @return true if save was successful
     */
    public boolean saveConfig() {
        return configManager.saveConfig();
    }
}