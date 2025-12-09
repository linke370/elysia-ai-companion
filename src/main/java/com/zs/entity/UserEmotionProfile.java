package com.zs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * @TableName user_emotion_profile
 */
@TableName(value ="user_emotion_profile")
@Data
public class UserEmotionProfile {
    private Long id;

    private Long userId;

    private Object emotionStats;

    private Object commonTopics;

    private String preferredResponseStyle;

    private Object activeTimes;

    private Object conversationPatterns;

    private BigDecimal emotionalUnderstandingScore;

    private Date lastAnalysisAt;

    private Date updatedAt;
}