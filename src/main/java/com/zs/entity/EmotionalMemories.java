package com.zs.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalTime;
import java.util.Date;
import lombok.Data;

/**
 * @TableName emotional_memories
 */
@TableName(value ="emotional_memories")
@Data
public class EmotionalMemories {
    private Long id;

    private Long userId;

    private String emotionType;

    private String emotionContext;

    private String triggerKeywords;

    private Object lifeScenario;

    private String aiResponsePattern;

    private Integer responseEffectiveness;

    private Date occurrenceTime;

    private String occurrenceDay;

    private Date createdAt;

    private Date updatedAt;

    public void setOccurrenceTime(LocalTime now) {

    }
}