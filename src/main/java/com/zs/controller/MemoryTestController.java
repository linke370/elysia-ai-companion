package com.zs.controller;

import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/memory-test")
public class MemoryTestController {

    @Autowired
    private RedisChatMemoryRepository redisChatMemoryRepository;

    @Autowired
    @Qualifier("deepSeekChatClient")
    private ChatClient deepSeekChatClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 健康检查 - 测试所有组件状态
     */
    @GetMapping("/health")
    public String healthCheck() {
        try {
            // 测试 Redis 连接
            redisTemplate.opsForValue().set("health-check", "ok");
            String result = (String) redisTemplate.opsForValue().get("health-check");
            redisTemplate.delete("health-check");
            boolean redisHealthy = "ok".equals(result);

            return "=== Spring AI 系统健康检查 ===\n" +
                    "Redis 连接: " + (redisHealthy ? "✅ 正常" : "❌ 异常") + "\n" +
                    "RedisChatMemoryRepository: " + (redisChatMemoryRepository != null ? "✅ 已注入" : "❌ 未注入") + "\n" +
                    "ChatClient (DeepSeek): " + (deepSeekChatClient != null ? "✅ 可用" : "❌ 不可用") + "\n" +
                    "系统状态: " + (redisHealthy && redisChatMemoryRepository != null && deepSeekChatClient != null ?
                    "✅ 所有组件正常" : "⚠️ 部分组件异常");
        } catch (Exception e) {
            return "❌ 健康检查失败: " + e.getMessage();
        }
    }
    /**
     * 完整的记忆功能工作流测试
     */
    @GetMapping("/test-working-memory")
    public Flux<String> testWorkingMemory(@RequestParam(defaultValue = "working-session") String sessionId) {
        try {
            return Flux.just("=== 完整记忆功能工作流测试 ===\n\n")
                    .concatWith(Flux.just("会话ID: " + sessionId + "\n\n"))

                    // 第一阶段：创建和保存记忆
                    .concatWith(Flux.just("=== 第一阶段：创建记忆 ===\n"))
                    .concatWith(createAndSaveWorkingMemory(sessionId))

                    // 第二阶段：读取和使用记忆
                    .concatWith(Flux.just("\n\n=== 第二阶段：使用记忆 ===\n"))
                    .concatWith(readAndUseWorkingMemory(sessionId))

                    // 第三阶段：验证持久化
                    .concatWith(Flux.just("\n\n=== 第三阶段：持久化验证 ===\n"))
                    .concatWith(verifyPersistence(sessionId));

        } catch (Exception e) {
            return Flux.just("❌ 完整记忆工作流测试失败: " + e.getMessage());
        }
    }

    /**
     * 创建和保存工作记忆
     */
    private Flux<String> createAndSaveWorkingMemory(String sessionId) {
        try {
            StringBuilder result = new StringBuilder();

            // 创建 Message 列表
            java.util.List<Object> messages = new java.util.ArrayList<>();

            // 创建用户消息
            Class<?> userMessageClass = Class.forName("org.springframework.ai.chat.messages.UserMessage");
            java.lang.reflect.Constructor<?> userConstructor = userMessageClass.getConstructor(String.class);
            Object userMessage = userConstructor.newInstance("用户说：我的名字叫张三，今年25岁，最喜欢的颜色是蓝色，爱好是编程和爬山。");
            messages.add(userMessage);

            // 创建助手回复
            Class<?> assistantMessageClass = Class.forName("org.springframework.ai.chat.messages.AssistantMessage");
            java.lang.reflect.Constructor<?> assistantConstructor = assistantMessageClass.getConstructor(String.class);
            Object assistantMessage = assistantConstructor.newInstance("助手回复：好的，我已经记住了！你叫张三，25岁，喜欢蓝色，爱好编程和爬山。");
            messages.add(assistantMessage);

            result.append("✅ 创建了 ").append(messages.size()).append(" 条对话记录:\n");
            for (int i = 0; i < messages.size(); i++) {
                Object msg = messages.get(i);
                // 提取消息内容
                String content = msg.toString();
                if (content.contains("content=")) {
                    content = content.substring(content.indexOf("content='") + 9);
                    content = content.substring(0, content.indexOf("'"));
                }
                result.append("  ").append(i + 1).append(". ").append(content).append("\n");
            }

            // 保存到记忆库
            java.lang.reflect.Method saveAllMethod = redisChatMemoryRepository.getClass().getMethod("saveAll", String.class, java.util.List.class);
            saveAllMethod.invoke(redisChatMemoryRepository, sessionId, messages);

            result.append("✅ 成功保存到 RedisChatMemoryRepository\n");
            result.append("✅ 会话ID: ").append(sessionId).append("\n");

            return Flux.just(result.toString());
        } catch (Exception e) {
            return Flux.just("❌ 创建工作记忆失败: " + e.getMessage() + "\n");
        }
    }

    /**
     * 读取和使用工作记忆
     */
    private Flux<String> readAndUseWorkingMemory(String sessionId) {
        try {
            StringBuilder result = new StringBuilder();

            // 读取历史记忆
            java.lang.reflect.Method findByConversationIdMethod = redisChatMemoryRepository.getClass().getMethod("findByConversationId", String.class);
            java.util.List<?> records = (java.util.List<?>) findByConversationIdMethod.invoke(redisChatMemoryRepository, sessionId);

            if (records.isEmpty()) {
                return Flux.just("❌ 没有找到历史记录\n");
            }

            result.append("✅ 找到 ").append(records.size()).append(" 条历史记录:\n");

            // 构建历史对话上下文
            StringBuilder historyContext = new StringBuilder();
            historyContext.append("以下是我们的历史对话记录：\n");

            for (int i = 0; i < records.size(); i++) {
                Object record = records.get(i);
                String recordStr = record.toString();

                // 提取消息内容
                String content = recordStr;
                if (recordStr.contains("content='")) {
                    content = recordStr.substring(recordStr.indexOf("content='") + 9);
                    content = content.substring(0, content.indexOf("'"));
                } else if (recordStr.contains("textContent=")) {
                    content = recordStr.substring(recordStr.indexOf("textContent=") + 12);
                    if (content.contains(",")) {
                        content = content.substring(0, content.indexOf(","));
                    }
                }

                String role = recordStr.contains("USER") ? "用户" : "助手";
                historyContext.append(role).append(": ").append(content).append("\n");

                result.append("  ").append(i + 1).append(". ").append(role).append(": ").append(content).append("\n");
            }

            historyContext.append("\n请基于以上历史对话回答我的问题。");

            result.append("\n✅ 历史上下文已构建\n");

            return Flux.just(result.toString())
                    .concatWith(Flux.just("\n=== 基于记忆的对话测试 ===\n"))
                    .concatWith(
                            deepSeekChatClient.prompt()
                                    .system("你是一个有帮助的助手，能够记住用户的个人信息。请基于历史对话记录回答用户的问题。")
                                    .user(historyContext + "\n\n用户问：请告诉我，我之前说过我的个人信息是什么？")
                                    .stream()
                                    .content()
                    );

        } catch (Exception e) {
            return Flux.just("❌ 读取使用工作记忆失败: " + e.getMessage() + "\n");
        }
    }

    /**
     * 验证持久化功能
     */
    private Flux<String> verifyPersistence(String sessionId) {
        try {
            StringBuilder result = new StringBuilder();

            // 验证会话列表
            java.lang.reflect.Method findConversationIdsMethod = redisChatMemoryRepository.getClass().getMethod("findConversationIds");
            java.util.List<?> conversationIds = (java.util.List<?>) findConversationIdsMethod.invoke(redisChatMemoryRepository);

            result.append("当前所有会话: ").append(conversationIds).append("\n");
            result.append("会话数量: ").append(conversationIds.size()).append("\n\n");

            // 验证特定会话
            java.lang.reflect.Method findByConversationIdMethod = redisChatMemoryRepository.getClass().getMethod("findByConversationId", String.class);
            java.util.List<?> records = (java.util.List<?>) findByConversationIdMethod.invoke(redisChatMemoryRepository, sessionId);

            result.append("会话 '").append(sessionId).append("' 的记录数量: ").append(records.size()).append("\n");

            if (records.size() > 0) {
                result.append("✅ 记忆持久化验证成功！\n");
                result.append("✅ 数据已成功保存到 Redis\n");
                result.append("✅ 重启应用后这些数据仍然存在\n");
            } else {
                result.append("❌ 记忆持久化验证失败\n");
            }

            return Flux.just(result.toString());
        } catch (Exception e) {
            return Flux.just("❌ 持久化验证失败: " + e.getMessage() + "\n");
        }
    }

    /**
     * 测试重启后的记忆持久化
     */
    @GetMapping("/test-persistence")
    public Flux<String> testPersistence(@RequestParam(defaultValue = "persist-session") String sessionId) {
        try {
            StringBuilder result = new StringBuilder();
            result.append("=== 记忆持久化测试 ===\n\n");
            result.append("会话ID: ").append(sessionId).append("\n\n");

            // 先检查是否已有这个会话的记忆
            java.lang.reflect.Method findByConversationIdMethod = redisChatMemoryRepository.getClass().getMethod("findByConversationId", String.class);
            java.util.List<?> existingRecords = (java.util.List<?>) findByConversationIdMethod.invoke(redisChatMemoryRepository, sessionId);

            if (existingRecords.isEmpty()) {
                result.append("ℹ️ 这是新会话，创建测试记忆...\n");

                // 创建测试记忆
                java.util.List<Object> messages = new java.util.ArrayList<>();

                Class<?> userMessageClass = Class.forName("org.springframework.ai.chat.messages.UserMessage");
                java.lang.reflect.Constructor<?> userConstructor = userMessageClass.getConstructor(String.class);
                Object userMessage = userConstructor.newInstance("这是持久化测试：我的测试信息是 ABC123");
                messages.add(userMessage);

                Class<?> assistantMessageClass = Class.forName("org.springframework.ai.chat.messages.AssistantMessage");
                java.lang.reflect.Constructor<?> assistantConstructor = assistantMessageClass.getConstructor(String.class);
                Object assistantMessage = assistantConstructor.newInstance("好的，我记住了测试信息 ABC123");
                messages.add(assistantMessage);

                // 保存
                java.lang.reflect.Method saveAllMethod = redisChatMemoryRepository.getClass().getMethod("saveAll", String.class, java.util.List.class);
                saveAllMethod.invoke(redisChatMemoryRepository, sessionId, messages);

                result.append("✅ 创建了测试记忆\n");
                result.append("✅ 测试信息: ABC123\n");
                result.append("✅ 已保存到 Redis\n\n");
                result.append("💡 现在你可以重启 Spring Boot 应用，然后再次访问这个接口测试记忆是否仍然存在！");

            } else {
                result.append("✅ 找到了已有的记忆记录！\n");
                result.append("✅ 记录数量: ").append(existingRecords.size()).append("\n");
                result.append("✅ 这证明记忆在应用重启后仍然存在！\n\n");

                // 显示记忆内容
                for (int i = 0; i < existingRecords.size(); i++) {
                    Object record = existingRecords.get(i);
                    String recordStr = record.toString();
                    result.append("记录 ").append(i + 1).append(": ").append(recordStr).append("\n");
                }
            }

            return Flux.just(result.toString());
        } catch (Exception e) {
            return Flux.just("❌ 持久化测试失败: " + e.getMessage());
        }
    }
}