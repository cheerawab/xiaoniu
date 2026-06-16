package co.partygame.common.util;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 隨機數工具類。
 *
 * 提供隨機數、隨機選擇、隨機洗牌等工具。
 * 基於 `java.util.concurrent.ThreadLocalRandom` 實現線程安全的隨機操作。
 */
public final class RandomUtils {

    private RandomUtils() {
        throw new UnsupportedOperationException("Utility class - no instantiation");
    }

    /**
     * 獲取 [min, max] 範圍內的安全隨機整數。
     *
     * @param min 最小值 (包含)
     * @param max 最大值 (包含)
     * @return 隨機整數
     * @throws IllegalArgumentException 如果 min > max
     */
    public static int nextInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min cannot be greater than max");
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * 獲取 [min, max] 範圍內的安全隨機雙精度浮點數。
     *
     * @param min 最小值 (包含)
     * @param max 最大值 (不包含)
     * @return 隨機雙精度浮點數
     * @throws IllegalArgumentException 如果 min > max
     */
    public static double nextDouble(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min cannot be greater than max");
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    /**
     * 獲取 [0, max) 範圍內的隨機整數。
     *
     * @param max 最大值 (不包含)
     * @return 隨機整數
     * @throws IllegalArgumentException 如果 max <= 0
     */
    public static int nextInt(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("max must be > 0");
        }
        return ThreadLocalRandom.current().nextInt(max);
    }

    /**
     * 獲取 [0, 1) 範圍內的隨機雙精度浮點數。
     *
     * @return 隨機雙精度浮點數
     */
    public static double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /**
     * 從列表中隨機選擇一個元素。
     *
     * @param list 要選擇的列表
     * @param <T>  元素類型
     * @return 隨機元素
     * @throws NoSuchElementException 如果列表為空
     */
    public static <T> T randomElement(List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new NoSuchElementException("List must not be null or empty");
        }
        int index = ThreadLocalRandom.current().nextInt(list.size());
        return list.get(index);
    }

    /**
     * 從數組中隨機選擇一個元素。
     *
     * @param array 要選擇的數組
     * @param <T>    元素類型
     * @return 隨機元素
     * @throws NoSuchElementException 如果數組為空
     */
    public static <T> T randomElement(T[] array) {
        if (array == null || array.length == 0) {
            throw new NoSuchElementException("Array must not be null or empty");
        }
        int index = ThreadLocalRandom.current().nextInt(array.length);
        return array[index];
    }

    /**
     * 隨機洗牌列表 (Fisher-Yates 洗牌演算法)。
     *
     * @param list 要打亂的列表 (原地修改)
     * @param <T>  元素類型
     */
    public static <T> void shuffle(List<T> list) {
        if (list == null || list.size() <= 1) return;
        for (int i = list.size() - 1; i > 0; i--) {
            int index = ThreadLocalRandom.current().nextInt(i + 1);
            T temp = list.get(index);
            list.set(index, list.get(i));
            list.set(i, temp);
        }
    }

    /**
     * 隨機選擇 Set 中的一個元素。
     *
     * @param set 要選擇的 Set
     * @return 隨機元素
     * @throws NoSuchElementException 如果 Set 為空
     */
    public static <T> T pickRandom(Set<T> set) {
        if (set == null || set.isEmpty()) {
            throw new NoSuchElementException("Set must not be null or empty");
        }
        int index = ThreadLocalRandom.current().nextInt(set.size());
        int i = 0;
        for (T element : set) {
            if (i == index) return element;
            i++;
        }
        return null;
    }

    /**
     * 生成隨機的 8 字符 ID。
     * 使用隨機字符: A-Z, a-z, 0-9。
     *
     * @return 隨機 ID
     */
    public static String generateId() {
        char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
        char[] id = new char[8];
        for (int i = 0; i < 8; i++) {
            id[i] = chars[ThreadLocalRandom.current().nextInt(chars.length)];
        }
        return new String(id);
    }

    /**
     * 生成隨機的 UUID (字符串格式)。
     *
     * @return UUID 字符串
     */
    public static String randomUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 隨機選擇一個布爾值 (50/50).
     *
     * @return 隨機布爾值
     */
    public static boolean nextBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    /**
     * 生成 [1, length] 範圍內的安全隨機 ID。
     *
     * @param length
     * @return 隨機 ID
     * @throws IllegalArgumentException 如果 length <= 0
     */
    public static String generateId(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be > 0");
        }
        char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
        char[] id = new char[length];
        for (int i = 0; i < length; i++) {
            id[i] = chars[ThreadLocalRandom.current().nextInt(chars.length)];
        }
        return new String(id);
    }

    /**
     * 生成 [min, max] 範圍內的安全隨機長整數。
     *
     * @param min 最小值 (包含)
     * @param max 最大值 (包含)
     * @return 隨機長整數
     * @throws IllegalArgumentException 如果 min > max
     */
    public static long nextLong(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("min cannot be greater than max");
        }
        long range = max - min + 1;
        return min + ThreadLocalRandom.current().nextLong(range);
    }

    /**
     * 生成 [0, bound) 範圍內的隨機長整數。
     *
     * @param bound 上界 (不包含)
     * @return 隨機長整數
     * @throws IllegalArgumentException 如果 bound <= 0
     */
    public static long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be > 0");
        }
        return ThreadLocalRandom.current().nextLong(bound);
    }
}
