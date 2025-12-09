// File: src/main/java/com/zs/service/emotion/repository/EmotionRepository.java
package com.zs.service.emotion.repository;

import com.zs.entity.EmotionalMemories;
import com.zs.mapper.EmotionalMemoriesMapper;
import com.zs.service.emotion.dto.EmotionAnalysisDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 情感数据访问层
 * 负责MySQL数据库操作
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class EmotionRepository {

    private final EmotionalMemoriesMapper emotionalMemoriesMapper;

    /**
     * 保存情感记忆到数据库
     */
    @Transactional
    public EmotionalMemories saveEmotionalMemory(Long userId, EmotionAnalysisDTO emotion) {
        try {
            EmotionalMemories memory = new EmotionalMemories();

            memory.setUserId(userId);
            memory.setEmotionType(emotion.getPrimaryEmotion());
            memory.setEmotionContext(emotion.getConversationContext());
            memory.setTriggerKeywords(String.join(",", emotion.getEmotionKeywords()));
            memory.setLifeScenario(emotion.getLifeScenario());
            memory.setResponseEffectiveness(3); // 默认效果评分

            // 设置时间模式
            memory.setOccurrenceTime(LocalTime.now());
            memory.setOccurrenceDay(getDayOfWeekChinese());

            // AI回应模式（可后续更新）
            memory.setAiResponsePattern("");

            emotionalMemoriesMapper.insert(memory);

            log.debug("保存情感记忆到数据库: userId={}, memoryId={}, emotion={}",
                    userId, memory.getId(), emotion.getPrimaryEmotion());

            return memory;

        } catch (Exception e) {
            log.error("保存情感记忆失败: userId={}", userId, e);
            throw new RuntimeException("保存情感记忆失败", e);
        }
    }

    /**
     * 批量保存情感记忆
     */
    @Transactional
    public void batchSaveEmotionalMemories(Long userId, List<EmotionAnalysisDTO> emotions) {
        for (EmotionAnalysisDTO emotion : emotions) {
            if (emotion.getIsMeaningful() != null && emotion.getIsMeaningful()) {
                saveEmotionalMemory(userId, emotion);
            }
        }
    }

    /**
     * 查询用户的情感记忆
     */
    public List<EmotionalMemories> findUserEmotionalMemories(Long userId, int limit) {
        try {
            // 这里使用MyBatis Plus的查询，需要创建对应的Wrapper
            // 简化版本，实际需要根据业务调整
            return emotionalMemoriesMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EmotionalMemories>()
                            .eq("user_id", userId)
                            .orderByDesc("created_at")
                            .last("LIMIT " + limit)
            );
        } catch (Exception e) {
            log.error("查询用户情感记忆失败: userId={}", userId, e);
            return List.of();
        }
    }

    /**
     * 统计用户的情感分布
     */
    public java.util.Map<String, Long> getEmotionDistribution(Long userId) {
        try {
            // 这里需要自定义SQL查询
            // 简化版本，实际需要写Mapper.xml或使用@Select注解
            List<EmotionalMemories> memories = emotionalMemoriesMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EmotionalMemories>()
                            .select("emotion_type", "COUNT(*) as count")
                            .eq("user_id", userId)
                            .groupBy("emotion_type")
            );

            java.util.Map<String, Long> distribution = new java.util.HashMap<>();
            for (EmotionalMemories memory : memories) {
                // 这里需要解析查询结果
                // 简化处理
            }

            return distribution;

        } catch (Exception e) {
            log.error("统计情感分布失败: userId={}", userId, e);
            return new java.util.HashMap<>();
        }
    }

    /**
     * 获取星期几的中文
     */
    private String getDayOfWeekChinese() {
        String[] days = {"日", "一", "二", "三", "四", "五", "六"};
        int dayOfWeek = java.time.LocalDate.now().getDayOfWeek().getValue() % 7;
        return "星期" + days[dayOfWeek];
    }
}