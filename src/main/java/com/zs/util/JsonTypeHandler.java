// File: src/main/java/com/zs/util/JsonTypeHandler.java
package com.zs.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库JSON字段处理工具类
 * 专门处理MySQL JSON字段与Java对象的转换
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JsonTypeHandler {

    private final ObjectMapper objectMapper;

    // ========== List转换为JSON ==========

    /**
     * 将List<String>转换为JSON字符串
     */
    public String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]"; // 空数组
        }

        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("List转JSON失败，使用空数组", e);
            return "[]";
        }
    }

    /**
     * 将List转换为JSON字符串（泛型版本）
     */
    public <T> String genericListToJson(List<T> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }

        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("泛型List转JSON失败，使用空数组", e);
            return "[]";
        }
    }

    /**
     * 将List<Integer>转换为JSON字符串
     */
    public String intListToJson(List<Integer> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }

        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Integer List转JSON失败，使用空数组", e);
            return "[]";
        }
    }

    // ========== Map转换为JSON ==========

    /**
     * 将Map转换为JSON字符串
     */
    public <K, V> String mapToJson(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return "{}"; // 空对象
        }

        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Map转JSON失败，使用空对象", e);
            return "{}";
        }
    }

    // ========== 字符串转换为JSON ==========

    /**
     * 将字符串转换为安全的JSON字符串（如果是JSON则直接返回，如果不是则包装）
     */
    public String stringToSafeJson(String str) {
        if (str == null || str.trim().isEmpty()) {
            return "\"\"";
        }

        // 先检查是否已经是有效的JSON
        if (isValidJson(str)) {
            return str;
        }

        // 尝试解析为JSON字符串（加引号）
        try {
            // 如果字符串看起来像数组的toString()，尝试修复
            if (str.startsWith("[") && str.endsWith("]")) {
                // 尝试解析为List
                List<?> list = objectMapper.readValue(str, List.class);
                return objectMapper.writeValueAsString(list);
            }

            // 否则作为普通字符串处理
            return objectMapper.writeValueAsString(str);
        } catch (Exception e) {
            // 最后手段：返回带引号的原始字符串
            return "\"" + escapeJson(str) + "\"";
        }
    }

    // ========== JSON转换为对象 ==========

    /**
     * 将JSON字符串转换为List<String>
     */
    public List<String> jsonToList(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(jsonStr, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("JSON转List失败: {}", jsonStr, e);
            return new ArrayList<>();
        }
    }

    /**
     * 将JSON字符串转换为List（泛型版本）
     */
    public <T> List<T> jsonToGenericList(String jsonStr, Class<T> elementType) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(jsonStr,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            log.warn("JSON转泛型List失败: {}", jsonStr, e);
            return new ArrayList<>();
        }
    }

    /**
     * 将JSON字符串转换为Map
     */
    public <K, V> Map<K, V> jsonToMap(String jsonStr, Class<K> keyType, Class<V> valueType) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(jsonStr,
                    objectMapper.getTypeFactory().constructMapType(Map.class, keyType, valueType));
        } catch (Exception e) {
            log.warn("JSON转Map失败: {}", jsonStr, e);
            return new HashMap<>();
        }
    }

    /**
     * 将JSON字符串转换为指定类型
     */
    public <T> T jsonToObject(String jsonStr, Class<T> clazz) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(jsonStr, clazz);
        } catch (Exception e) {
            log.warn("JSON转对象失败: {}", jsonStr, e);
            return null;
        }
    }

    // ========== JSON验证和清理 ==========

    /**
     * 验证字符串是否是有效的JSON
     */
    public boolean isValidJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return false;
        }

        String trimmed = jsonStr.trim();

        // 快速检查：JSON必须以{、[、"、数字或true/false/null开头
        if (!(trimmed.startsWith("{") || trimmed.startsWith("[") ||
                trimmed.startsWith("\"") || trimmed.startsWith("-") ||
                Character.isDigit(trimmed.charAt(0)) ||
                trimmed.startsWith("true") || trimmed.startsWith("false") ||
                trimmed.startsWith("null"))) {
            return false;
        }

        try {
            objectMapper.readTree(jsonStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 清理并验证JSON字符串
     * 1. 如果是null或空，返回默认值
     * 2. 如果是有效的JSON，直接返回
     * 3. 如果不是有效的JSON，尝试修复
     */
    public String cleanAndValidateJson(String jsonStr, JsonType type) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return type.defaultValue;
        }

        String trimmed = jsonStr.trim();

        // 如果已经是有效的JSON，直接返回
        if (isValidJson(trimmed)) {
            return trimmed;
        }

        // 尝试修复常见的JSON问题
        try {
            // 移除可能的BOM字符
            trimmed = trimmed.replace("\uFEFF", "");

            // 如果是数组形式的字符串（来自Java的toString()）
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                // 尝试解析为List，然后重新序列化
                List<?> list = objectMapper.readValue(trimmed, List.class);
                return objectMapper.writeValueAsString(list);
            }

            // 如果是对象形式的字符串
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                // 尝试解析为Map，然后重新序列化
                Map<?, ?> map = objectMapper.readValue(trimmed, Map.class);
                return objectMapper.writeValueAsString(map);
            }

            // 否则作为普通字符串处理
            return "\"" + escapeJson(trimmed) + "\"";

        } catch (Exception e) {
            log.warn("清理JSON失败，使用默认值: {}", jsonStr, e);
            return type.defaultValue;
        }
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }

        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ========== 数据库字段专用的方法 ==========

    /**
     * 处理conversations表的emotion_keywords字段
     */
    public String processEmotionKeywords(List<String> keywords) {
        return listToJson(keywords);
    }

    /**
     * 处理conversations表的user_feedback字段
     */
    public String processUserFeedback(Object feedback) {
        if (feedback == null) {
            return "{}";
        }

        if (feedback instanceof String) {
            return stringToSafeJson((String) feedback);
        }

        try {
            return objectMapper.writeValueAsString(feedback);
        } catch (JsonProcessingException e) {
            log.warn("处理user_feedback失败", e);
            return "{}";
        }
    }

    /**
     * 处理emotional_memories表的life_scenario字段
     */
    public String processLifeScenario(Object scenario) {
        if (scenario == null) {
            return "\"general\"";
        }

        if (scenario instanceof String) {
            return "\"" + escapeJson((String) scenario) + "\"";
        }

        try {
            return objectMapper.writeValueAsString(scenario);
        } catch (JsonProcessingException e) {
            log.warn("处理life_scenario失败", e);
            return "\"general\"";
        }
    }

    /**
     * JSON类型枚举
     */
    public enum JsonType {
        ARRAY("[]"),
        OBJECT("{}"),
        STRING("\"\""),
        NUMBER("0"),
        BOOLEAN("false"),
        NULL("null");

        public final String defaultValue;

        JsonType(String defaultValue) {
            this.defaultValue = defaultValue;
        }
    }
}