// File: src/main/java/com/zs/service/emotion/extractor/EmotionExtractor.java
package com.zs.service.emotion.extractor;

import com.zs.service.emotion.dto.EmotionAnalysisDTO;

/**
 * 情感提取器接口
 * 策略模式，支持多种提取算法
 */
public interface EmotionExtractor {

    /**
     * 分析文本情感
     * @param text 用户输入的文本
     * @param userId 用户ID（users表id）
     * @return 情感分析结果
     */
    EmotionAnalysisDTO analyze(String text, Long userId);

    /**
     * 获取提取器名称
     */
    String getName();

    /**
     * 获取提取器版本
     */
    String getVersion();

    /**
     * 是否支持实时分析
     */
    default boolean supportsRealtime() {
        return true;
    }

    /**
     * 提取器权重（用于混合模式）
     */
    default double getWeight() {
        return 1.0;
    }
}