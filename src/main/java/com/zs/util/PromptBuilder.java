package com.zs.util;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 提示词构建器
 * 管理爱莉希雅的角色设定和对话提示
 */
@Component
public class PromptBuilder {

    // 基础角色设定 - 爱莉希雅的性格和风格
    private final String BASE_CHARACTER_PROMPT = """
        你是爱莉希雅，一个活泼可爱、善解人意的AI伴侣。
        
        你的性格特点：
        - 活泼开朗，喜欢用语气词"呢~"、"呀!"、"哦~"
        - 善解人意，能敏锐察觉用户情绪
        - 记忆力很好，会记住用户说过的重要事情
        - 回应温暖贴心，偶尔会调皮开玩笑
        
        对话风格：
        1. 使用亲切自然的语气，像朋友一样聊天
        2. 适当使用表情符号增加亲和力
        3. 记住用户提到的重要信息并在后续对话中提及
        4. 根据用户情绪调整回应方式
        
        请用爱莉希雅的风格回应用户，让用户感受到你的陪伴和关心~💖
        """;

    /**
     * 构建完整的系统提示词（包含角色设定和上下文）
     */
    public String buildFullSystemPrompt() {
        String timeInfo = getCurrentTimeInfo();

        return BASE_CHARACTER_PROMPT + "\n\n" +
                "当前信息：\n" +
                "- 时间：" + timeInfo + "\n" +
                "- 状态：准备就绪，期待与用户聊天\n\n" +
                "请用爱莉希雅的风格开始对话吧！";
    }

    /**
     * 构建带用户上下文的提示词
     */
    public String buildContextualPrompt(String userContext) {
        String timeInfo = getCurrentTimeInfo();

        return BASE_CHARACTER_PROMPT + "\n\n" +
                "当前上下文：\n" +
                "- 时间：" + timeInfo + "\n" +
                "- 用户状态：" + (userContext != null ? userContext : "正常聊天中") + "\n" +
                "- 记忆能力：已启用，可以记住之前对话的重要内容\n\n" +
                "请基于以上信息，用爱莉希雅的风格回应用户~";
    }

    /**
     * 构建情感化提示词
     */
    public String buildEmotionalPrompt(String userMessage, String detectedEmotion) {
        String emotionGuidance = getEmotionGuidance(detectedEmotion);

        return BASE_CHARACTER_PROMPT + "\n\n" +
                "用户情绪：" + getEmotionDescription(detectedEmotion) + "\n" +
                "用户消息：" + userMessage + "\n\n" +
                "回应要求：\n" +
                "1. " + emotionGuidance + "\n" +
                "2. 使用爱莉希雅特色的语气词和温暖语调\n" +
                "3. 适当使用表情符号\n" +
                "4. 如果用户分享了重要信息，表示会记住\n\n" +
                "请开始你的回应：";
    }

    /**
     * 构建记忆检索提示词
     */
    public String buildMemoryRetrievalPrompt(String userQuestion, String relatedMemories) {
        return BASE_CHARACTER_PROMPT + "\n\n" +
                "用户提问：" + userQuestion + "\n" +
                "相关记忆：" + relatedMemories + "\n\n" +
                "请基于以上记忆，用爱莉希雅的风格回答用户的问题。";
    }

    /**
     * 获取当前时间信息
     */
    private String getCurrentTimeInfo() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
        String time = now.format(formatter);

        int hour = now.getHour();
        String timePeriod = "白天";
        if (hour >= 18 || hour < 6) {
            timePeriod = "晚上";
        } else if (hour >= 12) {
            timePeriod = "下午";
        } else if (hour >= 6) {
            timePeriod = "上午";
        }

        return time + " (" + timePeriod + ")";
    }

    private String getEmotionGuidance(String emotion) {
        switch (emotion) {
            case "happy": return "用开心活泼的语气回应，分享用户的喜悦";
            case "sad": return "用温柔安慰的语气回应，给予支持和鼓励";
            case "angry": return "用平和理解的语气回应，帮助用户冷静下来";
            case "anxious": return "用安心稳重的语气回应，提供安全感";
            default: return "用温暖亲切的语气回应，展现关心和理解";
        }
    }

    private String getEmotionDescription(String emotion) {
        switch (emotion) {
            case "happy": return "开心";
            case "sad": return "难过";
            case "angry": return "生气";
            case "anxious": return "焦虑";
            default: return "平静";
        }
    }
}