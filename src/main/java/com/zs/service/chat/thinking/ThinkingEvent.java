package com.zs.service.chat.thinking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 思考过程事件（SSE格式）
 * 新增类，不修改现有代码
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThinkingEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String event;      // 事件类型：thinking_start, thinking_step, thinking_complete
    private String step;       // 思考步骤：分析中、回忆中、组织中
    private String content;    // 思考内容
    private Integer progress;  // 进度0-100
    private LocalDateTime timestamp;

    @Override
    public String toString() {
        return String.format("[%s] %s - %s (进度:%d%%)",
                step, content, timestamp, progress);
    }
}