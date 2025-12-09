// File: src/main/java/com/zs/service/emotion/cache/EmotionCacheManager.java
package com.zs.service.emotion.cache;

import com.zs.service.emotion.dto.EmotionAnalysisDTO;
import com.zs.service.emotion.dto.UserEmotionSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 情感缓存管理器
 * 负责Redis缓存的所有操作
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmotionCacheManager {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis键前缀
    private static final String KEY_PREFIX = "emotion:user:";
    private static final String CURRENT_KEY = "current";
    private static final String TREND_KEY = "trend";
    private static final String KEYWORD_KEY = "keywords";
    private static final String HISTORY_KEY = "history";

    // 缓存过期时间
    private static final Duration CURRENT_TTL = Duration.ofHours(1);
    private static final Duration TREND_TTL = Duration.ofDays(1);
    private static final Duration KEYWORD_TTL = Duration.ofDays(7);
    private static final Duration HISTORY_TTL = Duration.ofHours(24);

    /**
     * 缓存当前情感状态 - 修正版
     */
    public void cacheCurrentEmotion(Long userId, EmotionAnalysisDTO emotion) {
        try {
            String key = buildKey(userId, CURRENT_KEY);

            UserEmotionSnapshot snapshot = UserEmotionSnapshot.builder()
                    .userId(userId)
                    .primaryEmotion(emotion.getPrimaryEmotion())
                    .intensity(emotion.getIntensity())
                    .confidence(emotion.getConfidence())
                    .keywords(emotion.getEmotionKeywords())
                    .lifeScenario(emotion.getLifeScenario())
                    .timestamp(LocalDateTime.now())
                    .metadata(Map.of(
                            "context", emotion.getConversationContext(),
                            "isMeaningful", emotion.getIsMeaningful(),
                            "source", emotion.getSource()
                    ))
                    .build();

            // ✅ 修改这里：先set值，再设置过期时间
            redisTemplate.opsForValue().set(key, snapshot);
            redisTemplate.expire(key, CURRENT_TTL.toMinutes(), TimeUnit.MINUTES);

            log.debug("缓存当前情感状态: userId={}, emotion={}", userId, emotion.getPrimaryEmotion());

        } catch (Exception e) {
            log.error("缓存当前情感状态失败: userId={}", userId, e);
        }
    }

    /**
     * 更新情感趋势 - 修正版
     */
    public void updateEmotionTrend(Long userId, EmotionAnalysisDTO emotion) {
        try {
            String key = buildKey(userId, TREND_KEY);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trend = (List<Map<String, Object>>) redisTemplate.opsForValue().get(key);

            if (trend == null) {
                trend = new ArrayList<>();
            }

            Map<String, Object> trendPoint = new HashMap<>();
            trendPoint.put("timestamp", System.currentTimeMillis());
            trendPoint.put("emotion", emotion.getPrimaryEmotion());
            trendPoint.put("intensity", emotion.getIntensity());
            trendPoint.put("scenario", emotion.getLifeScenario());
            trendPoint.put("keywords", emotion.getEmotionKeywords());

            trend.add(trendPoint);

            // 只保留最近100个点
            if (trend.size() > 100) {
                trend = trend.subList(trend.size() - 100, trend.size());
            }

            // ✅ 修改这里：先set值，再设置过期时间
            redisTemplate.opsForValue().set(key, trend);
            redisTemplate.expire(key, TREND_TTL.toMinutes(), TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("更新情感趋势失败: userId={}", userId, e);
        }
    }

    /**
     * 更新关键词频率 - 这个方法已经正确，无需修改
     */
    public void updateKeywordFrequency(Long userId, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return;
        }

        try {
            String key = buildKey(userId, KEYWORD_KEY);

            for (String keyword : keywords) {
                redisTemplate.opsForHash().increment(key, keyword, 1);
            }

            // 设置过期时间
            redisTemplate.expire(key, KEYWORD_TTL.toMinutes(), TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("更新关键词频率失败: userId={}", userId, e);
        }
    }

    /**
     * 获取当前情感状态
     */
    public UserEmotionSnapshot getCurrentEmotion(Long userId) {
        try {
            String key = buildKey(userId, CURRENT_KEY);
            return (UserEmotionSnapshot) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("获取当前情感状态失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 获取情感趋势
     */
    public List<Map<String, Object>> getEmotionTrend(Long userId, int limit) {
        try {
            String key = buildKey(userId, TREND_KEY);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trend = (List<Map<String, Object>>) redisTemplate.opsForValue().get(key);

            if (trend == null || trend.isEmpty()) {
                return new ArrayList<>();
            }

            // 返回指定数量的数据
            int start = Math.max(0, trend.size() - limit);
            return new ArrayList<>(trend.subList(start, trend.size()));

        } catch (Exception e) {
            log.error("获取情感趋势失败: userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取关键词频率
     */
    public Map<String, Long> getKeywordFrequency(Long userId) {
        try {
            String key = buildKey(userId, KEYWORD_KEY);

            @SuppressWarnings("unchecked")
            Map<Object, Object> rawMap = redisTemplate.opsForHash().entries(key);

            Map<String, Long> result = new HashMap<>();
            rawMap.forEach((k, v) -> {
                if (k != null && v != null) {
                    result.put(k.toString(), Long.parseLong(v.toString()));
                }
            });

            return result;

        } catch (Exception e) {
            log.error("获取关键词频率失败: userId={}", userId, e);
            return new HashMap<>();
        }
    }

    /**
     * 获取热门关键词
     */
    public List<Map.Entry<String, Long>> getTopKeywords(Long userId, int topN) {
        Map<String, Long> frequency = getKeywordFrequency(userId);

        return frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 清除用户情感缓存
     */
    public void clearUserCache(Long userId) {
        try {
            // 清除所有相关键
            String pattern = KEY_PREFIX + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);

            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("清除用户情感缓存: userId={}, keys={}", userId, keys.size());
            }
        } catch (Exception e) {
            log.error("清除用户缓存失败: userId={}", userId, e);
        }
    }

    /**
     * 记录情感分析历史 - 修正版
     */
    public void recordEmotionHistory(Long userId, EmotionAnalysisDTO emotion) {
        try {
            String key = buildKey(userId, HISTORY_KEY);

            @SuppressWarnings("unchecked")
            List<EmotionAnalysisDTO> history = (List<EmotionAnalysisDTO>) redisTemplate.opsForValue().get(key);

            if (history == null) {
                history = new ArrayList<>();
            }

            history.add(emotion);

            // 只保留最近50条记录
            if (history.size() > 50) {
                history = history.subList(history.size() - 50, history.size());
            }

            // ✅ 修改这里：先set值，再设置过期时间
            redisTemplate.opsForValue().set(key, history);
            redisTemplate.expire(key, HISTORY_TTL.toMinutes(), TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("记录情感历史失败: userId={}", userId, e);
        }
    }

    /**
     * 构建Redis键
     */
    private String buildKey(Long userId, String suffix) {
        return KEY_PREFIX + userId + ":" + suffix;
    }
}