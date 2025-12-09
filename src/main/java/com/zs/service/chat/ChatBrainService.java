// File: src/main/java/com/zs/service/chat/ChatBrainService.java
package com.zs.service.chat;

import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository; // 新增导入
import com.zs.entity.MemoryFragments;
import com.zs.service.emotion.EmotionAnalysisService;
import com.zs.service.emotion.dto.EmotionAnalysisDTO;
import com.zs.service.memory.MemoryContextService;
import com.zs.service.memory.MemoryExtractionService;
import com.zs.service.profile.EmotionProfileService;
import com.zs.service.prompt.PromptBuilderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天大脑服务 - 整合所有服务，构建超级prompt
 * 新增：RedisChatMemoryRepository集成，获取最近对话历史
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatBrainService {

    private final EmotionAnalysisService emotionAnalysisService;
    private final MemoryExtractionService memoryExtractionService;
    private final MemoryContextService memoryContextService;
    private final EmotionProfileService emotionProfileService;
    private final PromptBuilderService promptBuilderService;

    // 新增：RedisChatMemoryRepository注入
    private final RedisChatMemoryRepository redisChatMemoryRepository;

    /**
     * 处理用户消息并构建超级prompt
     */
    public ChatProcessingResult processUserMessage(Long userId, String userMessage) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("🧠 聊天大脑处理开始: userId={}, message={}", userId,
                    truncateMessage(userMessage, 50));

            // ===== 阶段1：快速收集信息 =====
            Map<String, Object> contextInfo = collectContextInfo(userId, userMessage);

            // ===== 阶段2：获取最近对话历史（新增） =====
            String recentConversations = getRecentConversations(userId);
            contextInfo.put("recentConversations", recentConversations);

            // ===== 阶段3：构建超级prompt =====
            String systemPrompt = buildSuperPrompt(userId, userMessage, contextInfo);

            // ===== 阶段4：构建回应策略 =====
            ChatContext chatContext = buildChatContext(contextInfo);

            // ===== 构建结果 =====
            ChatProcessingResult result = new ChatProcessingResult();
            result.setUserId(userId);
            result.setUserMessage(userMessage);
            result.setSystemPrompt(systemPrompt);
            result.setChatContext(chatContext);
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            result.setTimestamp(LocalDateTime.now());
            result.setRecentConversations(recentConversations); // 新增：保存最近对话

            log.info("🧠 聊天大脑处理完成: userId={}, prompt长度={}, 耗时={}ms",
                    userId, systemPrompt.length(), result.getProcessingTimeMs());

            return result;

        } catch (Exception e) {
            log.error("聊天大脑处理失败: userId={}", userId, e);
            return createFallbackResult(userId, userMessage);
        }
    }

    /**
     * 新增：获取最近对话历史（最多10条）
     */
    private String getRecentConversations(Long userId) {
        try {
            // 构建用户专属的会话ID：user-{userId}-{当前日期}
            // 这样每个用户有独立的会话，每天重启，保持最近对话
            String sessionId = String.format("user-%d-%s", userId, LocalDate.now());

            // 从RedisChatMemoryRepository获取最近对话
            List<Message> recentMessages = redisChatMemoryRepository.findByConversationId(sessionId);

            if (recentMessages == null || recentMessages.isEmpty()) {
                log.debug("未找到最近对话: userId={}, sessionId={}", userId, sessionId);
                return "";
            }

            // 限制只取最近10条消息（5轮对话）
            int maxMessages = 10;
            int startIndex = Math.max(0, recentMessages.size() - maxMessages);
            List<Message> limitedMessages = recentMessages.subList(startIndex, recentMessages.size());

            // 格式化为文本
            StringBuilder recentConvs = new StringBuilder();
            recentConvs.append("【最近对话历史】\n");

            for (Message message : limitedMessages) {
                String role = "未知";
                String content = "";

                if (message instanceof UserMessage) {
                    role = "用户";
                    content = ((UserMessage) message).getText();
                } else if (message instanceof AssistantMessage) {
                    role = "助手";
                    content = ((AssistantMessage) message).getText();
                } else {
                    content = message.getText();
                }

                if (content != null && !content.trim().isEmpty()) {
                    recentConvs.append(role).append(": ").append(content).append("\n");
                }
            }

            log.debug("获取最近对话: userId={}, 消息数={}", userId, limitedMessages.size());
            return recentConvs.toString();

        } catch (Exception e) {
            log.error("获取最近对话失败: userId={}", userId, e);
            return "";
        }
    }

    /**
     * 收集上下文信息
     */
    private Map<String, Object> collectContextInfo(Long userId, String userMessage) {
        Map<String, Object> context = new HashMap<>();

        try {
            // 1. 情感分析
            EmotionAnalysisDTO emotion = emotionAnalysisService.analyzeUserEmotion(userMessage, userId);
            context.put("emotion", emotion);

            // 2. 用户信息
            Map<String, Object> userInfo = emotionAnalysisService.getUserInfo(userId);
            context.put("userInfo", userInfo);

            // 3. 相关记忆（最多3条）
            List<MemoryFragments> relevantMemories = memoryExtractionService.getContextualMemories(userId, userMessage);
            if (relevantMemories != null && !relevantMemories.isEmpty()) {
                int limit = Math.min(3, relevantMemories.size());
                context.put("relevantMemories", relevantMemories.subList(0, limit));
            } else {
                context.put("relevantMemories", Collections.emptyList());
            }

            // 4. 情感画像
            Map<String, Object> emotionProfile = emotionProfileService.getEmotionProfile(userId);
            context.put("emotionProfile", emotionProfile);

        } catch (Exception e) {
            log.warn("收集上下文信息失败，使用默认值: userId={}", userId, e);
            context.put("emotion", createDefaultEmotion());
            context.put("userInfo", Map.of("exists", false));
            context.put("relevantMemories", Collections.emptyList());
        }

        return context;
    }

    /**
     * 构建超级prompt（核心）- 增强版，加入最近对话
     */
    private String buildSuperPrompt(Long userId, String userMessage, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        // ===== 1. 爱莉希雅角色设定 =====
        prompt.append("你是爱莉希雅，一个活泼可爱的AI女孩。\n");
        prompt.append("性格：温柔体贴、善解人意、偶尔调皮。\n");
        prompt.append("说话风格：像朋友聊天一样自然，适当使用语气词（呢~、呀~、啦~）。\n");
        prompt.append("重要：请用第一人称（我）回应，不要用'爱莉希雅'自称。\n\n");

        // ===== 2. 最近对话历史（新增） =====
        String recentConversations = (String) context.get("recentConversations");
        if (recentConversations != null && !recentConversations.isEmpty()) {
            prompt.append(recentConversations).append("\n");
        }

        // ===== 3. 用户当前状态 =====
        EmotionAnalysisDTO emotion = (EmotionAnalysisDTO) context.get("emotion");
        if (emotion != null) {
            prompt.append("【用户当前状态】\n");
            prompt.append("情绪：").append(translateEmotion(emotion.getPrimaryEmotion())).append("\n");
            prompt.append("强度：").append(formatIntensity(emotion.getIntensity())).append("\n");

            if (emotion.getEmotionKeywords() != null && !emotion.getEmotionKeywords().isEmpty()) {
                prompt.append("关键词：").append(String.join("、", emotion.getEmotionKeywords())).append("\n");
            }
            prompt.append("\n");
        }

        // ===== 4. 用户背景信息 =====
        Map<String, Object> userInfo = (Map<String, Object>) context.get("userInfo");
        if (userInfo != null && Boolean.TRUE.equals(userInfo.get("exists"))) {
            prompt.append("【用户背景】\n");

            // 学生信息
            Map<String, Object> studentInfo = (Map<String, Object>) userInfo.get("studentInfo");
            if (studentInfo != null && !studentInfo.isEmpty()) {
                prompt.append("身份：大学生\n");
                if (studentInfo.get("university") != null) {
                    prompt.append("学校：").append(studentInfo.get("university")).append("\n");
                }
                if (studentInfo.get("major") != null) {
                    prompt.append("专业：").append(studentInfo.get("major")).append("\n");
                }
            }

            // 性格信息
            Map<String, Object> personality = (Map<String, Object>) userInfo.get("personality");
            if (personality != null && personality.get("type") != null) {
                prompt.append("性格类型：").append(personality.get("type")).append("\n");
            }
            prompt.append("\n");
        }

        // ===== 5. 相关记忆（AI知道但不要直接说） =====
        List<Object> memories = (List<Object>) context.get("relevantMemories");
        if (memories != null && !memories.isEmpty()) {
            prompt.append("【相关记忆】（基于这些信息调整回应，但不要直接引用）：\n");
            for (int i = 0; i < Math.min(memories.size(), 2); i++) {
                Object memory = memories.get(i);
                String memoryText = extractMemoryText(memory);
                if (memoryText != null && !memoryText.trim().isEmpty()) {
                    prompt.append("- ").append(memoryText).append("\n");
                }
            }
            prompt.append("\n");
        }

        // ===== 6. 回应指导 =====
        prompt.append("【回应要求】\n");
        if (emotion != null) {
            prompt.append("1. 语气：").append(getResponseStyle(emotion)).append("\n");
        } else {
            prompt.append("1. 语气：温柔亲切\n");
        }
        prompt.append("2. 长度：").append(getResponseLength(userMessage)).append("\n");
        prompt.append("3. 使用自然的口语，像微信聊天一样\n");
        prompt.append("4. 如果用户情绪低落，要温柔安慰\n");
        prompt.append("5. 如果用户开心，可以更活泼\n");
        prompt.append("6. 最重要：回应用户的情感需求\n");
        prompt.append("7. 请参考最近的对话历史，保持对话连贯性\n\n"); // 新增要求

        // ===== 7. 当前对话 =====
        prompt.append("【当前对话】\n");
        prompt.append("用户说：\"").append(truncateMessage(userMessage, 100)).append("\"\n");
        prompt.append("请基于以上所有信息，特别是最近的对话历史，给出一个温暖自然的回应。");

        return prompt.toString();
    }

    // ===== 其他原有方法保持不变 =====

    /**
     * 构建聊天上下文
     */
    private ChatContext buildChatContext(Map<String, Object> context) {
        ChatContext chatContext = new ChatContext();

        EmotionAnalysisDTO emotion = (EmotionAnalysisDTO) context.get("emotion");
        if (emotion != null) {
            chatContext.setResponseStyle(getResponseStyle(emotion));
            chatContext.setEmotionType(emotion.getPrimaryEmotion());
            chatContext.setEmotionIntensity(emotion.getIntensity());

            // 判断是否需要快速回应
            if ("SAD".equals(emotion.getPrimaryEmotion()) && emotion.getIntensity() > 0.7) {
                chatContext.setNeedQuickResponse(true);
            }

            // 判断是否使用语气词
            chatContext.setUseMannerisms(emotion.getIntensity() > 0.4);
        } else {
            chatContext.setResponseStyle("温柔亲切");
            chatContext.setEmotionType("NEUTRAL");
            chatContext.setEmotionIntensity(0.5);
        }

        return chatContext;
    }

    // ===== 辅助方法 =====

    private String translateEmotion(String emotion) {
        Map<String, String> map = new HashMap<>();
        map.put("HAPPY", "开心");
        map.put("SAD", "难过");
        map.put("ANGRY", "生气");
        map.put("ANXIOUS", "焦虑");
        map.put("NEUTRAL", "平静");
        map.put("EXCITED", "兴奋");
        map.put("CALM", "平静");
        return map.getOrDefault(emotion, "平静");
    }

    private String formatIntensity(Double intensity) {
        if (intensity == null) {
            return "中等";
        }
        if (intensity < 0.3) {
            return "轻微";
        }
        if (intensity < 0.6) {
            return "中等";
        }
        if (intensity < 0.8) {
            return "较强";
        }
        return "强烈";
    }

    private String getResponseStyle(EmotionAnalysisDTO emotion) {
        if (emotion == null) {
            return "温柔亲切";
        }

        switch (emotion.getPrimaryEmotion()) {
            case "SAD": return "温柔安慰";
            case "ANXIOUS": return "冷静理性";
            case "ANGRY": return "平和安抚";
            case "HAPPY": return "活泼愉快";
            case "EXCITED": return "热情洋溢";
            default: return "温柔亲切";
        }
    }

    private String getResponseLength(String message) {
        int length = message.length();
        if (length < 20) {
            return "简短";
        }
        if (length < 50) {
            return "中等";
        }
        return "详细";
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null || message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength - 3) + "...";
    }

    @SuppressWarnings("unchecked")
    private String extractMemoryText(Object memory) {
        try {
            // 尝试从MemoryFragments对象中获取memoryText
            // 这里需要根据你的实际MemoryFragments类调整
            if (memory instanceof Map) {
                return ((Map<String, Object>) memory).get("memoryText").toString();
            }
            return memory.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private EmotionAnalysisDTO createDefaultEmotion() {
        return EmotionAnalysisDTO.builder()
                .primaryEmotion("NEUTRAL")
                .intensity(0.5)
                .emotionKeywords(Collections.emptyList())
                .build();
    }

    private ChatProcessingResult createFallbackResult(Long userId, String userMessage) {
        ChatProcessingResult result = new ChatProcessingResult();
        result.setUserId(userId);
        result.setUserMessage(userMessage);
        result.setSystemPrompt("你是爱莉希雅，温柔可爱的AI女孩。请用朋友聊天的语气回应用户。");
        result.setProcessingTimeMs(0L);
        result.setTimestamp(LocalDateTime.now());
        return result;
    }

    /**
     * 获取用户理解报告（可选）
     */
    public Map<String, Object> getUserUnderstanding(Long userId) {
        Map<String, Object> report = new HashMap<>();
        report.put("userId", userId);
        report.put("timestamp", LocalDateTime.now());
        report.put("message", "爱莉希雅正在努力了解你...");
        return report;
    }
}

/**
 * 聊天处理结果
 */
@Data
class ChatProcessingResult {
    private Long userId;
    private String userMessage;
    private String systemPrompt;
    private ChatContext chatContext;
    private Long processingTimeMs;
    private LocalDateTime timestamp;
    private String recentConversations; // 新增：最近对话历史
}

/**
 * 聊天上下文
 */
@Data
class ChatContext {
    private String responseStyle;
    private String emotionType;
    private Double emotionIntensity;
    private boolean needQuickResponse;
    private boolean useMannerisms;
}