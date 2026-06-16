package co.partygame.common.util;

import java.util.UUID;
import java.util.Objects;

/**
 * UUID 工具類。
 *
 * 提供 UUID 和 String 之間的轉換，支援 Minecraft 的有橫線 (/) 和無橫線 UUID 格式。
 * 提供短 UUID (8 字符) 的編碼/解碼。
 */
public final class UuidUtils {

    private UuidUtils() {
        throw new UnsupportedOperationException("Utility class - no instantiation");
    }

    /**
     * 將 String 轉換為 UUID，支援有橫線 (/) 和無橫線格式。
     * 例如: "550e8400-e29b-41d4-a716-446655440000" 或 "550e8400e29b41d4a716446655440000"。
     *
     * @param uuidStr UUID 字符串
     * @return UUID 對象
     * @throws IllegalArgumentException 如果格式不合法
     */
    public static UUID toUUID(String uuidStr) {
        if (uuidStr == null || uuidStr.isEmpty()) {
            throw new IllegalArgumentException("UUID string must not be null or empty");
        }
        if (uuidStr.length() == 32) {
            return UUID.fromString(uuidStr.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                "$1-$2-$3-$4-$5"
            ));
        }
        return UUID.fromString(uuidStr);
    }

    /**
     * 將 UUID 轉換為 String 格式。
     *
     * @param uuid UUID 對象
     * @return UUID 字符串 (例如 "550e8400-e29b-41d4-a716-446655440000")
     */
    public static String toString(UUID uuid) {
        return Objects.requireNonNull(uuid, "UUID must not be null").toString();
    }

    /**
     * 將 UUID 轉換為短 UUID (8 字符)。
     * 使用 Base32 編碼的前 8 個字符。
     * 例如: 550e8400e29b41d4 → "aB3dEfGh"
     *
     * @param uuid UUID 對象
     * @return 8 字符短 UUID
     */
    public static String toShortUUID(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID must not be null");
        char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        char c1 = chars[(int) (most >> 58) & 0x1f];
        char c2 = chars[(int) (most >> 53) & 0x1f];
        char c3 = chars[(int) (most >> 48) & 0x1f];
        char c4 = chars[(int) (most >> 43) & 0x1f];
        char c5 = chars[(int) (most >> 38) & 0x1f];
        char c6 = chars[(int) (most >> 33) & 0x1f];
        char c7 = chars[(int) (most >> 28) & 0x1f];
        char c8 = chars[(int) (most >> 23) & 0x1f];
        return new String(new char[]{c1, c2, c3, c4, c5, c6, c7, c8});
    }

    /**
     * 檢查字符串是否為 Minecraft 的 legacy UUID 格式 (3 或 32 字符)。
     * Legacy UUID 為 32 字符無橫線格式。
     *
     * @param uuidStr UUID 字符串
     * @return 如果是 legacy UUID 格式則返回 true
     */
    public static boolean isLegacyUUID(String uuidStr) {
        if (uuidStr == null || uuidStr.isEmpty()) {
            return false;
        }
        int length = uuidStr.length();
        if (length == 32) {
            for (char c : uuidStr.toCharArray()) {
                if (!Character.isHexDigit(c)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 檢查字符串是否為有效的 UUID 格式。
     *
     * @param uuidStr UUID 字符串
     * @return 如果為有效 UUID 格式則返回 true
     */
    public static boolean isValidUUID(String uuidStr) {
        if (uuidStr == null || uuidStr.isEmpty()) {
            return false;
        }
        try {
            toUUID(uuidStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
