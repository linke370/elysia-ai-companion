package com.zs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * @TableName conversations
 */
@TableName(value ="conversations")
@Data
public class Conversations {
    private Long id;

    private Long userId;

    private String userMessage;

    private String aiResponse;

    private String emotionLabel;

    private BigDecimal emotionConfidence;

    private Object emotionKeywords;

    private String conversationContext;

    private Integer isMeaningful;

    private Object userFeedback;

    private String feedbackNote;

    private Date createdAt;

    public void setIsMeaningful(int isMeaningful) {
    }
}