// File: src/main/java/com/zs/service/memory/cache/MemoryCacheManager.java
package com.zs.service.memory.cache;

import com.zs.entity.MemoryFragments;
import com.zs.service.memory.MemoryExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 记忆缓存管理器 - 类似EmotionCacheManager的设计
 * 使用Spring AI Alibaba的RedisTemplate
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemoryCacheManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MemoryExtractionService memoryExtractionService;

    // Redis键前缀
    private static final String KEY_PREFIX = "memory:user:";
    private static final String ACTIVE_KEY = "active";      // 活跃记忆（最近访问）
    private static final String CONTEXT_KEY = "context";    // 上下文相关记忆
    private static final String IMPORTANT_KEY = "important"; // 重要记忆
    private static final String STATS_KEY = "stats";        // 统计信息

    // 缓存过期时间
    private static final Duration ACTIVE_TTL = Duration.ofHours(2);
    private static final Duration CONTEXT_TTL = Duration.ofMinutes(30);
    private static final Duration IMPORTANT_TTL = Duration.ofDays(7);

    // 记忆限制
    private static final int MAX_ACTIVE_MEMORIES = 20;      // 活跃记忆最大数量
    private static final int MAX_IMPORTANT_MEMORIES = 10;   // 重要记忆最大数量

    /**
     * 获取上下文相关记忆（用于当前对话）
     */
    public List<MemoryFragments> getContextMemories(Long userId, String currentMessage) {
        try {
            String key = buildKey(userId, CONTEXT_KEY);

            // 先查缓存
            @SuppressWarnings("unchecked")
            List<MemoryFragments> cached = (List<MemoryFragments>) redisTemplate.opsForValue().get(key);

            if (cached != null && !cached.isEmpty()) {
                log.debug("从缓存获取上下文记忆: userId={}, count={}", userId, cached.size());
                return cached;
            }

            // 缓存没有，从数据库检索
            List<MemoryFragments> relevant = findRelevantMemories(userId, currentMessage);

            // 缓存结果
            if (!relevant.isEmpty()) {
                redisTemplate.opsForValue().set(key, relevant);
                redisTemplate.expire(key, CONTEXT_TTL.toMinutes(), TimeUnit.MINUTES);
            }

            return relevant;

        } catch (Exception e) {
            log.error("获取上下文记忆失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 查找相关记忆
     */
    private List<MemoryFragments> findRelevantMemories(Long userId, String currentMessage) {
        try {
            // 提取关键词
            List<String> keywords = extractKeywords(currentMessage);

            if (keywords.isEmpty()) {
                return Collections.emptyList();
            }

            // 从数据库搜索相关记忆
            List<MemoryFragments> allMemories = memoryExtractionService.getUserMemories(userId, null, 50);

            // 简单相关性评分
            List<MemoryFragments> relevant = allMemories.stream()
                    .filter(memory -> calculateRelevance(memory, keywords) > 0.3)
                    .sorted(Comparator.comparingDouble((MemoryFragments memory) -> calculateRelevance(memory, keywords)).reversed())

                    .limit(5)
                    .collect(Collectors.toList());

            return relevant;

        } catch (Exception e) {
            log.error("查找相关记忆失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 计算记忆相关性
     */
    private double calculateRelevance(MemoryFragments memory, List<String> keywords) {
        double relevance = 0.0;
        String memoryText = memory.getMemoryText() != null ? memory.getMemoryText().toLowerCase() : "";

        for (String keyword : keywords) {
            if (memoryText.contains(keyword.toLowerCase())) {
                relevance += 0.3;
            }
        }

        // 考虑重要性分数
        if (memory.getImportanceScore() != null) {
            relevance += memory.getImportanceScore().doubleValue() * 0.5;
        }

        // 考虑访问次数
        if (memory.getAccessCount() != null) {
            relevance += Math.min(memory.getAccessCount() * 0.1, 0.2);
        }

        return Math.min(relevance, 1.0);
    }

    /**
     * 提取关键词（简化版）
     */
    private List<String> extractKeywords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 简单分词，实际可以用更复杂的分词算法
        String[] words = text.split("[\\s,.，。!！?？、]+");
        return Arrays.stream(words)
                .filter(word -> word.length() > 1 && word.length() < 6)
                .collect(Collectors.toList());
    }

    /**
     * 记录记忆访问
     */
    public void recordMemoryAccess(Long userId, Long memoryId, String context) {
        try {
            String key = buildKey(userId, STATS_KEY);

            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) redisTemplate.opsForValue().get(key);

            if (stats == null) {
                stats = new HashMap<>();
            }

            // 记录访问时间
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> accesses = (List<Map<String, Object>>) stats.getOrDefault("accesses", new ArrayList<>());

            Map<String, Object> access = new HashMap<>();
            access.put("memoryId", memoryId);
            access.put("timestamp", System.currentTimeMillis());
            access.put("context", context);

            accesses.add(access);

            // 只保留最近100条记录
            if (accesses.size() > 100) {
                accesses = accesses.subList(accesses.size() - 100, accesses.size());
            }

            stats.put("accesses", accesses);
            stats.put("lastUpdated", System.currentTimeMillis());

            redisTemplate.opsForValue().set(key, stats);
            redisTemplate.expire(key, Duration.ofDays(1).toMinutes(), TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("记录记忆访问失败: userId={}, memoryId={}", userId, memoryId, e);
        }
    }

    /**
     * 清除用户记忆缓存
     */
    public void clearUserCache(Long userId) {
        try {
            String pattern = KEY_PREFIX + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);

            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("清除用户记忆缓存: userId={}, keys={}", userId, keys.size());
            }
        } catch (Exception e) {
            log.error("清除用户记忆缓存失败: userId={}", userId, e);
        }
    }

    /**
     * 构建Redis键
     */
    private String buildKey(Long userId, String suffix) {
        return KEY_PREFIX + userId + ":" + suffix;
    }
}