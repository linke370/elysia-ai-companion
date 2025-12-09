// File: src/main/java/com/zs/service/redis/RedisDataInitializer.java
package com.zs.service.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis数据初始化器
 * 应用启动时自动运行，验证Redis功能
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisDataInitializer implements CommandLineRunner {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("🚀 开始初始化Redis测试数据...");

        try {
            // 1. 清除之前的测试数据
            redisTemplate.delete("app:startup:test");

            // 2. 创建测试数据
            Map<String, Object> testData = new HashMap<>();
            testData.put("appName", "Elysia-AI-Companion");
            testData.put("version", "1.0.0");
            testData.put("startupTime", LocalDateTime.now());
            testData.put("status", "running");
            testData.put("testUser", createTestUserData());

            // 3. 保存到Redis
            redisTemplate.opsForValue().set("app:startup:test", testData);

            // 4. 验证保存
            Object retrieved = redisTemplate.opsForValue().get("app:startup:test");
            if (retrieved != null) {
                log.info("✅ Redis初始化成功，数据已保存");
                log.info("📊 测试数据: {}", retrieved);
            } else {
                log.warn("⚠️ Redis初始化警告：数据保存但检索失败");
            }

        } catch (Exception e) {
            log.error("❌ Redis初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("Redis初始化失败，请检查Redis配置和连接", e);
        }
    }

    private Map<String, Object> createTestUserData() {
        Map<String, Object> user = new HashMap<>();
        user.put("id", 999);
        user.put("username", "test_user");
        user.put("testTime", LocalDateTime.now());
        user.put("emotion", "HAPPY");
        user.put("score", 0.85);
        return user;
    }
}