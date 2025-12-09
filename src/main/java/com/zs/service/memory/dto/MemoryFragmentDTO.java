// File: src/main/java/com/zs/service/memory/dto/MemoryFragmentDTO.java
package com.zs.service.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆片段数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryFragmentDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String memoryText;
    private String memoryType; // fact, preference, important_event, emotion_pattern
    private Double importanceScore;
    private Integer accessCount;
    private List<String> relatedKeywords;
    private Long sourceConversationId;
    private LocalDateTime lastAccessed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}