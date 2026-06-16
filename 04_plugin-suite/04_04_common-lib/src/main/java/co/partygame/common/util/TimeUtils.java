package co.partygame.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 時間格式化工具類。
 *
 * 提供時間單位轉換和格式化方法。
 * 支持毫秒、秒、格式化時間戳等。
 */
public final class TimeUtils {

    private TimeUtils() {
        throw new UnsupportedOperationException("Utility class - no instantiation");
    }

    /**
     * 格式化毫秒為人類可讀的時間字符串。
     * 格式: "1h 23m 45s" 或 "23s" 或 "5m 6s"。
     *
     * @param millis 毫秒數
     * @return 格式化後的時間字符串
     */
    public static String formatMillis(long millis) {
        return formatSeconds((int) (Math.max(0, millis) / 1000));
    }

    /**
     * 格式化秒為人類可讀的時間字符串。
     * 格式: "1h 23m 45s" 或 "23s" 或 "5m 6s"。
     *
     * @param seconds 秒數
     * @return 格式化後的時間字符串
     */
    public static String formatSeconds(int seconds) {
        seconds = Math.max(0, seconds);
        if (seconds == 0) return "0s";
        if (seconds < 60) return seconds + "s";

        int hours = seconds / 3600;
        int remainder = seconds % 3600;
        int minutes = remainder / 60;
        int secs = remainder % 60;

        if (hours > 0) {
            if (minutes > 0) {
                return hours + "h " + minutes + "m " + secs + "s";
            }
            return hours + "h " + secs + "s";
        }
        return minutes + "m " + secs + "s";
    }

    /**
     * 格式化時間戳為人類可讀日期。
     * 格式: "Jan 1, 2024"。
     *
     * @param timestamp 時間戳 (毫秒)
     * @return 格式化日期字符串
     */
    public static String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "Never";
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
            return formatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * 將時間字符串解析為毫秒數。
     * 支持的單位: d (天), h (時), m (分), s (秒)。
     * 格式: "2h 30m 15s" 或 "2h" 或 "1d 12h"。
     *
     * @param timeStr 時間字符串
     * @return 對應的毫秒數
     */
    public static long toMillis(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 0;
        long totalMillis = 0;
        String[] parts = timeStr.trim().split("\\s+");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            try {
                long value = Long.parseLong(part.substring(0, part.length() - 1));
                char unit = Character.toLowerCase(part.charAt(part.length() - 1));
                long ms;
                switch (unit) {
                    case 'd': ms = value * 86400000L; break;
                    case 'h': ms = value * 3600000L; break;
                    case 'm': ms = value * 60000L; break;
                    case 's': ms = value * 1000L; break;
                    default:  ms = value * 1000L; break;
                }
                totalMillis += ms;
            } catch (Exception ignored) {
                // Skip invalid parts
            }
        }
        return totalMillis;
    }

    /**
     * 判斷時間是否已過期。
     *
     * @param timestamp 時間戳 (毫秒)
     * @param timeout   超時時間 (毫秒)
     * @return 如果已過期返回 true
     */
    public static boolean isExpired(long timestamp, long timeout) {
        return System.currentTimeMillis() - timestamp > timeout;
    }

    /**
     * 獲取當前時間戳 (毫秒)。
     *
     * @return 毫秒時間戳
     */
    public static long now() {
        return System.currentTimeMillis();
    }

    /**
     * 計算兩個時間戳之間的時間差 (秒)。
     *
     * @param start 開始時間 (毫秒)
     * @param end   結束時間 (毫秒)
     * @return 時間差 (秒), 如果 end < start 則返回 0
     */
    public static long diffSeconds(long start, long end) {
        return Math.max(0, (end - start) / 1000);
    }
}
