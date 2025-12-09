// File: src/main/java/com/zs/controller/chat/StreamChatController.java
package com.zs.controller.chat;

import com.zs.service.chat.StreamingChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

/**
 * 流式聊天控制器 - 提供微信式聊天体验
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class StreamChatController {

    private final StreamingChatService streamingChatService;

    /**
     * SSE流式聊天接口（推荐）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(
            @RequestParam Long userId,
            @RequestParam String message,
            @RequestParam(defaultValue = "qwen") String model) {

        log.info("📱 流式聊天请求: userId={}, model={}, message={}...",
                userId, model,
                message.length() > 30 ? message.substring(0, 30) + "..." : message);

        return streamingChatService.streamChat(userId, message, model)
                .timeout(Duration.ofSeconds(60))
                .doOnSubscribe(sub -> log.debug("开始SSE流: userId={}", userId))
                .doOnComplete(() -> log.debug("SSE流完成: userId={}", userId))
                .doOnError(e -> log.error("SSE流错误: userId={}", userId, e));
    }

    /**
     * 简单聊天接口（HTTP POST）
     */
    @PostMapping("/simple")
    public Map<String, Object> simpleChat(
            @RequestBody Map<String, Object> request) {

        Long userId = Long.parseLong(request.get("userId").toString());
        String message = request.get("message").toString();
        String model = request.getOrDefault("model", "qwen").toString();

        log.info("💬 简单聊天请求: userId={}, model={}", userId, model);

        long startTime = System.currentTimeMillis();
        String response = streamingChatService.simpleChat(userId, message, model);
        long processingTime = System.currentTimeMillis() - startTime;

        return Map.of(
                "userId", userId,
                "userMessage", message,
                "aiResponse", response,
                "model", model,
                "processingTimeMs", processingTime,
                "timestamp", java.time.LocalDateTime.now().toString()
        );
    }

    /**
     * WebSocket流式聊天（可选）
     */
    @GetMapping(value = "/stream-text", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChatText(
            @RequestParam Long userId,
            @RequestParam String message,
            @RequestParam(defaultValue = "qwen") String model) {

        return streamingChatService.streamChat(userId, message, model)
                .map(sse -> {
                    if ("message".equals(sse.event())) {
                        return sse.data();
                    }
                    return "";
                })
                .filter(text -> !text.isEmpty());
    }

    /**
     * 测试接口
     */
    @GetMapping("/test")
    public Map<String, Object> test() {
        return Map.of(
                "status", "ok",
                "service", "StreamChatController",
                "version", "1.0.0",
                "timestamp", java.time.LocalDateTime.now().toString(),
                "features", java.util.List.of(
                        "SSE Streaming Chat",
                        "Simple HTTP Chat",
                        "Dual Model Support",
                        "Memory Integration"
                )
        );
    }

    /**
     * 健康检查 - 修改路径避免冲突
     */
    @GetMapping("/stream/health")  // 修改：从"/health"改为"/stream/health"
    public Map<String, Object> health() {
        return Map.of(
                "status", "healthy",
                "service", "StreamChatController",
                "timestamp", java.time.LocalDateTime.now().toString()
        );
    }
}