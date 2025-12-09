// File: src/main/java/com/zs/service/emotion/state/EmotionStateTracker.java
package com.zs.service.emotion.state;

import com.zs.service.emotion.dto.EmotionAnalysisDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户心情状态追踪器
 * 重点：心情是动态变化的，需要有状态追踪
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmotionStateTracker {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis键前缀
    private static final String STATE_PREFIX = "emotion:state:user:";
    private static final String HISTORY_PREFIX = "emotion:history:user:";

    // 状态保留时间
    private static final Duration STATE_TTL = Duration.ofHours(6);

    /**
     * 更新用户当前心情状态
     */
    public void updateEmotionState(Long userId, EmotionAnalysisDTO emotion) {
        try {
            String stateKey = STATE_PREFIX + userId;

            Map<String, Object> state = new HashMap<>();
            state.put("currentEmotion", emotion.getPrimaryEmotion());
            state.put("intensity", emotion.getIntensity());
            state.put("confidence", emotion.getConfidence());
            state.put("scenario", emotion.getLifeScenario());
            state.put("keywords", emotion.getEmotionKeywords());
            state.put("timestamp", System.currentTimeMillis());
            state.put("moodScore", calculateMoodScore(emotion));
            state.put("trend", detectEmotionTrend(userId, emotion));

            // 保存状态
            redisTemplate.opsForHash().putAll(stateKey, state);
            redisTemplate.expire(stateKey, STATE_TTL.toMinutes(), TimeUnit.MINUTES);

            // 记录历史
            recordEmotionHistory(userId, emotion);

            log.debug("更新心情状态: userId={}, emotion={}, score={}",
                    userId, emotion.getPrimaryEmotion(), state.get("moodScore"));

        } catch (Exception e) {
            log.error("更新心情状态失败: userId={}", userId, e);
        }
    }

    /**
     * 获取用户当前心情状态
     */
    public Map<String, Object> getCurrentEmotionState(Long userId) {
        try {
            String stateKey = STATE_PREFIX + userId;

            Map<Object, Object> rawState = redisTemplate.opsForHash().entries(stateKey);
            if (rawState.isEmpty()) {
                return createDefaultState(userId);
            }

            // 转换为Map<String, Object>
            Map<String, Object> state = new HashMap<>();
            rawState.forEach((k, v) -> state.put(k.toString(), v));

            return state;

        } catch (Exception e) {
            log.error("获取心情状态失败: userId={}", userId, e);
            return createDefaultState(userId);
        }
    }

    /**
     * 计算心情分数（0-100）
     */
    private double calculateMoodScore(EmotionAnalysisDTO emotion) {
        double baseScore = 50.0; // 中性基准分

        // 根据情感类型调整分数
        switch (emotion.getPrimaryEmotion()) {
            case "HAPPY":
                baseScore += 40 * emotion.getIntensity(); // +0到+40分
                break;
            case "SAD":
                baseScore -= 30 * emotion.getIntensity(); // -0到-30分
                break;
            case "ANXIOUS":
                baseScore -= 20 * emotion.getIntensity(); // -0到-20分
                break;
            case "ANGRY":
                baseScore -= 25 * emotion.getIntensity(); // -0到-25分
                break;
            case "CALM":
                baseScore += 10; // 平静+10分
                break;
        }

        return Math.max(0, Math.min(100, baseScore));
    }

    /**
     * 检测情感趋势
     */
    private String detectEmotionTrend(Long userId, EmotionAnalysisDTO currentEmotion) {
        try {
            String historyKey = HISTORY_PREFIX + userId;

            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> history =
                    (java.util.List<Map<String, Object>>) redisTemplate.opsForValue().get(historyKey);

            if (history == null || history.size() < 2) {
                return "stable"; // 稳定
            }

            // 获取最近两次情感
            Map<String, Object> previous = history.get(history.size() - 2);
            String previousEmotion = (String) previous.get("emotion");
            double previousIntensity = (double) previous.get("intensity");

            // 分析趋势
            if (!currentEmotion.getPrimaryEmotion().equals(previousEmotion)) {
                return "changing"; // 正在变化
            }

            double intensityDiff = currentEmotion.getIntensity() - previousIntensity;
            if (Math.abs(intensityDiff) > 0.3) {
                return intensityDiff > 0 ? "intensifying" : "fading";
            }

            return "stable";

        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 记录情感历史
     */
    private void recordEmotionHistory(Long userId, EmotionAnalysisDTO emotion) {
        try {
            String historyKey = HISTORY_PREFIX + userId;

            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> history =
                    (java.util.List<Map<String, Object>>) redisTemplate.opsForValue().get(historyKey);

            if (history == null) {
                history = new java.util.ArrayList<>();
            }

            Map<String, Object> record = new HashMap<>();
            record.put("emotion", emotion.getPrimaryEmotion());
            record.put("intensity", emotion.getIntensity());
            record.put("scenario", emotion.getLifeScenario());
            record.put("timestamp", LocalDateTime.now());

            history.add(record);

            // 只保留最近100条记录
            if (history.size() > 100) {
                history = history.subList(history.size() - 100, history.size());
            }

            redisTemplate.opsForValue().set(historyKey, history);
            redisTemplate.expire(historyKey, Duration.ofDays(7).toMinutes(), TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("记录情感历史失败: userId={}", userId, e);
        }
    }

    /**
     * 创建默认状态
     */
    private Map<String, Object> createDefaultState(Long userId) {
        Map<String, Object> defaultState = new HashMap<>();
        defaultState.put("currentEmotion", "NEUTRAL");
        defaultState.put("intensity", 0.5);
        defaultState.put("confidence", 0.5);
        defaultState.put("scenario", "general");
        defaultState.put("keywords", new java.util.ArrayList<>());
        defaultState.put("timestamp", System.currentTimeMillis());
        defaultState.put("moodScore", 50.0);
        defaultState.put("trend", "stable");
        defaultState.put("message", "心情状态待更新");

        return defaultState;
    }

    /**
     * 获取心情报告
     */
    public Map<String, Object> getMoodReport(Long userId) {
        Map<String, Object> state = getCurrentEmotionState(userId);
        Map<String, Object> report = new HashMap<>();

        double moodScore = (double) state.get("moodScore");
        String emotion = (String) state.get("currentEmotion");
        String trend = (String) state.get("trend");

        // 心情等级
        String moodLevel;
        if (moodScore >= 80) {
            moodLevel = "非常愉快";
        } else if (moodScore >= 60) {
            moodLevel = "心情不错";
        } else if (moodScore >= 40) {
            moodLevel = "比较平静";
        } else if (moodScore >= 20) {
            moodLevel = "有些低落";
        } else {
            moodLevel = "情绪不佳";
        }

        // 趋势描述
        String trendDescription;
        switch (trend) {
            case "intensifying": trendDescription = "情绪正在加深"; break;
            case "fading": trendDescription = "情绪正在缓解"; break;
            case "changing": trendDescription = "情绪正在变化"; break;
            case "stable": trendDescription = "情绪相对稳定"; break;
            default: trendDescription = "情绪状态正常";
        }

        report.put("userId", userId);
        report.put("moodScore", moodScore);
        report.put("moodLevel", moodLevel);
        report.put("primaryEmotion", emotion);
        report.put("emotionIntensity", state.get("intensity"));
        report.put("trend", trend);
        report.put("trendDescription", trendDescription);
        report.put("lastUpdate", state.get("timestamp"));
        report.put("suggestion", generateSuggestion(emotion, moodScore));

        return report;
    }

    /**
     * 生成心情建议
     */
    private String generateSuggestion(String emotion, double moodScore) {
        if (moodScore >= 70) {
            return "保持好心情，继续保持哦！";
        } else if (moodScore >= 50) {
            return "情绪状态不错，可以做一些喜欢的事情~";
        } else if (moodScore >= 30) {
            return "情绪有些低落，要不要听听音乐放松一下？";
        } else {
            return "心情不太好，需要聊聊吗？我随时都在~";
        }
    }
}