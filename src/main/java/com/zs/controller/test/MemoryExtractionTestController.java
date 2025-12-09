// File: src/main/java/com/zs/controller/test/MemoryExtractionTestController.java
package com.zs.controller.test;

import com.zs.entity.Conversations;
import com.zs.entity.MemoryFragments;
import com.zs.mapper.ConversationsMapper;
import com.zs.service.memory.MemoryExtractionService;
import com.zs.vo.ResultVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 记忆提取服务测试控制器 - 增强版（适配MemoryExtractionService增强功能）
 */
@RestController
@RequestMapping("/api/test/memory")
@Slf4j
public class MemoryExtractionTestController {

    @Resource
    private MemoryExtractionService memoryExtractionService;

    @Resource
    private ConversationsMapper conversationsMapper;

    // ========== 基础功能测试 ==========

    /**
     * 测试服务健康状态（增强版）
     */
    @GetMapping("/health")
    public ResultVO<Map<String, Object>> healthCheck() {
        try {
            log.info("测试记忆提取服务健康状态（增强版）");
            Map<String, Object> health = memoryExtractionService.healthCheck();
            return ResultVO.success("服务健康检查完成（增强版）", health);
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return ResultVO.error("健康检查失败: " + e.getMessage());
        }
    }

    /**
     * 测试服务统计信息（增强版）
     */
    @GetMapping("/stats")
    public ResultVO<Map<String, Object>> getServiceStats() {
        try {
            log.info("获取记忆提取服务统计信息（增强版）");
            Map<String, Object> stats = memoryExtractionService.getServiceStats();
            return ResultVO.success("服务统计获取成功（增强版）", stats);
        } catch (Exception e) {
            log.error("获取统计失败", e);
            return ResultVO.error("获取统计失败: " + e.getMessage());
        }
    }

    // ========== 记忆提取测试（增强功能）==========

    /**
     * 测试增强版记忆提取（带Redis缓存）
     */
    @PostMapping("/extract/enhanced/{conversationId}")
    public ResultVO<Map<String, Object>> testEnhancedExtractFromConversation(
            @PathVariable Long conversationId) {
        try {
            log.info("测试增强版记忆提取（带缓存）: conversationId={}", conversationId);

            // 检查对话是否存在
            Conversations conversation = conversationsMapper.selectById(conversationId);
            if (conversation == null) {
                return ResultVO.error("对话不存在: " + conversationId);
            }

            // 使用增强版方法提取记忆（带Redis缓存）
            List<MemoryFragments> memories = memoryExtractionService
                    .extractMemoriesWithCache(conversationId);

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("memories", memories);
            responseData.put("conversationId", conversationId);
            responseData.put("userId", conversation.getUserId());
            responseData.put("userMessage", conversation.getUserMessage());
            responseData.put("extractedCount", memories.size());
            responseData.put("method", "enhanced_with_cache");

            return ResultVO.success(
                    String.format("成功提取 %d 个记忆片段（增强版）", memories.size()),
                    responseData
            );

        } catch (Exception e) {
            log.error("增强版提取记忆测试失败: conversationId={}", conversationId, e);
            return ResultVO.error("增强版提取记忆失败: " + e.getMessage());
        }
    }

    /**
     * 测试获取上下文相关记忆（AI对话使用）
     */
    @GetMapping("/context/{userId}")
    public ResultVO<Map<String, Object>> testGetContextualMemories(
            @PathVariable Long userId,
            @RequestParam String message) {
        try {
            log.info("测试获取上下文相关记忆: userId={}, message={}", userId, message);

            // 使用增强方法获取上下文相关记忆
            List<MemoryFragments> memories = memoryExtractionService
                    .getContextualMemories(userId, message);

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("memories", memories);
            responseData.put("userId", userId);
            responseData.put("searchMessage", message);
            responseData.put("foundCount", memories.size());
            responseData.put("method", "contextual_search");

            // 如果有找到记忆，展示相关性信息
            if (!memories.isEmpty()) {
                List<Map<String, Object>> memoryDetails = new ArrayList<>();
                for (MemoryFragments memory : memories) {
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("id", memory.getId());
                    detail.put("type", memory.getMemoryType());
                    detail.put("content", memory.getMemoryText());
                    detail.put("importance", memory.getImportanceScore());
                    memoryDetails.add(detail);
                }
                responseData.put("memoryDetails", memoryDetails);
            }

            return ResultVO.success(
                    String.format("找到 %d 个上下文相关记忆", memories.size()),
                    responseData
            );

        } catch (Exception e) {
            log.error("获取上下文记忆测试失败: userId={}", userId, e);
            return ResultVO.error("获取上下文记忆失败: " + e.getMessage());
        }
    }

    /**
     * 测试Redis缓存功能
     */
    @GetMapping("/cache/{userId}")
    public ResultVO<Map<String, Object>> testRedisCache(@PathVariable Long userId) {
        try {
            log.info("测试Redis缓存功能: userId={}", userId);

            Map<String, Object> cacheStats = new LinkedHashMap<>();

            // 1. 获取增强版缓存统计
            Map<String, Object> enhancedStats = memoryExtractionService.getCacheStats();
            cacheStats.put("enhancedCacheStats", enhancedStats);

            // 2. 测试获取活跃记忆（从Redis）
            // 这里需要模拟一个消息来测试上下文缓存
            String testMessage = "测试消息";
            List<MemoryFragments> contextualMemories = memoryExtractionService
                    .getContextualMemories(userId, testMessage);

            cacheStats.put("contextualMemoriesFromCache", contextualMemories.size());
            cacheStats.put("testMessage", testMessage);
            cacheStats.put("userId", userId);

            // 3. 检查Redis连接状态（通过健康检查）
            Map<String, Object> health = memoryExtractionService.healthCheck();
            cacheStats.put("redisStatus", health.get("redis"));
            cacheStats.put("serviceVersion", health.get("version"));

            return ResultVO.success("Redis缓存功能测试完成", cacheStats);

        } catch (Exception e) {
            log.error("Redis缓存测试失败: userId={}", userId, e);
            return ResultVO.error("Redis缓存测试失败: " + e.getMessage());
        }
    }

    // ========== 记忆限制测试 ==========

    /**
     * 测试记忆限制功能
     */
    @GetMapping("/limit-test/{userId}")
    public ResultVO<Map<String, Object>> testMemoryLimits(@PathVariable Long userId) {
        try {
            log.info("测试记忆限制功能: userId={}", userId);

            Map<String, Object> limitInfo = new LinkedHashMap<>();

            // 1. 获取当前记忆总数
            Map<String, Object> stats = memoryExtractionService.getMemoryStatistics(userId);
            limitInfo.put("currentMemoryCount", stats.get("totalMemories"));
            limitInfo.put("typeDistribution", stats.get("typeCounts"));

            // 2. 获取重要记忆数量
            long importantCount = memoryExtractionService.getUserMemories(userId, null, 100)
                    .stream()
                    .filter(memory -> {
                        if (memory.getImportanceScore() != null) {
                            return memory.getImportanceScore().doubleValue() > 0.7;
                        }
                        return false;
                    })
                    .count();

            limitInfo.put("importantMemoriesCount", importantCount);

            // 3. 测试获取最近记忆
            List<MemoryFragments> recentMemories = memoryExtractionService.getUserMemories(userId, null, 10);
            limitInfo.put("recentMemoriesCount", recentMemories.size());

            // 4. 显示限制配置
            Map<String, Integer> limits = new HashMap<>();
            limits.put("MAX_MEMORIES_PER_USER", 100); // 从服务中获取，这里写死示例值
            limits.put("MAX_CACHED_MEMORIES", 30);
            limits.put("MAX_REDIS_MEMORIES", 50);
            limitInfo.put("configuredLimits", limits);

            return ResultVO.success("记忆限制测试完成", limitInfo);

        } catch (Exception e) {
            log.error("记忆限制测试失败: userId={}", userId, e);
            return ResultVO.error("记忆限制测试失败: " + e.getMessage());
        }
    }

    // ========== 模拟AI对话记忆使用测试 ==========

    /**
     * 模拟AI对话中使用记忆（不显示给用户）
     */
    @PostMapping("/simulate-ai-chat")
    public ResultVO<Map<String, Object>> simulateAiChatWithMemory(
            @RequestParam Long userId,
            @RequestParam String userMessage) {
        try {
            log.info("模拟AI对话使用记忆: userId={}, message={}", userId, userMessage);

            Map<String, Object> simulation = new LinkedHashMap<>();
            simulation.put("userId", userId);
            simulation.put("userMessage", userMessage);
            simulation.put("simulationTime", new Date());

            // 1. AI内部获取相关记忆（不显示给用户）
            List<MemoryFragments> relevantMemories = memoryExtractionService
                    .getContextualMemories(userId, userMessage);

            simulation.put("internalMemoriesFound", relevantMemories.size());
            simulation.put("internalMemories", relevantMemories);

            // 2. 模拟AI生成回复（基于记忆但不提及记忆）
            String aiResponse = generateAiResponse(userMessage, relevantMemories);
            simulation.put("aiResponse", aiResponse);
            simulation.put("responseBasedOnMemory", !relevantMemories.isEmpty());

            // 3. 如果是新信息，触发记忆提取
            if (containsNewInformation(userMessage)) {
                // 创建临时对话记录用于测试
                Conversations tempConversation = new Conversations();
                tempConversation.setUserId(userId);
                tempConversation.setUserMessage(userMessage);
                tempConversation.setAiResponse(aiResponse);
                tempConversation.setEmotionLabel("NEUTRAL");
                tempConversation.setEmotionConfidence(new java.math.BigDecimal("0.5"));
                tempConversation.setIsMeaningful(1);

                conversationsMapper.insert(tempConversation);

                // 提取记忆
                List<MemoryFragments> extracted = memoryExtractionService
                        .extractMemoriesWithCache(tempConversation.getId());

                simulation.put("newMemoriesExtracted", extracted.size());
                simulation.put("tempConversationId", tempConversation.getId());
            }

            simulation.put("status", "success");
            return ResultVO.success("AI对话记忆模拟完成", simulation);

        } catch (Exception e) {
            log.error("模拟AI对话失败: userId={}", userId, e);
            return ResultVO.error("模拟AI对话失败: " + e.getMessage());
        }
    }

    // ========== 批量性能测试 ==========

    /**
     * 批量测试记忆系统性能
     */
    @PostMapping("/performance/{userId}")
    public ResultVO<Map<String, Object>> performanceTest(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int testCount) {
        try {
            log.info("批量性能测试: userId={}, testCount={}", userId, testCount);

            Map<String, Object> performance = new LinkedHashMap<>();
            performance.put("userId", userId);
            performance.put("testCount", testCount);
            performance.put("startTime", new Date());

            List<Map<String, Object>> testResults = new ArrayList<>();

            // 测试不同类型的消息
            String[] testMessages = {
                    "我叫张三，今年20岁",
                    "我喜欢吃草莓和巧克力",
                    "我昨天考试考得很好",
                    "我经常在压力大的时候想吃甜食",
                    "我是计算机专业的学生",
                    "我讨厌早起上课",
                    "我上周去旅行了",
                    "我一紧张就会胃痛",
                    "我的生日是5月20日",
                    "我总是周末去图书馆学习"
            };

            int successfulTests = 0;
            long totalResponseTime = 0;

            for (int i = 0; i < Math.min(testCount, testMessages.length); i++) {
                try {
                    String message = testMessages[i];
                    long startTime = System.currentTimeMillis();

                    // 测试上下文记忆获取
                    List<MemoryFragments> memories = memoryExtractionService
                            .getContextualMemories(userId, message);

                    long responseTime = System.currentTimeMillis() - startTime;

                    Map<String, Object> result = new HashMap<>();
                    result.put("testIndex", i + 1);
                    result.put("message", message);
                    result.put("memoriesFound", memories.size());
                    result.put("responseTimeMs", responseTime);
                    result.put("status", "success");

                    testResults.add(result);
                    successfulTests++;
                    totalResponseTime += responseTime;

                } catch (Exception e) {
                    log.warn("单个测试失败: index={}", i, e);
                }
            }

            performance.put("testResults", testResults);
            performance.put("successfulTests", successfulTests);
            performance.put("failedTests", testCount - successfulTests);
            performance.put("averageResponseTimeMs",
                    successfulTests > 0 ? totalResponseTime / successfulTests : 0);
            performance.put("endTime", new Date());

            return ResultVO.success("性能测试完成", performance);

        } catch (Exception e) {
            log.error("性能测试失败: userId={}", userId, e);
            return ResultVO.error("性能测试失败: " + e.getMessage());
        }
    }

    // ========== 原有方法保持不变 ==========

    /**
     * 测试从指定对话提取记忆（原有方法）
     */
    @PostMapping("/extract/{conversationId}")
    public ResultVO<Map<String, Object>> testExtractFromConversation(
            @PathVariable Long conversationId) {
        try {
            log.info("测试从对话提取记忆: conversationId={}", conversationId);

            // 检查对话是否存在
            Conversations conversation = conversationsMapper.selectById(conversationId);
            if (conversation == null) {
                return ResultVO.error("对话不存在: " + conversationId);
            }

            // 提取记忆
            List<MemoryFragments> memories = memoryExtractionService
                    .extractMemoriesFromConversation(conversationId);

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("memories", memories);
            responseData.put("conversationId", conversationId);
            responseData.put("userId", conversation.getUserId());
            responseData.put("userMessage", conversation.getUserMessage());
            responseData.put("extractedCount", memories.size());

            return ResultVO.success(
                    String.format("成功提取 %d 个记忆片段", memories.size()),
                    responseData
            );

        } catch (Exception e) {
            log.error("提取记忆测试失败: conversationId={}", conversationId, e);
            return ResultVO.error("提取记忆失败: " + e.getMessage());
        }
    }

    /**
     * 测试异步提取记忆（原有方法）
     */
    @PostMapping("/extract/async/{conversationId}")
    public ResultVO<String> testAsyncExtract(@PathVariable Long conversationId) {
        try {
            log.info("测试异步提取记忆: conversationId={}", conversationId);

            // 检查对话是否存在
            Conversations conversation = conversationsMapper.selectById(conversationId);
            if (conversation == null) {
                return ResultVO.error("对话不存在: " + conversationId);
            }

            // 异步提取记忆
            memoryExtractionService.extractMemoriesAsync(conversationId);

            return ResultVO.success(
                    String.format("对话 %d 的记忆提取任务已提交，请稍后查询结果", conversationId)
            );

        } catch (Exception e) {
            log.error("异步提取测试失败: conversationId={}", conversationId, e);
            return ResultVO.error("异步提取失败: " + e.getMessage());
        }
    }

    /**
     * 测试批量提取历史记忆（原有方法）
     */
    @PostMapping("/extract/historical/{userId}")
    public ResultVO<Map<String, Object>> testExtractHistorical(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            log.info("测试批量提取历史记忆: userId={}, limit={}", userId, limit);

            List<MemoryFragments> memories = memoryExtractionService
                    .extractHistoricalMemories(userId, limit);

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("memories", memories);
            responseData.put("userId", userId);
            responseData.put("limit", limit);
            responseData.put("extractedCount", memories.size());
            responseData.put("uniqueTypes", getUniqueMemoryTypes(memories));

            return ResultVO.success(
                    String.format("成功从历史对话提取 %d 个记忆片段", memories.size()),
                    responseData
            );

        } catch (Exception e) {
            log.error("提取历史记忆测试失败: userId={}", userId, e);
            return ResultVO.error("提取历史记忆失败: " + e.getMessage());
        }
    }

    /**
     * 测试获取用户记忆（原有方法）
     */
    @GetMapping("/user/{userId}")
    public ResultVO<Map<String, Object>> testGetUserMemories(
            @PathVariable Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            log.info("测试获取用户记忆: userId={}, type={}, limit={}", userId, type, limit);

            MemoryExtractionService.MemoryType memoryType = null;
            if (type != null && !type.isEmpty()) {
                try {
                    memoryType = MemoryExtractionService.MemoryType.fromCode(type);
                } catch (Exception e) {
                    return ResultVO.error("无效的记忆类型: " + type);
                }
            }

            List<MemoryFragments> memories = memoryExtractionService
                    .getUserMemories(userId, memoryType, limit);

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("memories", memories);
            responseData.put("userId", userId);
            responseData.put("requestedType", type);
            responseData.put("actualType", memoryType != null ? memoryType.getCode() : "all");
            responseData.put("limit", limit);
            responseData.put("foundCount", memories.size());
            responseData.put("importanceStats", calculateImportanceStats(memories));

            return ResultVO.success(
                    String.format("找到 %d 个记忆片段", memories.size()),
                    responseData
            );

        } catch (Exception e) {
            log.error("获取用户记忆测试失败: userId={}", userId, e);
            return ResultVO.error("获取用户记忆失败: " + e.getMessage());
        }
    }

    /**
     * 测试搜索记忆（原有方法）
     */
    @GetMapping("/search/{userId}")
    public ResultVO<Map<String, Object>> testSearchMemories(
            @PathVariable Long userId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            log.info("测试搜索记忆: userId={}, keyword={}, limit={}", userId, keyword, limit);

            List<MemoryFragments> memories = memoryExtractionService
                    .searchMemories(userId, keyword, limit);

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("memories", memories);
            responseData.put("userId", userId);
            responseData.put("keyword", keyword);
            responseData.put("limit", limit);
            responseData.put("foundCount", memories.size());

            return ResultVO.success(
                    String.format("找到 %d 个相关记忆", memories.size()),
                    responseData
            );

        } catch (Exception e) {
            log.error("搜索记忆测试失败: userId={}, keyword={}", userId, keyword, e);
            return ResultVO.error("搜索记忆失败: " + e.getMessage());
        }
    }

    /**
     * 测试获取记忆统计（原有方法）
     */
    @GetMapping("/stats/{userId}")
    public ResultVO<Map<String, Object>> testGetMemoryStats(@PathVariable Long userId) {
        try {
            log.info("测试获取记忆统计: userId={}", userId);

            Map<String, Object> stats = memoryExtractionService.getMemoryStatistics(userId);

            return ResultVO.success("记忆统计获取成功", stats);

        } catch (Exception e) {
            log.error("获取记忆统计测试失败: userId={}", userId, e);
            return ResultVO.error("获取记忆统计失败: " + e.getMessage());
        }
    }

    /**
     * 测试更新记忆重要性（原有方法）
     */
    @PutMapping("/importance/{memoryId}")
    public ResultVO<String> testUpdateImportance(
            @PathVariable Long memoryId,
            @RequestParam Double importanceScore) {
        try {
            log.info("测试更新记忆重要性: memoryId={}, score={}", memoryId, importanceScore);

            if (importanceScore < 0.0 || importanceScore > 1.0) {
                return ResultVO.error("重要性评分必须在0.0到1.0之间");
            }

            memoryExtractionService.updateMemoryImportance(memoryId, importanceScore);

            return ResultVO.success(
                    String.format("记忆 %d 的重要性已更新为 %.2f", memoryId, importanceScore)
            );

        } catch (Exception e) {
            log.error("更新记忆重要性测试失败: memoryId={}", memoryId, e);
            return ResultVO.error("更新记忆重要性失败: " + e.getMessage());
        }
    }

    /**
     * 测试记录记忆访问（原有方法）
     */
    @PostMapping("/access/{memoryId}")
    public ResultVO<String> testRecordAccess(
            @PathVariable Long memoryId,
            @RequestParam Long userId,
            @RequestParam Long conversationId) {
        try {
            log.info("测试记录记忆访问: memoryId={}, userId={}, conversationId={}",
                    memoryId, userId, conversationId);

            memoryExtractionService.recordMemoryAccess(memoryId, userId, conversationId);

            return ResultVO.success(
                    String.format("记忆 %d 的访问已记录", memoryId)
            );

        } catch (Exception e) {
            log.error("记录记忆访问测试失败: memoryId={}", memoryId, e);
            return ResultVO.error("记录记忆访问失败: " + e.getMessage());
        }
    }

    /**
     * 测试获取缓存统计（原有方法）
     */
    @GetMapping("/cache/stats")
    public ResultVO<Map<String, Object>> testGetCacheStats() {
        try {
            log.info("测试获取缓存统计");

            Map<String, Object> cacheStats = memoryExtractionService.getCacheStats();

            return ResultVO.success("缓存统计获取成功", cacheStats);

        } catch (Exception e) {
            log.error("获取缓存统计测试失败", e);
            return ResultVO.error("获取缓存统计失败: " + e.getMessage());
        }
    }

    /**
     * 测试清空用户缓存（原有方法）
     */
    @DeleteMapping("/cache/{userId}")
    public ResultVO<String> testClearUserCache(@PathVariable Long userId) {
        try {
            log.info("测试清空用户缓存: userId={}", userId);

            memoryExtractionService.clearUserMemoryCache(userId);

            return ResultVO.success(
                    String.format("用户 %d 的记忆缓存已清空", userId)
            );

        } catch (Exception e) {
            log.error("清空用户缓存测试失败: userId={}", userId, e);
            return ResultVO.error("清空用户缓存失败: " + e.getMessage());
        }
    }

    /**
     * 完整功能测试（原有方法）
     */
    @PostMapping("/full-test/{userId}")
    public ResultVO<Map<String, Object>> runFullTest(@PathVariable Long userId) {
        try {
            log.info("开始完整功能测试（增强版）: userId={}", userId);

            Map<String, Object> testResults = new LinkedHashMap<>();

            // 1. 健康检查（增强版）
            testResults.put("healthCheck", memoryExtractionService.healthCheck());

            // 2. 服务统计（增强版）
            testResults.put("serviceStats", memoryExtractionService.getServiceStats());

            // 3. 获取用户最近对话
            List<Conversations> recentConversations = conversationsMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Conversations>()
                            .eq("user_id", userId)
                            .orderByDesc("created_at")
                            .last("LIMIT 3")
            );

            testResults.put("recentConversations", recentConversations.size());

            // 4. 如果有关联对话，测试记忆提取（增强版和原版）
            if (!recentConversations.isEmpty()) {
                Long conversationId = recentConversations.get(0).getId();

                // 原版提取
                List<MemoryFragments> extracted = memoryExtractionService
                        .extractMemoriesFromConversation(conversationId);
                testResults.put("extractedFromRecent_original", extracted.size());

                // 增强版提取（带缓存）
                List<MemoryFragments> extractedEnhanced = memoryExtractionService
                        .extractMemoriesWithCache(conversationId);
                testResults.put("extractedFromRecent_enhanced", extractedEnhanced.size());
            }

            // 5. 测试获取记忆（原版）
            List<MemoryFragments> memories = memoryExtractionService
                    .getUserMemories(userId, null, 5);
            testResults.put("retrievedMemories_original", memories.size());

            // 6. 测试上下文记忆（增强版）
            List<MemoryFragments> contextual = memoryExtractionService
                    .getContextualMemories(userId, "测试");
            testResults.put("retrievedMemories_contextual", contextual.size());

            // 7. 测试记忆统计
            testResults.put("memoryStatistics", memoryExtractionService.getMemoryStatistics(userId));

            // 8. 测试缓存（增强版）
            testResults.put("cacheStats", memoryExtractionService.getCacheStats());

            testResults.put("testStatus", "passed");
            testResults.put("timestamp", new Date());
            testResults.put("userId", userId);
            testResults.put("version", "enhanced");

            return ResultVO.success("完整功能测试完成（增强版）", testResults);

        } catch (Exception e) {
            log.error("完整功能测试失败: userId={}", userId, e);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("testStatus", "failed");
            errorResult.put("error", e.getMessage());
            errorResult.put("userId", userId);
            errorResult.put("version", "enhanced");

            return ResultVO.error("测试过程中出现错误", errorResult);
        }
    }

    /**
     * 创建测试对话并提取记忆（模拟真实场景）（原有方法）
     */
    @PostMapping("/simulate/{userId}")
    public ResultVO<Map<String, Object>> simulateMemoryExtraction(
            @PathVariable Long userId,
            @RequestParam String userMessage) {
        try {
            log.info("模拟记忆提取: userId={}, message={}", userId, userMessage);

            Map<String, Object> simulationResult = new LinkedHashMap<>();
            simulationResult.put("userId", userId);
            simulationResult.put("userMessage", userMessage);
            simulationResult.put("simulationStartTime", new Date());

            // 1. 创建测试对话记录
            Conversations testConversation = new Conversations();
            testConversation.setUserId(userId);
            testConversation.setUserMessage(userMessage);
            testConversation.setAiResponse("这是AI的测试回复");
            testConversation.setEmotionLabel("NEUTRAL");
            testConversation.setEmotionConfidence(new java.math.BigDecimal("0.5"));
            testConversation.setConversationContext("test_simulation");
            testConversation.setIsMeaningful(1);

            conversationsMapper.insert(testConversation);

            simulationResult.put("createdConversationId", testConversation.getId());
            simulationResult.put("conversationCreated", true);

            // 2. 提取记忆（增强版）
            List<MemoryFragments> extractedMemories = memoryExtractionService
                    .extractMemoriesWithCache(testConversation.getId());

            simulationResult.put("extractedMemoriesCount", extractedMemories.size());
            simulationResult.put("extractedMemories", extractedMemories);

            // 3. 查询验证（原版）
            List<MemoryFragments> retrievedMemories = memoryExtractionService
                    .getUserMemories(userId, null, 10);

            simulationResult.put("retrievedMemoriesCount", retrievedMemories.size());

            // 4. 测试上下文记忆（增强版）
            List<MemoryFragments> contextualMemories = memoryExtractionService
                    .getContextualMemories(userId, userMessage);
            simulationResult.put("contextualMemoriesCount", contextualMemories.size());

            simulationResult.put("simulationEndTime", new Date());
            simulationResult.put("status", "success");
            simulationResult.put("version", "enhanced");

            return ResultVO.success("模拟记忆提取完成（增强版）", simulationResult);

        } catch (Exception e) {
            log.error("模拟记忆提取失败: userId={}", userId, e);
            return ResultVO.error("模拟记忆提取失败: " + e.getMessage());
        }
    }

    /**
     * 测试服务整体功能（原有方法）
     */
    @GetMapping("/test/{userId}")
    public ResultVO<Map<String, Object>> testService(@PathVariable Long userId) {
        try {
            log.info("测试记忆提取服务（增强版）: userId={}", userId);

            Map<String, Object> testResult = memoryExtractionService.testService(userId);

            return ResultVO.success("服务测试完成（增强版）", testResult);

        } catch (Exception e) {
            log.error("服务测试失败: userId={}", userId, e);
            return ResultVO.error("服务测试失败: " + e.getMessage());
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 获取记忆的唯一类型
     */
    private Set<String> getUniqueMemoryTypes(List<MemoryFragments> memories) {
        Set<String> types = new HashSet<>();
        for (MemoryFragments memory : memories) {
            Object type = memory.getMemoryType();
            if (type != null) {
                types.add(type.toString());
            }
        }
        return types;
    }

    /**
     * 计算记忆重要性统计
     */
    private Map<String, Object> calculateImportanceStats(List<MemoryFragments> memories) {
        Map<String, Object> stats = new HashMap<>();

        if (memories.isEmpty()) {
            stats.put("total", 0);
            stats.put("averageImportance", 0.0);
            stats.put("importantCount", 0);
            return stats;
        }

        double totalImportance = 0.0;
        int importantCount = 0;

        for (MemoryFragments memory : memories) {
            java.math.BigDecimal importance = memory.getImportanceScore();
            if (importance != null) {
                double value = importance.doubleValue();
                totalImportance += value;

                if (value > 0.7) {
                    importantCount++;
                }
            }
        }

        stats.put("total", memories.size());
        stats.put("averageImportance", String.format("%.2f", totalImportance / memories.size()));
        stats.put("importantCount", importantCount);
        stats.put("importantPercentage",
                String.format("%.1f%%", (double) importantCount / memories.size() * 100));

        return stats;
    }

    /**
     * 模拟AI生成回复（基于记忆）
     */
    private String generateAiResponse(String userMessage, List<MemoryFragments> memories) {
        // 简单模拟AI回复
        StringBuilder response = new StringBuilder();

        if (!memories.isEmpty()) {
            // 基于记忆生成个性化回复（但不提及记忆内容）
            response.append("了解啦～ ");

            // 根据记忆数量调整语气
            if (memories.size() > 3) {
                response.append("根据我们之前的交流，");
            } else if (memories.size() > 0) {
                response.append("考虑到你的情况，");
            }
        } else {
            response.append("好的，");
        }

        // 简单回应
        if (userMessage.contains("喜欢") || userMessage.contains("爱")) {
            response.append("很高兴知道你的喜好呢～");
        } else if (userMessage.contains("讨厌") || userMessage.contains("不喜欢")) {
            response.append("明白了，我会记住的。");
        } else if (userMessage.contains("考试") || userMessage.contains("学习")) {
            response.append("学习方面的事情要加油哦！");
        } else {
            response.append("我明白你的意思了。");
        }

        return response.toString();
    }

    /**
     * 检查是否包含新信息（简单判断）
     */
    private boolean containsNewInformation(String message) {
        // 简单判断：包含特定关键词则认为有新信息
        String[] newInfoKeywords = {"我叫", "我喜欢", "我讨厌", "我昨天", "我上周", "我经常"};
        for (String keyword : newInfoKeywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}