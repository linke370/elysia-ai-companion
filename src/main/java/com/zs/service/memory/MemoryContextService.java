// File: src/main/java/com/zs/service/memory/MemoryContextService.java
package com.zs.service.memory;

import com.zs.entity.MemoryFragments;
import com.zs.service.memory.cache.MemoryCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆上下文服务 - AI背后使用记忆，不显示给用户
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryContextService {

    private final MemoryCacheManager memoryCacheManager;
    private final MemoryExtractionService memoryExtractionService;

    /**
     * 为AI回复构建记忆上下文（不显示给用户）
     */
    public String buildMemoryContext(Long userId, String userMessage) {
        try {
            // 1. 获取相关记忆（从Redis缓存）
            List<MemoryFragments> relevantMemories = memoryCacheManager.getContextMemories(userId, userMessage);

            if (relevantMemories.isEmpty()) {
                log.debug("未找到相关记忆: userId={}", userId);
                return "";
            }

            // 2. 构建系统提示（不显示给用户，只给AI使用）
            StringBuilder context = new StringBuilder();
            context.append("【用户记忆背景】\n");
            context.append("以下是关于这位用户的一些信息（请不要在回复中直接引用，但请基于这些信息调整你的回复风格和内容）：\n\n");

            // 按类型分组
            Map<String, List<MemoryFragments>> groupedByType = relevantMemories.stream()
                    .collect(Collectors.groupingBy(memory ->
                            memory.getMemoryType() != null ? memory.getMemoryType().toString() : "unknown"
                    ));

            for (Map.Entry<String, List<MemoryFragments>> entry : groupedByType.entrySet()) {
                String type = entry.getKey();
                List<MemoryFragments> memories = entry.getValue();

                context.append(type).append(":\n");
                for (int i = 0; i < memories.size() && i < 3; i++) {
                    MemoryFragments memory = memories.get(i);
                    String memoryText = memory.getMemoryText();
                    if (memoryText != null && memoryText.length() > 100) {
                        memoryText = memoryText.substring(0, 100) + "...";
                    }
                    context.append("  - ").append(memoryText != null ? memoryText : "").append("\n");
                }
                context.append("\n");
            }

            // 3. 添加引导说明
            context.append("请基于以上用户背景信息，用更贴切的方式回应用户，但不要直接说'我记得'或提及这些具体信息。\n");

            log.debug("构建记忆上下文: userId={}, memories={}", userId, relevantMemories.size());
            return context.toString();

        } catch (Exception e) {
            log.error("构建记忆上下文失败: userId={}", userId, e);
            return "";
        }
    }

    /**
     * 获取记忆统计数据（用于监控）
     */
    public Map<String, Object> getMemoryUsageStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 从缓存获取活跃记忆 - 需要MemoryCacheManager提供相应方法
            List<MemoryFragments> activeMemories = memoryExtractionService.getUserMemories(userId, null, 30);

            // 计算重要记忆数量
            long importantCount = activeMemories.stream()
                    .filter(memory -> {
                        if (memory.getImportanceScore() != null) {
                            return memory.getImportanceScore().doubleValue() > 0.7;
                        }
                        return false;
                    })
                    .count();

            stats.put("userId", userId);
            stats.put("activeMemoriesCount", activeMemories.size());
            stats.put("importantMemoriesCount", importantCount);
            stats.put("cacheStatus", "active");

        } catch (Exception e) {
            log.error("获取记忆使用统计失败: userId={}", userId, e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 评估记忆使用效果（根据对话反馈）
     */
    public void evaluateMemoryEffectiveness(Long userId, Long conversationId, String aiResponse, String userFeedback) {
        try {
            // 获取本次对话使用的记忆 - 从数据库查询本次对话提取的记忆
            List<MemoryFragments> usedMemories = memoryExtractionService.searchMemories(userId, "", 10);

            if (!usedMemories.isEmpty()) {
                // 根据用户反馈调整记忆重要性
                double feedbackScore = calculateFeedbackScore(userFeedback);

                for (MemoryFragments memory : usedMemories) {
                    // 更新重要性分数
                    if (memory.getImportanceScore() != null) {
                        double currentScore = memory.getImportanceScore().doubleValue();
                        double newScore = currentScore + (feedbackScore * 0.1);
                        newScore = Math.max(0.0, Math.min(1.0, newScore));

                        // 调用现有服务更新
                        memoryExtractionService.updateMemoryImportance(memory.getId(), newScore);

                        // 记录访问
                        memoryExtractionService.recordMemoryAccess(memory.getId(), userId, conversationId);
                    }
                }

                log.debug("评估记忆效果: userId={}, feedback={}, adjusted={} memories",
                        userId, feedbackScore, usedMemories.size());
            }

        } catch (Exception e) {
            log.error("评估记忆效果失败: userId={}", userId, e);
        }
    }

    /**
     * 计算反馈分数
     */
    private double calculateFeedbackScore(String feedback) {
        if (feedback == null) {
            return 0.0;
        }

        switch (feedback.toLowerCase()) {
            case "positive": return 0.5;
            case "negative": return -0.3;
            case "neutral": return 0.1;
            default: return 0.0;
        }
    }
}