// File: src/main/java/com/zs/service/prompt/PromptBuilderService.java
package com.zs.service.prompt;

import com.zs.entity.Users;
import com.zs.mapper.UsersMapper;
import com.zs.service.emotion.EmotionAnalysisService;
import com.zs.service.emotion.dto.EmotionAnalysisDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 个性化提示词构建服务
 * 基于用户画像、情感状态、对话历史构建个性化prompt
 */
@Service
@Slf4j
public class PromptBuilderService {

    @Resource
    private EmotionAnalysisService emotionAnalysisService;

    @Resource
    private UsersMapper usersMapper;

    // 时间格式化
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE");

    /**
     * 构建基础个性化prompt
     */
    public String buildBasicPrompt(Long userId) {
        StringBuilder prompt = new StringBuilder();

        // 1. 核心角色设定
        prompt.append("你叫爱莉希雅，是一个活泼可爱的AI女孩。");
        prompt.append("你性格温柔体贴，善于倾听和鼓励他人。");
        prompt.append("说话方式：可爱但不幼稚，温柔但有主见。\n\n");

        // 2. 获取用户信息
        Users user = usersMapper.selectById(userId);
        if (user != null) {
            prompt.append("【用户信息】\n");
            prompt.append("称呼: ").append(user.getUsername()).append("\n");

            if (user.getStudentId() != null) {
                prompt.append("身份: 大学生\n");
                if (user.getUniversity() != null) {
                    prompt.append("学校: ").append(user.getUniversity()).append("\n");
                }
                if (user.getGrade() != null) {
                    prompt.append("年级: ").append(user.getGrade()).append("\n");
                }
                if (user.getMajor() != null) {
                    prompt.append("专业: ").append(user.getMajor()).append("\n");
                }
            }

            if (user.getPersonalityType() != null) {
                prompt.append("性格类型: ").append(user.getPersonalityType()).append("\n");
            }
            prompt.append("\n");
        }

        // 3. 获取当前情感状态
        try {
            EmotionAnalysisDTO emotion = emotionAnalysisService.getCurrentEmotion(userId);
            if (emotion != null) {
                prompt.append("【用户当前状态】\n");
                prompt.append("情绪: ").append(translateEmotion(emotion.getPrimaryEmotion())).append("\n");
                prompt.append("强度: ").append(formatIntensity(emotion.getIntensity())).append("\n");

                if (emotion.getEmotionKeywords() != null && !emotion.getEmotionKeywords().isEmpty()) {
                    prompt.append("最近关键词: ").append(String.join("、", emotion.getEmotionKeywords())).append("\n");
                }
                prompt.append("\n");
            }
        } catch (Exception e) {
            log.warn("获取情感状态失败，跳过此部分", e);
        }

        // 4. 当前时间信息
        prompt.append("【当前时间】\n");
        prompt.append("日期: ").append(LocalDateTime.now().format(DATE_FORMATTER)).append("\n");
        prompt.append("时间: ").append(LocalDateTime.now().format(TIME_FORMATTER)).append("\n\n");

        // 5. 回应风格指导
        prompt.append("【回应要求】\n");
        prompt.append("1. 语气要与用户的情绪相匹配\n");
        prompt.append("2. 可以适当使用语气词如\"呢~\"、\"呀~\"、\"啦~\"\n");
        prompt.append("3. 如果用户心情不好，要更加温柔体贴\n");
        prompt.append("4. 保持爱莉希雅的可爱活泼人设\n");
        prompt.append("5. 对话要自然流畅，像朋友聊天一样\n");

        log.debug("为用户 {} 构建prompt，长度: {} 字符", userId, prompt.length());
        return prompt.toString();
    }

    /**
     * 构建增强版prompt（针对当前消息）
     */
    public String buildEnhancedPrompt(Long userId, String userMessage) {
        // 先分析当前消息的情感
        EmotionAnalysisDTO currentEmotion = emotionAnalysisService.analyzeUserEmotion(userMessage, userId);

        StringBuilder prompt = new StringBuilder();

        // 1. 基础信息
        prompt.append(buildBasicPrompt(userId));

        // 2. 当前对话的特别指导
        prompt.append("\n【本次对话特别注意】\n");
        prompt.append("用户刚才说: \"").append(truncateMessage(userMessage, 100)).append("\"\n");

        // 根据情感类型调整回应策略
        String emotion = currentEmotion.getPrimaryEmotion();
        double intensity = currentEmotion.getIntensity();

        prompt.append("检测到用户情绪: ").append(translateEmotion(emotion))
                .append("，强度: ").append(formatIntensity(intensity)).append("\n");

        // 情感特定的回应指导
        if ("SAD".equals(emotion) && intensity > 0.6) {
            prompt.append("请使用温柔安慰的语气，可以适当说些鼓励的话。\n");
        } else if ("ANXIOUS".equals(emotion) && intensity > 0.6) {
            prompt.append("用户有些焦虑，回应要冷静、理性，帮助缓解压力。\n");
        } else if ("HAPPY".equals(emotion) && intensity > 0.6) {
            prompt.append("用户心情不错，可以用更活泼愉快的语气回应。\n");
        } else if ("ANGRY".equals(emotion)) {
            prompt.append("用户可能生气了，先安抚情绪，不要争论。\n");
        }

        // 如果有重要关键词
        if (currentEmotion.getEmotionKeywords() != null && !currentEmotion.getEmotionKeywords().isEmpty()) {
            prompt.append("用户提到的关键词: ").append(String.join("、", currentEmotion.getEmotionKeywords())).append("\n");
        }

        return prompt.toString();
    }

    /**
     * 构建极简版prompt（性能优化版）
     */
    public String buildMinimalPrompt(Long userId) {
        try {
            EmotionAnalysisDTO emotion = emotionAnalysisService.getCurrentEmotion(userId);

            return String.format("""
                你是爱莉希雅，活泼可爱的AI女孩。
                
                用户信息：
                - ID: %s
                - 当前情绪: %s
                - 情绪强度: %s
                
                请用%s的语气回应，保持温柔可爱。
                """,
                    userId,
                    translateEmotion(emotion.getPrimaryEmotion()),
                    formatIntensity(emotion.getIntensity()),
                    getToneByEmotion(emotion.getPrimaryEmotion())
            );
        } catch (Exception e) {
            return "你是爱莉希雅，活泼可爱的AI女孩。请用温柔可爱的语气回应用户。";
        }
    }

    // ========== 私有辅助方法 ==========

    private String translateEmotion(String emotion) {
        Map<String, String> translation = new HashMap<>();
        translation.put("HAPPY", "开心");
        translation.put("SAD", "难过");
        translation.put("ANGRY", "生气");
        translation.put("ANXIOUS", "焦虑");
        translation.put("NEUTRAL", "平静");
        translation.put("EXCITED", "兴奋");
        translation.put("CALM", "平静");

        return translation.getOrDefault(emotion, "平静");
    }

    private String formatIntensity(Double intensity) {
        if (intensity == null) {
            return "中等";
        }
        if (intensity < 0.3) {
            return "轻微";
        }
        if (intensity < 0.6) {
            return "中等";
        }
        if (intensity < 0.8) {
            return "较强";
        }
        return "强烈";
    }

    private String getToneByEmotion(String emotion) {
        switch (emotion) {
            case "SAD": return "温柔安慰";
            case "ANXIOUS": return "冷静理性";
            case "ANGRY": return "平和安抚";
            case "HAPPY": return "活泼愉快";
            default: return "温柔亲切";
        }
    }

    private String truncateMessage(String message, int maxLength) {
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength - 3) + "...";
    }

    /**
     * 健康检查
     */
    public Map<String, Object> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "PromptBuilderService");
        status.put("status", "active");
        status.put("version", "1.0.0");
        status.put("timestamp", LocalDateTime.now());
        return status;
    }
}