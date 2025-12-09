// File: src/main/java/com/zs/service/memory/dto/MemoryStatisticsDTO.java
package com.zs.service.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 记忆统计数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryStatisticsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private Integer totalMemories;
    private Integer totalImportantMemories;
    private Map<String, Integer> typeDistribution;
    private Double importantMemoryRatio;
    private Integer recentMemoryCount;
    private LocalDateTime lastExtractionTime;
    private LocalDateTime lastAccessedTime;
}