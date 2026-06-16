package co.partygame.common.util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Color;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 聊天消息和顏色工具類。
 *
 * 提供 ChatColor 顏色代碼和 Hex 顏色支持。
 * 支持 `&` 顏色代碼和 `&#RRGGBB` 十六進制顏色格式。
 * 提供消息發送和廣播方法。
 */
public final class ChatUtils {

    private static final Logger LOGGER = Logger.getLogger(ChatUtils.class.getName());

    // 匹配 &#RRGGBB 格式的十六進制顏色
    private static final Pattern HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");

    private ChatUtils() {
        throw new UnsupportedOperationException("Utility class - no instantiation");
    }

    /**
     * 解析 `&` 顏色代碼為 ChatColor 顏色。
     * 支持所有 Minecraft 顏色代碼: &0-&9, &a-&f, &k-&r, &l, &m, &n, &o, &r 等。
     *
     * @param text 包含 & 顏色代碼的文本
     * @return 翻譯後的文本
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // First translate hex colors before processing & codes
        text = translateHex(text);
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * 解析 `&#RRGGBB` 格式的十六進制顏色為 ChatColor 顏色。
     * 例如: "&#FF0000" → 紅色。
     *
     * @param text 包含 &#RRGGBB 格式的文本
     * @return 翻譯後的文本
     */
    public static String translateHex(String text) {
        if (text == null || !text.contains("&#")) {
            return text;
        }
        try {
            Matcher matcher = HEX_PATTERN.matcher(text);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String hexCode = matcher.group(1);
                int color = Integer.parseInt(hexCode, 16);
                Color colorObj = Color.fromRGB(color);
                matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of(colorObj).toString());
            }
            matcher.appendTail(buffer);
            return buffer.toString();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to translate hex color", e);
            return text;
        }
    }

    /**
     * 給指定玩家發送消息 (使用 ChatColor 顏色).
     *
     * @param player 接收消息的玩家
     * @param message 消息內容 (支持 & 顏色代碼)
     */
    public static void msg(Player player, String message) {
        if (player == null || message == null) return;
        if (message.isEmpty()) return;
        for (String line : message.split("\\n")) {
            player.sendMessage(colorize(line));
        }
    }

    /**
     * 給所有線上玩家發送消息 (使用 ChatColor 顏色).
     *
     * @param message 消息內容 (支持 & 顏色代碼)
     */
    public static void msgAll(String message) {
        if (message == null) return;
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            msg(player, message);
        }
    }

    /**
     * 向所有玩家广播消息 (使用 ChatColor 顏色).
     * 使用 `&e[Server]&r &7` 格式前綴
     *
     * @param message 消息內容 (支持 & 顏色代碼)
     */
    public static void broadcast(String message) {
        if (message == null) return;
        org.bukkit.Bukkit.broadcastMessage(colorize(message));
    }

    /**
     * 給所有玩家发送消息 (使用 ChatColor 顏色).
     *
     * @param message 消息內容 (支持 & 顏色代碼)
     */
    public static void sendToAll(String message) {
        if (message == null) return;
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            msg(player, message);
        }
    }

    /**
     * 給指定 CommandSender 發送消息 (使用 ChatColor 顏色).
     *
     * @param sender 消息接收者
     * @param message 消息内容 (支持 & 顏色代碼)
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null) return;
        for (String line : message.split("\\n")) {
            sender.sendMessage(colorize(line));
        }
    }

    /**
     * 檢查字符串是否包含顏色代碼。
     *
     * @param text 文本
     * @return 如果包含顏色代碼返回 true
     */
    public static boolean hasColorCodes(String text) {
        return text != null && text.contains("&");
    }

    /**
    * 清理 ChatColor 顏色代碼, 返回純文本。
    *
    * @param text 包含顏色代碼的文本
    * @return 清理後的純文本
    */
    public static String stripColor(String text) {
        if (text == null) return null;
        return net.md_5.bungee.api.ChatColor.stripColor(text);
    }
}
