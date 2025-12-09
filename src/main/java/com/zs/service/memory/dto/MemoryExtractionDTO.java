// File: src/main/java/com/zs/service/memory/dto/MemoryExtractionDTO.java
package com.zs.service.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 记忆提取数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryExtractionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // 基础信息
    private Long userId;
    private Long conversationId;
    private String userMessage;

    // 提取结果
    private List<MemoryFragmentDTO> extractedMemories;
    private Integer totalExtracted;

    // 统计信息
    private Map<String, Integer> memoryTypeDistribution;
    private Double averageImportance;
    private Integer importantMemoryCount;

    // 处理信息
    private String status; // SUCCESS, PARTIAL, FAILED
    private String errorMessage;
    private LocalDateTime extractionTime;
    private Long processingTimeMs;

    // 上下文信息
    private String emotionLabel;
    private Double emotionConfidence;
    private Boolean isMeaningfulConversation;
}