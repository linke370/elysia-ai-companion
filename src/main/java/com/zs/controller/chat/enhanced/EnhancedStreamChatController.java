package com.zs.controller.chat.enhanced;

import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository; // 新增导入
import com.zs.service.chat.StreamingChatService;
import com.zs.service.chat.thinking.ThinkingEvent;
import com.zs.service.chat.thinking.ThinkingService;
import com.zs.service.emotion.EmotionAnalysisService;
import com.zs.service.emotion.state.AIEmotionService;
import com.zs.service.memory.MemoryExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 增强版流式聊天控制器 - 带思考过程和AI情感状态
 * 新增：RedisChatMemoryRepository集成，保存对话历史
 */
@RestController
@RequestMapping("/api/chat/enhanced")
@RequiredArgsConstructor
@Slf4j
public class EnhancedStreamChatController {

    // 原有服务（不变）
    private final StreamingChatService streamingChatService;
    private final EmotionAnalysisService emotionAnalysisService;
    private final MemoryExtractionService memoryExtractionService;

    // 新增服务
    private final ThinkingService thinkingService;
    private final AIEmotionService aiEmotionService;

    // 新增：RedisChatMemoryRepository
    private final RedisChatMemoryRepository redisChatMemoryRepository;

    // 用于累积完整AI回复
    private final Map<Long, AtomicReference<StringBuilder>> userResponseBuilders = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 增强版SSE流式聊天 - 带思考过程
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> enhancedStreamChat(
            @RequestParam Long userId,
            @RequestParam String message,
            @RequestParam(defaultValue = "qwen") String model) {

        log.info("🎯 增强版流式聊天请求: userId={}, model={}, message={}...",
                userId, model,
                message.length() > 30 ? message.substring(0, 30) + "..." : message);

        // 为当前用户初始化回复构建器
        userResponseBuilders.put(userId, new AtomicReference<>(new StringBuilder()));

        return Flux.create((FluxSink<ServerSentEvent<String>> sink) -> {
            try {
                // 阶段1：情感分析
                var emotion = emotionAnalysisService.analyzeUserEmotion(message, userId);

                // 阶段2：获取相关记忆
                var relevantMemories = memoryExtractionService.getContextualMemories(userId, message);

                // 阶段3：更新AI情感状态
                aiEmotionService.updateAIEmotion(userId,
                        emotion.getPrimaryEmotion(),
                        emotion.getIntensity());

                // 阶段4：生成思考过程
                List<ThinkingEvent> thinkingEvents = thinkingService.generateThinkingProcess(
                        userId, message, emotion.getPrimaryEmotion(),
                        relevantMemories != null ? relevantMemories.size() : 0);

                // 阶段5：发送思考过程
                sendThinkingProcess(sink, thinkingEvents);

                // 阶段6：获取AI情感状态
                var aiEmotionReport = aiEmotionService.getAIEmotionReport(userId);
                String aiStateDesc = (String) aiEmotionReport.get("description");

                // 阶段7：发送AI情感状态
                sink.next(createSSE("ai_emotion",
                        String.format("爱莉希雅当前状态：%s", aiStateDesc)));

                // 阶段8：调用原有流式聊天服务，并收集完整回复
                Flux<ServerSentEvent<String>> originalStream = streamingChatService.streamChat(userId, message, model);

                // 订阅并处理流事件
                originalStream.subscribe(
                        sse -> {
                            // 转发事件给前端
                            sink.next(sse);

                            // 如果是消息内容，累积到构建器
                            if ("message".equals(sse.event()) && sse.data() != null) {
                                StringBuilder builder = userResponseBuilders.get(userId).get();
                                if (builder != null) {
                                    builder.append(sse.data());
                                }
                            }
                        },
                        error -> {
                            log.error("流式聊天错误: userId={}", userId, error);
                            sink.error(error);
                        },
                        () -> {
                            // 流式聊天完成后，保存对话到Redis
                            String fullAIResponse = userResponseBuilders.get(userId).get().toString();
                            if (!fullAIResponse.trim().isEmpty()) {
                                saveConversationToRedis(userId, message, fullAIResponse);
                            }

                            // 清理构建器
                            userResponseBuilders.remove(userId);

                            // 完成SSE流
                            sink.complete();
                        }
                );

            } catch (Exception e) {
                log.error("增强版聊天流异常: userId={}", userId, e);
                sink.error(e);
            }
        }).timeout(Duration.ofSeconds(60));
    }

    /**
     * 增强版简单聊天（HTTP POST）
     */
    @PostMapping("/simple")
    public Map<String, Object> enhancedSimpleChat(
            @RequestBody Map<String, Object> request) {

        Long userId = Long.parseLong(request.get("userId").toString());
        String message = request.get("message").toString();
        String model = request.getOrDefault("model", "qwen").toString();

        log.info("💬 增强版简单聊天请求: userId={}, model={}", userId, model);

        long startTime = System.currentTimeMillis();

        // 阶段1：情感分析
        var emotion = emotionAnalysisService.analyzeUserEmotion(message, userId);

        // 阶段2：更新AI情感状态
        aiEmotionService.updateAIEmotion(userId,
                emotion.getPrimaryEmotion(),
                emotion.getIntensity());

        // 阶段3：获取AI情感状态
        var aiEmotionReport = aiEmotionService.getAIEmotionReport(userId);

        // 阶段4：调用原有简单聊天服务
        String response = streamingChatService.simpleChat(userId, message, model);
        long processingTime = System.currentTimeMillis() - startTime;

        // 阶段5：保存对话到Redis
        saveConversationToRedis(userId, message, response);

        return Map.of(
                "userId", userId,
                "userMessage", message,
                "aiResponse", response,
                "model", model,
                "aiEmotionState", aiEmotionReport,
                "processingTimeMs", processingTime,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    /**
     * 新增：保存对话到RedisChatMemoryRepository
     */
    private void saveConversationToRedis(Long userId, String userMessage, String aiResponse) {
        try {
            // 构建用户专属的会话ID：user-{userId}-{当前日期}
            // 这样每个用户有独立的会话，每天重启，保持最近对话
            String sessionId = String.format("user-%d-%s", userId, LocalDate.now());

            // 创建用户消息和助手消息
            UserMessage userMsg = new UserMessage(userMessage);
            AssistantMessage assistantMsg = new AssistantMessage(aiResponse);

            // 保存到RedisChatMemoryRepository
            List<Message> messages = List.of(userMsg, assistantMsg);
            redisChatMemoryRepository.saveAll(sessionId, messages);

            log.debug("保存对话到Redis: userId={}, sessionId={}, 消息数={}",
                    userId, sessionId, messages.size());

        } catch (Exception e) {
            log.error("保存对话到Redis失败: userId={}", userId, e);
        }
    }

    /**
     * 发送思考过程（带延迟效果）
     */
    private void sendThinkingProcess(
            FluxSink<ServerSentEvent<String>> sink,
            List<ThinkingEvent> thinkingEvents) {

        AtomicInteger index = new AtomicInteger(0);

        // 使用定时器模拟思考过程
        Flux.interval(Duration.ofMillis(500))
                .take(thinkingEvents.size())
                .subscribe(i -> {
                    int idx = index.getAndIncrement();
                    if (idx < thinkingEvents.size()) {
                        ThinkingEvent event = thinkingEvents.get(idx);
                        sink.next(createSSE(event.getEvent(), event.toString()));
                    }
                });
    }

    /**
     * 创建SSE事件
     */
    private ServerSentEvent<String> createSSE(String event, String data) {
        return ServerSentEvent.<String>builder()
                .event(event)
                .data(data)
                .id(LocalDateTime.now().toString())
                .build();
    }

    /**
     * 测试接口
     */
    @GetMapping("/test")
    public Map<String, Object> test() {
        return Map.of(
                "status", "ok",
                "service", "EnhancedStreamChatController",
                "version", "2.0.0",
                "timestamp", LocalDateTime.now().toString(),
                "features", List.of(
                        "带思考过程的SSE聊天",
                        "AI情感状态管理",
                        "增强版简单聊天",
                        "RedisChatMemoryRepository集成",
                        "最近对话历史持久化",
                        "兼容原有所有功能"
                )
        );
    }
}