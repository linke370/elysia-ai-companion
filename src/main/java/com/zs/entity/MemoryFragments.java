package com.zs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * @TableName memory_fragments
 */
@TableName(value ="memory_fragments")
@Data
public class MemoryFragments {
    private Long id;

    private Long userId;

    private String memoryText;

    private Object memoryType;

    private BigDecimal importanceScore;

    private Date lastAccessed;

    private Integer accessCount;

    private Object relatedKeywords;

    private Long sourceConversationId;

    private Date createdAt;

    private Date updatedAt;
}