package com.zs.controller.test;

import com.zs.service.chat.thinking.ThinkingService;
import com.zs.service.emotion.state.AIEmotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器（专门测试新功能）
 * 新增类，不修改现有代码
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ThinkingService thinkingService;
    private final AIEmotionService aiEmotionService;

    /**
     * 测试Redis连接
     */
    @GetMapping("/redis")
    public ResponseEntity<Map<String, Object>> testRedis() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 测试Redis连接
            redisTemplate.opsForValue().set("test:connection", "成功连接Redis！");
            String value = (String) redisTemplate.opsForValue().get("test:connection");

            result.put("success", true);
            result.put("message", value);
            result.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 测试思考过程
     */
    @GetMapping("/thinking/{userId}")
    public ResponseEntity<Map<String, Object>> testThinking(
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "你好") String message) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 生成思考过程
            var thinkingProcess = thinkingService.generateThinkingProcess(
                    userId, message, "HAPPY", 3);

            result.put("success", true);
            result.put("userId", userId);
            result.put("message", message);
            result.put("thinkingSteps", thinkingProcess.size());
            result.put("thinkingProcess", thinkingProcess);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 测试AI情感状态
     */
    @GetMapping("/ai-emotion/{userId}")
    public ResponseEntity<Map<String, Object>> testAIEmotion(
            @PathVariable Long userId) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 获取AI情感报告
            var report = aiEmotionService.getAIEmotionReport(userId);

            result.put("success", true);
            result.put("userId", userId);
            result.put("report", report);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 更新AI情感状态
     */
    @PostMapping("/ai-emotion/{userId}")
    public ResponseEntity<Map<String, Object>> updateAIEmotion(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "HAPPY") String userEmotion,
            @RequestParam(defaultValue = "0.7") Double intensity) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 更新AI情感状态
            aiEmotionService.updateAIEmotion(userId, userEmotion, intensity);

            // 获取更新后的状态
            var report = aiEmotionService.getAIEmotionReport(userId);

            result.put("success", true);
            result.put("userId", userId);
            result.put("userEmotion", userEmotion);
            result.put("intensity", intensity);
            result.put("aiEmotionReport", report);
            result.put("message", "AI情感状态已更新");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 测试所有新功能
     */
    @GetMapping("/all/{userId}")
    public ResponseEntity<Map<String, Object>> testAllFeatures(
            @PathVariable Long userId) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 测试Redis
            redisTemplate.opsForValue().set("test:all:" + userId, "测试通过");
            String redisTest = (String) redisTemplate.opsForValue().get("test:all:" + userId);

            // 2. 测试思考过程
            var thinkingProcess = thinkingService.generateThinkingProcess(
                    userId, "综合测试消息", "NEUTRAL", 2);

            // 3. 测试AI情感状态
            var aiEmotionReport = aiEmotionService.getAIEmotionReport(userId);

            result.put("success", true);
            result.put("userId", userId);
            result.put("redisTest", redisTest != null ? "通过" : "失败");
            result.put("thinkingService", thinkingProcess.size() > 0 ? "正常" : "异常");
            result.put("aiEmotionService", aiEmotionReport.containsKey("currentState") ? "正常" : "异常");
            result.put("timestamp", LocalDateTime.now());
            result.put("message", "所有新功能测试完成");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}