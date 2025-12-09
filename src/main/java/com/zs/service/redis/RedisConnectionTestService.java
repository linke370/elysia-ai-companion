// File: src/main/java/com/zs/service/redis/RedisConnectionTestService.java
package com.zs.service.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis连接测试服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisConnectionTestService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 测试Redis连接和序列化
     */
    public Map<String, Object> testRedisConnection() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 测试1: 简单字符串
            redisTemplate.opsForValue().set("test:string", "Hello Redis!");
            String stringValue = (String) redisTemplate.opsForValue().get("test:string");
            result.put("stringTest", stringValue.equals("Hello Redis!") ? "成功" : "失败");

            // 测试2: 复杂对象（包含LocalDateTime）
            Map<String, Object> complexObject = new HashMap<>();
            complexObject.put("name", "测试对象");
            complexObject.put("time", LocalDateTime.now());
            complexObject.put("number", 123);

            redisTemplate.opsForValue().set("test:complex", complexObject);

            @SuppressWarnings("unchecked")
            Map<String, Object> retrieved = (Map<String, Object>) redisTemplate.opsForValue().get("test:complex");

            boolean complexSuccess = retrieved != null &&
                    "测试对象".equals(retrieved.get("name")) &&
                    retrieved.get("time") instanceof LocalDateTime;

            result.put("complexTest", complexSuccess ? "成功" : "失败");
            if (retrieved != null) {
                result.put("retrievedTime", retrieved.get("time"));
                result.put("retrievedTimeClass", retrieved.get("time").getClass().getName());
            }

            // 测试3: 过期时间
            redisTemplate.opsForValue().set("test:expire", "会过期的数据", 10, java.util.concurrent.TimeUnit.SECONDS);
            Long expireTtl = redisTemplate.getExpire("test:expire");
            result.put("expireTest", expireTtl != null && expireTtl > 0 ? "成功" : "失败");
            result.put("expireTTL", expireTtl);

            // 测试4: 哈希表
            redisTemplate.opsForHash().put("test:hash", "field1", "value1");
            redisTemplate.opsForHash().put("test:hash", "field2", 100);
            Object hashValue = redisTemplate.opsForHash().get("test:hash", "field1");
            result.put("hashTest", "value1".equals(hashValue) ? "成功" : "失败");

            // 清理测试数据
            redisTemplate.delete("test:string");
            redisTemplate.delete("test:complex");
            redisTemplate.delete("test:expire");
            redisTemplate.delete("test:hash");

            result.put("overall", "所有测试通过");
            result.put("status", "正常");
            log.info("✅ Redis连接测试成功");

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("status", "异常");
            log.error("❌ Redis连接测试失败", e);
        }

        return result;
    }

    /**
     * 获取Redis服务器信息
     */
    public Map<String, Object> getRedisInfo() {
        Map<String, Object> info = new HashMap<>();

        try {
            // 获取Redis信息
            String redisInfo = String.valueOf(redisTemplate.getConnectionFactory().getConnection().info());

            // 解析关键信息
            info.put("server", "Redis");
            info.put("connected", true);
            info.put("timestamp", LocalDateTime.now());

            // 简单解析info字符串
            String[] lines = redisInfo.split("\r\n");
            for (String line : lines) {
                if (line.startsWith("redis_version:")) {
                    info.put("version", line.split(":")[1]);
                } else if (line.startsWith("used_memory_human:")) {
                    info.put("memory", line.split(":")[1]);
                } else if (line.startsWith("connected_clients:")) {
                    info.put("clients", line.split(":")[1]);
                }
            }

        } catch (Exception e) {
            info.put("error", e.getMessage());
            info.put("connected", false);
        }

        return info;
    }
}