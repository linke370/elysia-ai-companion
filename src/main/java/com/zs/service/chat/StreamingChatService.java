package com.zs.service.chat;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式聊天服务 - 支持双模型，模拟微信聊天效果
 * 适配Spring AI Alibaba 1.0.0.2
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingChatService {

    private final ChatBrainService chatBrainService;

    // 注入ChatClient（带记忆）
    private final ChatClient qwenChatClient;
    private final ChatClient deepSeekChatClient;

    // 注入流式模型
    private final DashScopeChatModel qwenStreamingModel;
    private final DashScopeChatModel deepSeekStreamingModel;

    /**
     * 流式聊天主方法 - 返回SSE流
     */
    public Flux<ServerSentEvent<String>> streamChat(Long userId, String userMessage, String modelType) {
        return Flux.create(sink -> {
            try {
                // 阶段1：构建超级prompt
                ChatProcessingResult processingResult = chatBrainService.processUserMessage(userId, userMessage);
                String systemPrompt = processingResult.getSystemPrompt();

                // 阶段2：选择模型
                ChatModel chatModel = selectChatModel(modelType);

                // 阶段3：构建Prompt
                SystemPromptTemplate systemTemplate = new SystemPromptTemplate(systemPrompt);
                var systemMessage = systemTemplate.createMessage();

                Prompt prompt = new Prompt(
                        java.util.List.of(
                                systemMessage,
                                new org.springframework.ai.chat.messages.UserMessage(userMessage)
                        )
                );

                // 阶段4：流式生成
                generateStreamingResponse(chatModel, prompt, sink, userId);

            } catch (Exception e) {
                log.error("流式聊天失败: userId={}", userId, e);
                sink.next(createSSE("error", "抱歉，我好像有点卡壳了..."));
                sink.complete();
            }
        });
    }

    /**
     * 生成流式回应（核心）
     */
    private void generateStreamingResponse(ChatModel chatModel, Prompt prompt,
                                           FluxSink<ServerSentEvent<String>> sink,
                                           Long userId) {

        // 用于累积句子
        AtomicReference<StringBuilder> sentenceBuilder = new AtomicReference<>(new StringBuilder());
        AtomicInteger sentenceCount = new AtomicInteger(0);

        // 开始标记
        sink.next(createSSE("start", ""));

        // 调用流式API
        Flux<ChatResponse> responseFlux = chatModel.stream(prompt);

        responseFlux
                .doOnSubscribe(subscription -> {
                    log.debug("开始流式生成: userId={}", userId);
                })
                .flatMap(chatResponse -> {
                    // 提取内容
                    String chunk = chatResponse.getResult().getOutput().getText();
                    return Mono.just(chunk);
                })
                .bufferTimeout(50, Duration.ofMillis(100)) // 每100ms或50个字符发送一次
                .flatMap(chunks -> {
                    String combined = String.join("", chunks);
                    return processChunk(combined, sentenceBuilder, sentenceCount, sink);
                })
                .doOnComplete(() -> {
                    // 发送最后一句
                    String finalSentence = sentenceBuilder.get().toString();
                    if (!finalSentence.trim().isEmpty()) {
                        sink.next(createSSE("message", finalSentence));
                        sentenceCount.incrementAndGet();
                    }

                    // 完成标记
                    sink.next(createSSE("complete", String.format("共生成%d句话", sentenceCount.get())));
                    sink.complete();

                    log.info("流式生成完成: userId={}, sentences={}", userId, sentenceCount.get());
                })
                .doOnError(error -> {
                    log.error("流式生成错误: userId={}", userId, error);
                    sink.next(createSSE("error", "生成回应时出错了..."));
                    sink.complete();
                })
                .subscribe();
    }

    /**
     * 处理流式块
     */
    private Mono<Void> processChunk(String chunk,
                                    AtomicReference<StringBuilder> sentenceBuilder,
                                    AtomicInteger sentenceCount,
                                    FluxSink<ServerSentEvent<String>> sink) {

        return Mono.fromRunnable(() -> {
            StringBuilder currentBuilder = sentenceBuilder.get();
            currentBuilder.append(chunk);

            String currentText = currentBuilder.toString();

            // 查找句子结束符
            int endIndex = findSentenceEnd(currentText);

            if (endIndex > 0) {
                // 提取完整句子
                String sentence = currentText.substring(0, endIndex).trim();
                if (!sentence.isEmpty()) {
                    // 发送句子
                    sink.next(createSSE("message", sentence));
                    sentenceCount.incrementAndGet();

                    // 移除已发送的部分
                    currentBuilder.delete(0, endIndex);

                    // 模拟打字延迟（50-300ms随机）
                    try {
                        Thread.sleep(50 + (int)(Math.random() * 250));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            // 如果句子太长（超过100字），强制分割
            if (currentBuilder.length() > 100) {
                String longSentence = currentBuilder.toString();
                sink.next(createSSE("message", longSentence));
                sentenceCount.incrementAndGet();
                currentBuilder.setLength(0);

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    /**
     * 查找句子结束位置
     */
    private int findSentenceEnd(String text) {
        // 中文句子结束符：。！？；\n
        int[] positions = {
                text.indexOf('。'), text.indexOf('！'), text.indexOf('？'),
                text.indexOf(';'), text.indexOf('\n'), text.indexOf('.'),
                text.indexOf('!'), text.indexOf('?')
        };

        int endIndex = -1;
        for (int pos : positions) {
            if (pos > 0 && (endIndex == -1 || pos < endIndex)) {
                endIndex = pos + 1; // 包含结束符
            }
        }

        return endIndex;
    }

    /**
     * 选择聊天模型
     */
    private ChatModel selectChatModel(String modelType) {
        if ("deepseek".equalsIgnoreCase(modelType)) {
            return deepSeekStreamingModel;
        }
        // 默认使用千问
        return qwenStreamingModel;
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
     * 简单聊天（非流式，用于测试）
     */
    public String simpleChat(Long userId, String userMessage, String modelType) {
        try {
            // 构建prompt
            ChatProcessingResult processingResult = chatBrainService.processUserMessage(userId, userMessage);
            String systemPrompt = processingResult.getSystemPrompt();

            // 选择ChatClient
            ChatClient chatClient = selectChatClient(modelType);

            // 调用AI
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            log.info("简单聊天完成: userId={}, 回应长度={}", userId, response.length());

            return response;

        } catch (Exception e) {
            log.error("简单聊天失败: userId={}", userId, e);
            return "抱歉，我好像有点卡壳了...";
        }
    }

    /**
     * 选择ChatClient（带记忆）
     */
    private ChatClient selectChatClient(String modelType) {
        if ("deepseek".equalsIgnoreCase(modelType)) {
            return deepSeekChatClient;
        }
        return qwenChatClient;
    }
}