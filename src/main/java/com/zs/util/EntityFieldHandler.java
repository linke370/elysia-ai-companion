// File: src/main/java/com/zs/util/EntityFieldHandler.java
package com.zs.util;

import com.zs.entity.Conversations;
import com.zs.entity.EmotionalMemories;
import com.zs.service.emotion.dto.EmotionAnalysisDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 实体类字段处理器
 * 专门处理实体类中的JSON字段
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityFieldHandler {

    private final JsonTypeHandler jsonTypeHandler;

    /**
     * 设置Conversations实体类的JSON字段
     */
    public Conversations populateConversationFields(Conversations conversation,
                                                    EmotionAnalysisDTO emotion) {
        // 情感关键词 - 确保有效的JSON
        if (emotion.getEmotionKeywords() != null && !emotion.getEmotionKeywords().isEmpty()) {
            String keywordsJson = jsonTypeHandler.processEmotionKeywords(emotion.getEmotionKeywords());
            conversation.setEmotionKeywords(keywordsJson);
        } else {
            conversation.setEmotionKeywords("[]");
        }

        // 用户反馈（如果有）
        if (emotion.getEmotionScores() != null && !emotion.getEmotionScores().isEmpty()) {
            String feedbackJson = jsonTypeHandler.mapToJson(emotion.getEmotionScores());
            conversation.setUserFeedback(feedbackJson);
        }

        // 其他字段
        conversation.setEmotionLabel(emotion.getPrimaryEmotion());
        conversation.setConversationContext(emotion.getConversationContext());
        conversation.setIsMeaningful(emotion.getIsMeaningful() != null && emotion.getIsMeaningful() ? 1 : 0);
        conversation.setCreatedAt(new Date());

        return conversation;
    }

    /**
     * 设置EmotionalMemories实体类的JSON字段
     */
    public EmotionalMemories populateEmotionalMemoryFields(EmotionalMemories memory,
                                                           EmotionAnalysisDTO emotion) {
        // 触发关键词 - 逗号分隔的字符串
        if (emotion.getEmotionKeywords() != null && !emotion.getEmotionKeywords().isEmpty()) {
            memory.setTriggerKeywords(String.join(",", emotion.getEmotionKeywords()));
        }

        // 生活场景 - 确保有效的JSON
        if (emotion.getLifeScenario() != null) {
            String scenarioJson = jsonTypeHandler.processLifeScenario(emotion.getLifeScenario());
            memory.setLifeScenario(scenarioJson);
        }

        // 其他字段
        memory.setEmotionType(emotion.getPrimaryEmotion());
        memory.setEmotionContext(emotion.getConversationContext());
        memory.setResponseEffectiveness(3); // 默认值
        memory.setAiResponsePattern("");
        memory.setCreatedAt(new Date());
        memory.setUpdatedAt(new Date());

        return memory;
    }

    /**
     * 验证实体类的JSON字段是否有效
     */
    public boolean validateEntityJsonFields(Conversations conversation) {
        boolean allValid = true;

        // 验证emotion_keywords
        if (conversation.getEmotionKeywords() != null) {
            String keywords = conversation.getEmotionKeywords().toString();
            if (!jsonTypeHandler.isValidJson(keywords)) {
                allValid = false;
                log.warn("Invalid emotion_keywords JSON: {}", keywords);
            }
        }

        // 验证user_feedback
        if (conversation.getUserFeedback() != null) {
            String feedback = conversation.getUserFeedback().toString();
            if (!jsonTypeHandler.isValidJson(feedback)) {
                allValid = false;
                log.warn("Invalid user_feedback JSON: {}", feedback);
            }
        }

        return allValid;
    }
}