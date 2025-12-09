package com.zs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * @TableName user_preferences
 */
@TableName(value ="user_preferences")
@Data
public class UserPreferences {
    private Long id;

    private Long userId;

    private Object responseStyle;

    private Object conversationPace;

    private Object careFrequency;

    private Object preferredCareTimes;

    private Integer studyRemindersEnabled;

    private Integer examSupportEnabled;

    private Integer emotionAnalysisEnabled;

    private String themeColor;

    private String fontSize;

    private Date createdAt;

    private Date updatedAt;
}