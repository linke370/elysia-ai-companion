package com.zs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * @TableName relationship_growth
 */
@TableName(value ="relationship_growth")
@Data
public class RelationshipGrowth {
    private Long id;

    private Long userId;

    private Integer intimacyLevel;

    private Integer intimacyPoints;

    private Integer trustLevel;

    private Integer totalConversations;

    private Integer meaningfulConversations;

    private Integer positiveFeedbacks;

    private Object unlockedMilestones;

    private Object specialMemories;

    private Integer emotionalUnderstandingLevel;

    private Date lastLevelUpDate;

    private Date createdAt;

    private Date updatedAt;
}