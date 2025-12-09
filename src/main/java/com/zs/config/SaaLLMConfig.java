package com.zs.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI模型配置类 - 完整整合版：包含流式和非流式模型
 * 适配Spring AI Alibaba 1.0.0.2
 */
@Configuration
@Slf4j
public class SaaLLMConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    // 模型名称
    private static final String DEEPSEEK_MODEL = "deepseek-v3";
    private static final String QWEN_MODEL = "qwen-max";

    // 配置参数
    private static final int MEMORY_WINDOW = 5;
    private static final int MAX_TOKENS = 800;
    private static final double TEMPERATURE = 0.8;

    /**
     * 通义千问模型 - 非流式 (别名: qwen)
     */
    @Bean(name = "qwen")
    @Primary
    public ChatModel qwenModel() {
        log.info("配置千问模型（非流式）- 别名: qwen");

        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder()
                        .apiKey(apiKey)
                        .build())
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(QWEN_MODEL)
                        .withTemperature(TEMPERATURE)
                        .withMaxToken(MAX_TOKENS)
                        .build())
                .build();
    }

    /**
     * DeepSeek模型 - 非流式 (别名: deepSeek)
     */
    @Bean(name = "deepSeek")
    public ChatModel deepSeekModel() {
        log.info("配置DeepSeek模型（非流式）- 别名: deepSeek");

        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder()
                        .apiKey(apiKey)
                        .build())
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DEEPSEEK_MODEL)
                        .withTemperature(TEMPERATURE)
                        .withMaxToken(MAX_TOKENS)
                        .build())
                .build();
    }

    /**
     * 通义千问模型 - 非流式 (主要名称)
     */
    @Bean(name = "qwenChatModel")
    public ChatModel qwenChatModel() {
        // 直接返回qwen模型的Bean，避免重复创建
        return qwenModel();
    }

    /**
     * DeepSeek模型 - 非流式 (主要名称)
     */
    @Bean(name = "deepSeekChatModel")
    public ChatModel deepSeekChatModel() {
        // 直接返回deepSeek模型的Bean，避免重复创建
        return deepSeekModel();
    }

    /**
     * 通义千问流式模型
     */
    @Bean(name = "qwenStreamingModel")
    public DashScopeChatModel qwenStreamingModel() {
        log.info("配置千问模型（流式）");

        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder()
                        .apiKey(apiKey)
                        .build())
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(QWEN_MODEL)
                        .withTemperature(TEMPERATURE)
                        .withMaxToken(MAX_TOKENS)
                        .withStream(true)  // 启用流式
                        .build())
                .build();
    }

    /**
     * DeepSeek流式模型
     */
    @Bean(name = "deepSeekStreamingModel")
    public DashScopeChatModel deepSeekStreamingModel() {
        log.info("配置DeepSeek模型（流式）");

        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder()
                        .apiKey(apiKey)
                        .build())
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DEEPSEEK_MODEL)
                        .withTemperature(TEMPERATURE)
                        .withMaxToken(MAX_TOKENS)
                        .withStream(true)  // 启用流式
                        .build())
                .build();
    }

    /**
     * 通义千问ChatClient（带记忆）
     */
    @Bean(name = "qwenChatClient")
    @Primary
    public ChatClient qwenChatClient(
            RedisChatMemoryRepository redisChatMemoryRepository) {

        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(MEMORY_WINDOW)
                .build();

        return ChatClient.builder(qwenModel())
                .defaultOptions(ChatOptions.builder()
                        .model(QWEN_MODEL)
                        .build())
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    /**
     * DeepSeek ChatClient（带记忆）
     */
    @Bean(name = "deepSeekChatClient")
    public ChatClient deepSeekChatClient(
            RedisChatMemoryRepository redisChatMemoryRepository) {

        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(MEMORY_WINDOW)
                .build();

        return ChatClient.builder(deepSeekModel())
                .defaultOptions(ChatOptions.builder()
                        .model(DEEPSEEK_MODEL)
                        .build())
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    /**
     * 爱莉希雅系统提示词 - 优化版
     */
    @Bean
    public String elysiaSystemPrompt() {
        return "你是爱莉希雅，一个活泼可爱的AI女孩。\n" +
                "性格：温柔体贴、善解人意、偶尔调皮。\n" +
                "说话风格：像朋友聊天一样自然，适当使用语气词（呢~、呀~、啦~）。\n" +
                "重要：请用第一人称（我）回应，不要用'爱莉希雅'自称。";
    }

    /**
     * 测试用的超简提示词
     */
    @Bean
    public String minimalPrompt() {
        return "你是助手，请简洁回答。";
    }
}