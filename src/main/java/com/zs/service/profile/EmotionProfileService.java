// File: src/main/java/com/zs/service/profile/EmotionProfileService.java
package com.zs.service.profile;

import com.zs.service.emotion.dto.EmotionAnalysisDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 用户情感画像服务 - 基于user_emotion_profile表设计
 * 让AI"越来越懂你"的核心
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmotionProfileService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis键前缀
    private static final String PROFILE_PREFIX = "emotion:profile:user:";

    /**
     * 更新用户情感画像
     */
    public void updateEmotionProfile(Long userId, EmotionAnalysisDTO emotion) {
        try {
            String profileKey = PROFILE_PREFIX + userId;

            // 获取现有画像
            Map<String, Object> profile = getEmotionProfile(userId);

            // 更新情感统计
            updateEmotionStats(profile, emotion);

            // 更新话题偏好
            updateTopicPreferences(profile, emotion);

            // 更新行为模式
            updateBehaviorPatterns(profile, emotion);

            // 计算情感理解度
            updateUnderstandingScore(profile);

            // 保存画像
            saveProfile(profileKey, profile);

            log.debug("更新情感画像: userId={}, emotion={}", userId, emotion.getPrimaryEmotion());

        } catch (Exception e) {
            log.error("更新情感画像失败: userId={}", userId, e);
        }
    }

    /**
     * 获取用户情感画像
     */
    public Map<String, Object> getEmotionProfile(Long userId) {
        try {
            String profileKey = PROFILE_PREFIX + userId;

            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) redisTemplate.opsForValue().get(profileKey);

            if (profile == null) {
                return createInitialProfile(userId);
            }

            return profile;

        } catch (Exception e) {
            log.error("获取情感画像失败: userId={}", userId, e);
            return createInitialProfile(userId);
        }
    }

    /**
     * 创建初始画像
     */
    private Map<String, Object> createInitialProfile(Long userId) {
        Map<String, Object> profile = new HashMap<>();

        // 基础信息
        profile.put("userId", userId);
        profile.put("createdDate", LocalDate.now().toString());
        profile.put("lastUpdated", System.currentTimeMillis());

        // 情感统计
        Map<String, Integer> emotionStats = new HashMap<>();
        emotionStats.put("HAPPY", 0);
        emotionStats.put("SAD", 0);
        emotionStats.put("ANXIOUS", 0);
        emotionStats.put("ANGRY", 0);
        emotionStats.put("NEUTRAL", 0);
        profile.put("emotionStats", emotionStats);

        // 话题偏好
        Map<String, Integer> topicPreferences = new HashMap<>();
        topicPreferences.put("study", 0);
        topicPreferences.put("life", 0);
        topicPreferences.put("emotion", 0);
        topicPreferences.put("entertainment", 0);
        profile.put("topicPreferences", topicPreferences);

        // 行为模式
        Map<String, Object> behaviorPatterns = new HashMap<>();
        behaviorPatterns.put("activeTimes", new ArrayList<>());
        behaviorPatterns.put("responseStyle", "gentle");
        behaviorPatterns.put("conversationLengthAvg", 0);
        profile.put("behaviorPatterns", behaviorPatterns);

        // 理解度评分
        profile.put("emotionalUnderstandingScore", 0.5);

        // 理解层次
        Map<String, Double> understandingLevels = new HashMap<>();
        understandingLevels.put("basic_facts", 0.5);
        understandingLevels.put("emotional_patterns", 0.3);
        understandingLevels.put("deep_motivations", 0.1);
        understandingLevels.put("unspoken_needs", 0.0);
        understandingLevels.put("future_aspirations", 0.0);
        profile.put("understandingLevels", understandingLevels);

        // 已解锁洞察
        profile.put("unlockedInsights", new ArrayList<>());

        return profile;
    }

    /**
     * 更新情感统计
     */
    private void updateEmotionStats(Map<String, Object> profile, EmotionAnalysisDTO emotion) {
        @SuppressWarnings("unchecked")
        Map<String, Integer> emotionStats = (Map<String, Integer>) profile.get("emotionStats");

        String primaryEmotion = emotion.getPrimaryEmotion();
        emotionStats.put(primaryEmotion, emotionStats.getOrDefault(primaryEmotion, 0) + 1);

        // 如果是强烈情感，额外计数
        if (emotion.getIntensity() > 0.7) {
            String strongKey = "STRONG_" + primaryEmotion;
            emotionStats.put(strongKey, emotionStats.getOrDefault(strongKey, 0) + 1);
        }
    }

    /**
     * 更新话题偏好
     */
    private void updateTopicPreferences(Map<String, Object> profile, EmotionAnalysisDTO emotion) {
        @SuppressWarnings("unchecked")
        Map<String, Integer> topicPreferences = (Map<String, Integer>) profile.get("topicPreferences");

        String context = emotion.getConversationContext();
        if (context.contains("study") || context.contains("exam") || context.contains("学习")) {
            topicPreferences.put("study", topicPreferences.getOrDefault("study", 0) + 1);
        } else if (context.contains("emotion") || context.contains("心情") || context.contains("感觉")) {
            topicPreferences.put("emotion", topicPreferences.getOrDefault("emotion", 0) + 1);
        } else if (context.contains("entertainment") || context.contains("娱乐") || context.contains("游戏")) {
            topicPreferences.put("entertainment", topicPreferences.getOrDefault("entertainment", 0) + 1);
        } else {
            topicPreferences.put("life", topicPreferences.getOrDefault("life", 0) + 1);
        }
    }

    /**
     * 更新行为模式
     */
    private void updateBehaviorPatterns(Map<String, Object> profile, EmotionAnalysisDTO emotion) {
        @SuppressWarnings("unchecked")
        Map<String, Object> behaviorPatterns = (Map<String, Object>) profile.get("behaviorPatterns");

        // 记录活跃时间
        @SuppressWarnings("unchecked")
        List<String> activeTimes = (List<String>) behaviorPatterns.get("activeTimes");
        String currentHour = String.format("%02d:00", java.time.LocalTime.now().getHour());

        if (!activeTimes.contains(currentHour)) {
            activeTimes.add(currentHour);
            if (activeTimes.size() > 10) {
                activeTimes.remove(0);
            }
        }

        // 更新对话长度
        int currentLength = emotion.getUserMessage() != null ? emotion.getUserMessage().length() : 0;
        int avgLength = (int) behaviorPatterns.getOrDefault("conversationLengthAvg", 0);

        // 移动平均
        int newAvg = (avgLength * 9 + currentLength) / 10;
        behaviorPatterns.put("conversationLengthAvg", newAvg);

        // 分析回应风格偏好
        analyzeResponseStylePreference(behaviorPatterns, emotion);
    }

    /**
     * 分析回应风格偏好
     */
    private void analyzeResponseStylePreference(Map<String, Object> behaviorPatterns, EmotionAnalysisDTO emotion) {
        // 基于情感类型调整回应风格
        String currentStyle = (String) behaviorPatterns.getOrDefault("responseStyle", "gentle");

        switch (emotion.getPrimaryEmotion()) {
            case "SAD":
                behaviorPatterns.put("preferredStyleWhenSad", "comforting");
                break;
            case "ANXIOUS":
                behaviorPatterns.put("preferredStyleWhenAnxious", "reassuring");
                break;
            case "HAPPY":
                behaviorPatterns.put("preferredStyleWhenHappy", "playful");
                break;
        }
    }

    /**
     * 更新理解度评分
     */
    private void updateUnderstandingScore(Map<String, Object> profile) {
        @SuppressWarnings("unchecked")
        Map<String, Integer> emotionStats = (Map<String, Integer>) profile.get("emotionStats");

        int totalInteractions = emotionStats.values().stream().mapToInt(Integer::intValue).sum();

        if (totalInteractions < 10) {
            // 初期：缓慢学习
            profile.put("emotionalUnderstandingScore", 0.3);
        } else if (totalInteractions < 50) {
            // 中期：逐步理解
            profile.put("emotionalUnderstandingScore", 0.6);
        } else if (totalInteractions < 200) {
            // 后期：深度理解
            profile.put("emotionalUnderstandingScore", 0.8);
        } else {
            // 长期：高度理解
            profile.put("emotionalUnderstandingScore", 0.9);
        }

        // 更新理解层次
        updateUnderstandingLevels(profile, totalInteractions);
    }

    /**
     * 更新理解层次
     */
    private void updateUnderstandingLevels(Map<String, Object> profile, int totalInteractions) {
        @SuppressWarnings("unchecked")
        Map<String, Double> understandingLevels = (Map<String, Double>) profile.get("understandingLevels");

        if (totalInteractions >= 5) {
            understandingLevels.put("basic_facts", 0.7);
        }
        if (totalInteractions >= 20) {
            understandingLevels.put("emotional_patterns", 0.5);
        }
        if (totalInteractions >= 50) {
            understandingLevels.put("deep_motivations", 0.3);
        }
        if (totalInteractions >= 100) {
            understandingLevels.put("unspoken_needs", 0.2);
        }
        if (totalInteractions >= 200) {
            understandingLevels.put("future_aspirations", 0.1);
        }
    }

    /**
     * 保存画像到Redis
     */
    private void saveProfile(String key, Map<String, Object> profile) {
        profile.put("lastUpdated", System.currentTimeMillis());
        redisTemplate.opsForValue().set(key, profile);
        redisTemplate.expire(key, Duration.ofDays(30).toMinutes(), TimeUnit.MINUTES);
    }

    /**
     * 生成个性化洞察报告
     */
    public Map<String, Object> generatePersonalInsights(Long userId) {
        Map<String, Object> profile = getEmotionProfile(userId);
        Map<String, Object> insights = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Integer> emotionStats = (Map<String, Integer>) profile.get("emotionStats");
        @SuppressWarnings("unchecked")
        Map<String, Integer> topicPreferences = (Map<String, Integer>) profile.get("topicPreferences");
        @SuppressWarnings("unchecked")
        Map<String, Object> behaviorPatterns = (Map<String, Object>) profile.get("behaviorPatterns");

        // 情感洞察
        String dominantEmotion = emotionStats.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("STRONG_"))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NEUTRAL");

        int totalEmotions = emotionStats.values().stream().mapToInt(Integer::intValue).sum();

        // 话题洞察
        String favoriteTopic = topicPreferences.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("life");

        // 行为洞察
        @SuppressWarnings("unchecked")
        List<String> activeTimes = (List<String>) behaviorPatterns.get("activeTimes");
        String activePeriod = activeTimes.isEmpty() ? "随机时间" :
                "通常在 " + activeTimes.get(activeTimes.size() - 1) + " 左右活跃";

        insights.put("userId", userId);
        insights.put("totalInteractions", totalEmotions);
        insights.put("dominantEmotion", dominantEmotion);
        insights.put("dominantEmotionCount", emotionStats.getOrDefault(dominantEmotion, 0));
        insights.put("favoriteTopic", favoriteTopic);
        insights.put("activePeriod", activePeriod);
        insights.put("understandingScore", profile.get("emotionalUnderstandingScore"));
        insights.put("understandingLevels", profile.get("understandingLevels"));

        // 生成自然语言描述
        insights.put("description", generateInsightDescription(profile, insights));

        return insights;
    }

    /**
     * 生成洞察描述
     */
    private String generateInsightDescription(Map<String, Object> profile, Map<String, Object> insights) {
        StringBuilder description = new StringBuilder();

        description.append("我发现你是一个");

        // 情感特点
        String dominantEmotion = (String) insights.get("dominantEmotion");
        switch (dominantEmotion) {
            case "HAPPY": description.append("比较乐观开朗"); break;
            case "SAD": description.append("情感细腻丰富"); break;
            case "ANXIOUS": description.append("心思比较细腻"); break;
            case "ANGRY": description.append("情感比较强烈"); break;
            default: description.append("情绪稳定平和"); break;
        }

        description.append("的人。");

        // 话题偏好
        String favoriteTopic = (String) insights.get("favoriteTopic");
        description.append("你经常聊到").append(getTopicChinese(favoriteTopic)).append("相关的话题。");

        // 活跃时间
        description.append("你").append(insights.get("activePeriod")).append("和我聊天。");

        // 理解程度
        double score = (double) profile.get("emotionalUnderstandingScore");
        if (score > 0.7) {
            description.append("通过这么多次的交流，我已经对你有了比较深入的了解呢~");
        } else if (score > 0.4) {
            description.append("我正在努力了解更多关于你的事情~");
        } else {
            description.append("我们刚开始熟悉，期待更多了解你~");
        }

        return description.toString();
    }

    /**
     * 获取话题中文
     */
    private String getTopicChinese(String topic) {
        switch (topic) {
            case "study": return "学习";
            case "emotion": return "情感";
            case "entertainment": return "娱乐";
            default: return "生活";
        }
    }
}