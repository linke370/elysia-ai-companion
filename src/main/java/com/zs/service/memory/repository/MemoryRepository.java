// File: src/main/java/com/zs/service/memory/repository/MemoryRepository.java
package com.zs.service.memory.repository;

import com.zs.entity.MemoryFragments;
import com.zs.mapper.MemoryFragmentsMapper;
import com.zs.service.memory.dto.MemoryStatisticsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆数据访问层
 * 参考EmotionRepository风格
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class MemoryRepository {

    private final MemoryFragmentsMapper memoryFragmentsMapper;

    /**
     * 保存记忆片段
     */
    @Transactional
    public MemoryFragments saveMemoryFragment(MemoryFragments memory) {
        try {
            memoryFragmentsMapper.insert(memory);
            log.debug("保存记忆片段: userId={}, memoryId={}, type={}",
                    memory.getUserId(), memory.getId(), memory.getMemoryType());
            return memory;
        } catch (Exception e) {
            log.error("保存记忆片段失败: userId={}", memory.getUserId(), e);
            throw new RuntimeException("保存记忆片段失败", e);
        }
    }

    /**
     * 批量保存记忆片段
     */
    @Transactional
    public void batchSaveMemoryFragments(List<MemoryFragments> memories) {
        for (MemoryFragments memory : memories) {
            saveMemoryFragment(memory);
        }
    }

    /**
     * 查询用户记忆
     */
    public List<MemoryFragments> findUserMemories(Long userId, String memoryType, Integer limit) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments>()
                            .eq("user_id", userId)
                            .orderByDesc("importance_score", "last_accessed", "created_at");

            if (memoryType != null) {
                wrapper.eq("memory_type", memoryType);
            }

            if (limit != null) {
                wrapper.last("LIMIT " + limit);
            }

            return memoryFragmentsMapper.selectList(wrapper);

        } catch (Exception e) {
            log.error("查询用户记忆失败: userId={}, type={}", userId, memoryType, e);
            return List.of();
        }
    }

    /**
     * 搜索记忆
     */
    public List<MemoryFragments> searchMemories(Long userId, String keyword, Integer limit) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments>()
                            .eq("user_id", userId)
                            .like("memory_text", keyword)
                            .orderByDesc("importance_score", "last_accessed")
                            .last("LIMIT " + limit);

            return memoryFragmentsMapper.selectList(wrapper);

        } catch (Exception e) {
            log.error("搜索记忆失败: userId={}, keyword={}", userId, keyword, e);
            return List.of();
        }
    }

    /**
     * 获取记忆统计
     */
    public MemoryStatisticsDTO getMemoryStatistics(Long userId) {
        try {
            // 获取所有记忆
            List<MemoryFragments> allMemories = memoryFragmentsMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments>()
                            .eq("user_id", userId)
            );

            if (allMemories.isEmpty()) {
                return MemoryStatisticsDTO.builder()
                        .userId(userId)
                        .totalMemories(0)
                        .totalImportantMemories(0)
                        .importantMemoryRatio(0.0)
                        .recentMemoryCount(0)
                        .build();
            }

            // 计算类型分布
            Map<String, Integer> typeDistribution = new HashMap<>();
            int importantCount = 0;
            int recentCount = 0; // 这里简化处理，实际可以根据时间判断

            for (MemoryFragments memory : allMemories) {
                // 类型分布
                String type = memory.getMemoryType() != null ? memory.getMemoryType().toString() : "unknown";
                typeDistribution.put(type, typeDistribution.getOrDefault(type, 0) + 1);

                // 重要记忆计数
                BigDecimal importance = memory.getImportanceScore();
                if (importance != null && importance.doubleValue() > 0.7) {
                    importantCount++;
                }
            }

            // 计算重要记忆比例
            double importantRatio = (double) importantCount / allMemories.size();

            return MemoryStatisticsDTO.builder()
                    .userId(userId)
                    .totalMemories(allMemories.size())
                    .totalImportantMemories(importantCount)
                    .typeDistribution(typeDistribution)
                    .importantMemoryRatio(importantRatio)
                    .recentMemoryCount(recentCount)
                    .build();

        } catch (Exception e) {
            log.error("获取记忆统计失败: userId={}", userId, e);
            return MemoryStatisticsDTO.builder()
                    .userId(userId)
                    .totalMemories(0)
                    .build();
        }
    }

    /**
     * 更新记忆访问次数
     */
    @Transactional
    public void updateMemoryAccess(Long memoryId, Integer accessCount, java.util.Date lastAccessed) {
        try {
            MemoryFragments memory = memoryFragmentsMapper.selectById(memoryId);
            if (memory != null) {
                memory.setAccessCount(accessCount);
                memory.setLastAccessed(lastAccessed);
                memory.setUpdatedAt(new java.util.Date());
                memoryFragmentsMapper.updateById(memory);
            }
        } catch (Exception e) {
            log.error("更新记忆访问失败: memoryId={}", memoryId, e);
        }
    }
}