// File: src/main/java/com/zs/service/emotion/dto/UserEmotionSnapshot.java
package com.zs.service.emotion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEmotionSnapshot {
    private Long userId;
    private String primaryEmotion;
    private Double intensity;
    private Double confidence;
    private List<String> keywords;
    private String lifeScenario;

    // 使用ISO格式，与Redis配置中的ObjectMapper保持一致
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private Long messageId;          // 关联的conversations表id
    private Map<String, Object> metadata;  // 扩展元数据
}