package com.zs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * @TableName emotion_analysis_cache
 */
@TableName(value ="emotion_analysis_cache")
@Data
public class EmotionAnalysisCache {
    private Long id;

    private Long userId;

    private String textContent;

    private String textHash;

    private Object emotionResult;

    private BigDecimal confidenceScore;

    private String analysisMethod;

    private Date cacheValidUntil;

    private Date createdAt;
}