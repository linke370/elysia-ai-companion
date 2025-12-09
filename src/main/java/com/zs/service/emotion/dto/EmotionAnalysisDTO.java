// File: src/main/java/com/zs/service/emotion/dto/EmotionAnalysisDTO.java
package com.zs.service.emotion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 情感分析数据传输对象
 * 用于服务层之间传递数据，不直接对应数据库表
 */
// File: src/main/java/com/zs/service/emotion/dto/EmotionAnalysisDTO.java
// 在原有基础上添加学生信息字段
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionAnalysisDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // 基础信息
    private Long userId;           // 用户ID（对应users表的id）
    private String userMessage;    // 用户原始消息
    private String username;       // 用户名

    // 学生信息（新添加）
    private Map<String, String> studentInfo; // 包含studentId, university, major, grade

    // 情感分析结果
    private String primaryEmotion;      // 主要情感：HAPPY, SAD, ANGRY, ANXIOUS, NEUTRAL
    private String secondaryEmotion;    // 次要情感
    private Double intensity;           // 情感强度 0.0-1.0
    private Double confidence;          // 置信度 0.0-1.0

    // 详细分析数据
    private Map<String, Double> emotionScores;   // 各情感维度得分
    private List<String> emotionKeywords;        // 检测到的情感关键词
    private List<String> contextKeywords;        // 上下文关键词

    // 场景分类（基于你的数据库ENUM）
    private String lifeScenario;         // 生活场景：exam_stress, homesick等
    private String conversationContext;  // 对话场景

    // 用户档案信息
    private String personalityType;      // 性格类型（从users表获取）
    private String emotionalTendency;    // 情感倾向（从users表获取）

    // 标记信息
    private Boolean isMeaningful;        // 是否重要情感事件
    private String source;               // 分析来源：KEYWORD, AI_MODEL, HYBRID

    // 时间信息
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime analysisTime;
    private Long processingTimeMs;       // 处理耗时(毫秒)
}