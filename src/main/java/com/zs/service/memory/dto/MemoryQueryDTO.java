// File: src/main/java/com/zs/service/memory/dto/MemoryQueryDTO.java
package com.zs.service.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 记忆查询数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String memoryType;
    private String keyword;
    private Integer limit;
    private Boolean onlyImportant;
    private SortOrder sortBy;

    public enum SortOrder {
        IMPORTANCE_DESC,
        RECENT_ACCESSED,
        CREATED_TIME
    }
}