package com.zs.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI情感状态实体
 * 对应数据库表：ai_emotional_state
 */
@TableName("ai_emotional_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIEmotionalState {

    @TableId(type = IdType.INPUT, value = "user_id")
    private Long userId;  // 与users表id对应

    @TableField("current_state")
    private String currentState; // NEUTRAL, HAPPY, CONCERNED, EXCITED, CURIOUS, PLAYFUL, REFLECTIVE

    @TableField("energy_level")
    private BigDecimal energyLevel;  // 能量值 0-1

    @TableField("last_state_change")
    private LocalDateTime lastStateChange;

    @TableField("last_interaction_time")
    private LocalDateTime lastInteractionTime;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}