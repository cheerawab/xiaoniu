package co.partygame.common.util;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 工具類。
 *
 * 使用 Jackson ObjectMapper 進行 JSON 序列化/反序列化。
 * 支援 Map, List, 以及泛型類型。
 * 提供 prettyPrint 方法用於格式化輸出。
 *
 * 使用示例：
 * <pre>{@code
 * JsonUtils.toJson(map);            // Map -> JSON 字符串
 * JsonUtils.fromJson(json, Foo.class); // JSON -> Foo 對象
 * JsonUtils.parseMap(json);         // JSON 字符串 -> Map
 * JsonUtils.prettyPrint(json);      // 格式化 JSON 字符串
 * }</pre>
 */
public final class JsonUtils {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() { };
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<List<String>>() { };

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private JsonUtils() {
        throw new UnsupportedOperationException("Utility class - no instantiation");
    }

    /**
     * 將對象序列化為 JSON 字符串。
     *
     * @param object 要序列化的對象
     * @return JSON 字符串
     * @throws RuntimeException 如果序列化失敗
     */
    public static String toJson(Object object) {
        if (object == null) return "null";
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * 將 JSON 字符串反序列化為指定類型的對象。
     *
     * @param json JSON 字符串
     * @param clazz 目標類型的 Class 對象
     * @param <T>   目標類型
     * @return 反序列化後的對象
     * @throws RuntimeException 如果反序列化失敗
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) return null;
        try {
            return mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON to " + clazz.getName(), e);
        }
    }

    /**
     * 將 JSON 字符串解析為 Map<String, Object>。
     *適合解析任意 JSON 對象結構。
     *
     * @param json JSON 字符串
     * @return Map 對象
     * @throws RuntimeException 如果解析失敗
     */
    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return mapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON: " + json, e);
        }
    }

    /**
     * 將 JSON 數組字符串解析為 List<String>。
     *
     * @param json JSON 數組字符串
     * @return List 對象
     * @throws RuntimeException 如果解析失敗
     */
    public static List<String> parseStringList(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return mapper.readValue(json, LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON list: " + json, e);
        }
    }

    /**
     * 格式化 JSON 字符串 (美化輸出)。
     *
     * @param json 原始 JSON 字符串
     * @return 格式化後的 JSON 字符串
     * @throws RuntimeException 如果格式化失敗
     */
    public static String prettyPrint(String json) {
        if (json == null || json.isEmpty()) return json;
        try {
            Object obj = mapper.readValue(json, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}
