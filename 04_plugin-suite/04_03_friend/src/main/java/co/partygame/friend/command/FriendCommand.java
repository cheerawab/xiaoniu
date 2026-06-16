package co.partygame.friend.command;

import co.partygame.common.util.ChatUtils;
import co.partygame.friend.FriendPlugin;
import co.partygame.friend.PartyInvite;
import co.partygame.friend.FriendManager;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * Command executor and tab completer for /friend command.
 *
 * Supports:
 *   /friend                          - Open friend GUI
 *   /friend add <player>             - Send friend request
 *   /friend remove <player>          - Remove friendship
 *   /friend block <player>           - Block player
 *   /friend unblock <player>         - Unblock player
 *   /friend ignore <player>          - Ignore player
 *   /friend unignore <player>        - Stop ignoring player
 *   /friend list                     - Open friend list GUI
 *   /friend invites                  - Open friend requests GUI
 *   /friend invite <player>          - Invite to party
 *   /friend game_invite <player>     - Invite to game
 */
public class FriendCommand implements CommandExecutor, TabCompleter {

    private final FriendPlugin plugin;
    private final FriendManager friendManager;

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "add", "remove", "block", "unblock", "ignore", "unignore",
            "invites", "invite", "game_invite"
    );

    /**
     * Creates a FriendCommand instance.
     *
     * @param plugin the main plugin instance
     */
    public FriendCommand(FriendPlugin plugin) {
        this.plugin = plugin;
        this.friendManager = plugin.getFriendManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("partygame.friend")) {
            sender.sendMessage(ChatUtils.colorize("&cYou don't have permission to use this command."));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatUtils.colorize("&cThis command must be used in-game."));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Open GUI
            plugin.getFriendListGUI().openGUI(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "add":
                return handleAdd(player, args);
            case "remove":
                return handleRemove(player, args);
            case "block":
                return handleBlock(player, args);
            case "unblock":
                return handleUnblock(player, args);
            case "ignore":
                return handleIgnore(player, args);
            case "unignore":
                return handleUnignore(player, args);
            case "invites":
                return handleInvites(player);
            case "invite":
                return handleInvite(player, args);
            case "game_invite":
                return handleGameInvite(player, args);
            default:
                player.sendMessage(ChatUtils.colorize("Unknown subcommand. Try: /friend list"));
                return true;
        }
    }

    private boolean handleAdd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatUtils.colorize("&cUsage: /friend add <player>"));
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatUtils.colorize("&cPlayer not found: " + targetName));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(ChatUtils.colorize("&cYou can't add yourself as a friend!"));
            return true;
        }

        if (friendManager.isFriend(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(ChatUtils.colorize("&cYou are already friends with &e" + target.getName()));
            return true;
        }

        // Send friend request
        friendManager.sendInvite(player.getUniqueId(), target.getUniqueId(),
                player.getName(), null);

        player.sendMessage(ChatUtils.colorize("&aFriend request sent to &e" + target.getName() + "&a!"));

        // Send invite to target if online
        Player targetPlayer = Bukkit.getPlayer(target.getUniqueId());
        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendMessage(ChatUtils.colorize("&e" + player.getName() + " &7sent you a friend request!"));
            targetPlayer.sendMessage(ChatUtils.colorize("&7Use &e/friend invites &7to see pending requests"));
        }

        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatUtils.colorize("&cUsage: /friend remove <player>"));
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatUtils.colorize("&cPlayer not found: " + targetName));
            return true;
        }

        if (!friendManager.isFriend(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(ChatUtils.colorize("&cYou are not friends with &e" + target.getName()));
            return true;
        }

        friendManager.removeFriend(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(ChatUtils.colorize("&cYou removed &e" + target.getName() + "&c as a friend."));

        return true;
    }

    private boolean handleBlock(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatUtils.colorize("&cUsage: /friend block <player>"));
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatUtils.colorize("&cPlayer not found: " + targetName));
            return true;
        }

        friendManager.blockPlayer(player.getUniqueId(), target.getUniqueId(), target.getName());
        player.sendMessage(ChatUtils.colorize("&aYou blocked &e" + target.getName() + "&a."));
        player.sendMessage(ChatUtils.colorize("&cYou can no longer see messages from &e" + target.getName() + "&c."));

        return true;
    }

    private boolean handleUnblock(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatUtils.colorize("&cUsage: /friend unblock <player>"));
            return true;
        }

        String targetName = args[1];
        Player target;
        // Handle case where player might not be online
        Player onlineTarget = Bukkit.getPlayer(targetName);
        if (onlineTarget != null) {
            target = onlineTarget;
        } else {
            // Try to find by UUID or allow unblocking non-online players
            player.sendMessage(ChatUtils.colorize("&cPlayer not found: " + targetName));
            return true;
        }

        if (!friendManager.isOnBlockList(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(ChatUtils.colorize("&e" + target.getName() + " &cis not on your block list."));
            return true;
        }

        friendManager.unblockPlayer(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(ChatUtils.colorize("&aYou unblocked &e" + target.getName()));

        return true;
    }

    private boolean handleIgnore(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatUtils.colorize("&cUsage: /friend ignore <player>"));
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatUtils.colorize("&cPlayer not found: " + targetName));
            return true;
        }

        friendManager.ignorePlayer(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(ChatUtils.colorize("&aYou are now ignoring &e" + target.getName()));
        player.sendMessage(ChatUtils.colorize("&cYou will not see their messages."));

        return true;
    }

    private boolean handleUnignore(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatUtils.colorize("&cUsage: /friend unignore <player>"));
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatUtils.colorize("&cPlayer not found: " + targetName));
            return true;
        }

        friendManager.unignorePlayer(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(ChatUtils.colorize("&aYou are no longer ignoring &e" + target.getName()));

        return true;
    }

    private boolean handleInvites(Player player) {
        plugin.openFriendGUI(player);
        return true;
    }

    private boolean handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatUtils.colorize("&cUsage: /friend invite <player>"));
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatUtils.colorize("&cPlayer not found: " + targetName));
            return true;
        }

        if (!friendManager.isFriend(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(ChatUtils.colorize("&cYou are not friends with &e" + target.getName()));
            return true;
        }

        // Use PartyInvite to send party invitation
        PartyInvite.sendPartyInvite(player.getUniqueId(), target.getUniqueId(), "partygame");

        player.sendMessage(ChatUtils.colorize("&aParty invite sent to &e" + target.getName() + "&a!"));

        return true;
    }

    private boolean handleGameInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatUtils.colorize("&cUsage: /friend game_invite <player>"));
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatUtils.colorize("&cPlayer not found: " + targetName));
            return true;
        }

        if (!friendManager.isFriend(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(ChatUtils.colorize("&cYou are not friends with &e" + target.getName()));
            return true;
        }

        // Use PartyInvite to send game invitation through matchmaking
        PartyInvite.sendGameInvite(player.getUniqueId(), target.getUniqueId(),
                "bedwars");

        player.sendMessage(ChatUtils.colorize("&aGame invite sent to &e" + target.getName() + "&a!"));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "add":
                case "remove":
                case "block":
                case "unblock":
                case "ignore":
                case "unignore":
                case "invite":
                    return plugin.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                default:
                    return List.of();
            }
        }

        return List.of();
    }
}
