package com.zs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * @TableName memory_access_logs
 */
@TableName(value ="memory_access_logs")
@Data
public class MemoryAccessLogs {
    private Long id;

    private Long userId;

    private Long memoryId;

    private Long conversationId;

    private Object accessType;

    private String accessContext;

    private Integer accessEffectiveness;

    private Date createdAt;
}