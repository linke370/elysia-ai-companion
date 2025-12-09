// DTO类 - 单独放在vo包下
// 位置：src/main/java/com/zs/vo/ChatRequest.java

package com.zs.vo;

import lombok.Data;

/**
 * 聊天请求DTO类
 * 用于接收JSON格式的请求体
 */
@Data
public class ChatRequest {
    private String message;
    private String userId;
    private String model = "qwen"; // 默认使用qwen
    private Boolean useElysiaStyle = true; // 默认使用爱莉希雅风格

    // 构造函数
    public ChatRequest() {}

    public ChatRequest(String message, String userId) {
        this.message = message;
        this.userId = userId;
    }
}