// File: src/main/java/com/zs/service/conversation/ConversationProcessor.java
package com.zs.service.conversation;

import com.zs.entity.Conversations;
import com.zs.entity.EmotionalMemories;
import com.zs.mapper.ConversationsMapper;
import com.zs.mapper.EmotionalMemoriesMapper;
import com.zs.service.emotion.EmotionAnalysisService;
import com.zs.service.emotion.dto.EmotionAnalysisDTO;
import com.zs.service.memory.MemoryExtractionService;
import com.zs.service.memory.MemoryContextService;
import com.zs.util.JsonTypeHandler;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Time;
import java.time.LocalTime;
import java.util.Date;

/**
 * 对话完整处理器 - 使用JSON工具类修复JSON字段问题
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationProcessor {

    private final EmotionAnalysisService emotionAnalysisService;
    private final MemoryExtractionService memoryExtractionService;
    private final MemoryContextService memoryContextService;
    private final JsonTypeHandler jsonTypeHandler; // 使用JSON工具类

    // Mapper
    private final ConversationsMapper conversationsMapper;
    private final EmotionalMemoriesMapper emotionalMemoriesMapper;

    /**
     * 完整对话处理流水线
     */
    @Transactional
    public ProcessResult processCompleteConversation(Long userId, String userMessage, String aiResponse) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始完整对话处理: userId={}, messageLength={}", userId, userMessage.length());

            // ========== 阶段1: 情感分析 ==========
            EmotionAnalysisDTO emotion = emotionAnalysisService.analyzeUserEmotion(userMessage, userId);

            // ========== 阶段2: 保存完整对话记录 ==========
            Conversations conversation = saveConversationRecord(userId, userMessage, aiResponse, emotion);

            // ========== 阶段3: 保存情感记忆 ==========
            if (emotion.getIsMeaningful() != null && emotion.getIsMeaningful()) {
                saveEmotionalMemory(userId, conversation.getId(), emotion);
            }

            // ========== 阶段4: 记忆提取 ==========
            memoryExtractionService.extractMemoriesAsync(conversation.getId());

            // ========== 阶段5: 更新用户心情状态 ==========
            updateUserEmotionState(userId, emotion);

            // ========== 阶段6: 构建记忆上下文 ==========
            String memoryContext = memoryContextService.buildMemoryContext(userId, userMessage);

            long processingTime = System.currentTimeMillis() - startTime;

            return ProcessResult.builder()
                    .success(true)
                    .conversationId(conversation.getId())
                    .emotion(emotion)
                    .memoryContext(memoryContext)
                    .processingTimeMs(processingTime)
                    .build();

        } catch (Exception e) {
            log.error("对话处理失败: userId={}", userId, e);
            return ProcessResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 保存完整对话记录 - 使用JSON工具类处理JSON字段
     */
    private Conversations saveConversationRecord(Long userId, String userMessage,
                                                 String aiResponse, EmotionAnalysisDTO emotion) {
        Conversations conversation = new Conversations();

        // 基础信息
        conversation.setUserId(userId);
        conversation.setUserMessage(userMessage);
        conversation.setAiResponse(aiResponse);

        // 情感分析结果
        conversation.setEmotionLabel(emotion.getPrimaryEmotion());

        // 设置置信度，确保不为null
        BigDecimal confidence = BigDecimal.valueOf(
                emotion.getConfidence() != null ? emotion.getConfidence() : 0.5
        );
        conversation.setEmotionConfidence(confidence);

        // 情感关键词 - 使用JSON工具类确保有效的JSON格式
        if (emotion.getEmotionKeywords() != null && !emotion.getEmotionKeywords().isEmpty()) {
            // 使用JsonTypeHandler生成有效的JSON字符串
            String keywordsJson = jsonTypeHandler.processEmotionKeywords(emotion.getEmotionKeywords());
            conversation.setEmotionKeywords(keywordsJson);
            log.debug("生成情感关键词JSON: {}", keywordsJson);
        } else {
            // 空数组
            conversation.setEmotionKeywords("[]");
        }

        // 对话上下文和重要性标记
        conversation.setConversationContext(emotion.getConversationContext());
        conversation.setIsMeaningful(emotion.getIsMeaningful() != null && emotion.getIsMeaningful() ? 1 : 0);

        // user_feedback字段（如果有）- 使用JSON工具类
        if (emotion.getEmotionScores() != null && !emotion.getEmotionScores().isEmpty()) {
            String feedbackJson = jsonTypeHandler.mapToJson(emotion.getEmotionScores());
            conversation.setUserFeedback(feedbackJson);
        }

        // 时间戳
        conversation.setCreatedAt(new Date());

        // 插入数据库
        conversationsMapper.insert(conversation);
        log.info("保存对话记录成功: conversationId={}, emotion={}, keywords={}",
                conversation.getId(), emotion.getPrimaryEmotion(),
                conversation.getEmotionKeywords());

        return conversation;
    }

    /**
     * 保存情感记忆到emotional_memories表
     */
    private void saveEmotionalMemory(Long userId, Long conversationId, EmotionAnalysisDTO emotion) {
        try {
            EmotionalMemories emotionalMemory = new EmotionalMemories();

            emotionalMemory.setUserId(userId);
            emotionalMemory.setEmotionType(emotion.getPrimaryEmotion());
            emotionalMemory.setEmotionContext(emotion.getConversationContext());

            // 触发关键词 - 使用逗号分隔的字符串
            if (emotion.getEmotionKeywords() != null && !emotion.getEmotionKeywords().isEmpty()) {
                emotionalMemory.setTriggerKeywords(String.join(",", emotion.getEmotionKeywords()));
            }

            // 生活场景 - 使用JSON工具类确保有效JSON
            if (emotion.getLifeScenario() != null) {
                String scenarioJson = jsonTypeHandler.processLifeScenario(emotion.getLifeScenario());
                emotionalMemory.setLifeScenario(scenarioJson);
            }

            // AI回应效果（默认3分）
            emotionalMemory.setResponseEffectiveness(3);

            // 时间模式 - 处理时间字段
            emotionalMemory.setOccurrenceTime(Time.valueOf(LocalTime.now()).toLocalTime());

            // 获取星期几
            emotionalMemory.setOccurrenceDay(getCurrentDayOfWeek());

            emotionalMemory.setAiResponsePattern(""); // 可后续填充

            // 设置时间戳
            Date now = new Date();
            emotionalMemory.setCreatedAt(now);
            emotionalMemory.setUpdatedAt(now);

            // 插入数据库
            emotionalMemoriesMapper.insert(emotionalMemory);
            log.info("保存情感记忆成功: userId={}, emotion={}, memoryId={}",
                    userId, emotion.getPrimaryEmotion(), emotionalMemory.getId());

        } catch (Exception e) {
            log.error("保存情感记忆失败: userId={}", userId, e);
        }
    }

    /**
     * 获取当前星期几
     */
    private String getCurrentDayOfWeek() {
        String[] days = {"日", "一", "二", "三", "四", "五", "六"};
        int dayOfWeek = java.time.LocalDate.now().getDayOfWeek().getValue() % 7;
        return "星期" + days[dayOfWeek];
    }

    /**
     * 更新用户心情状态（待实现）
     */
    private void updateUserEmotionState(Long userId, EmotionAnalysisDTO emotion) {
        log.debug("用户心情更新: userId={}, emotion={}, intensity={}",
                userId, emotion.getPrimaryEmotion(), emotion.getIntensity());
    }

    /**
     * 处理结果封装
     */
    @Data
    @Builder
    public static class ProcessResult {
        private boolean success;
        private Long conversationId;
        private EmotionAnalysisDTO emotion;
        private String memoryContext;
        private Long processingTimeMs;
        private String errorMessage;
    }
}