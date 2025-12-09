package com.zs.service.chat.thinking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 思考过程服务
 * 新增类，不修改现有代码
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThinkingService {

    // 思考步骤定义
    private enum ThinkingStep {
        ANALYZING("分析中", "正在理解您的意思..."),
        EMOTION_CHECK("情感检测", "分析您的情绪状态..."),
        MEMORY_RECALL("回忆中", "寻找相关的记忆..."),
        CONTEXT_BUILDING("构建上下文", "结合背景信息思考..."),
        RESPONSE_ORGANIZING("组织回应", "思考如何回应最合适呢~"),
        PERSONALIZING("个性化调整", "加入爱莉希雅的特色语气...");

        private final String chinese;
        private final String defaultContent;

        ThinkingStep(String chinese, String defaultContent) {
            this.chinese = chinese;
            this.defaultContent = defaultContent;
        }

        public String getChinese() { return chinese; }
        public String getDefaultContent() { return defaultContent; }
    }

    /**
     * 生成思考过程（主方法）
     */
    public List<ThinkingEvent> generateThinkingProcess(
            Long userId,
            String userMessage,
            String userEmotion,
            Integer memoryCount) {

        List<ThinkingEvent> events = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 步骤1：开始思考
        events.add(ThinkingEvent.builder()
                .event("thinking_start")
                .step("start")
                .content("爱莉希雅开始思考...")
                .progress(0)
                .timestamp(now)
                .build());

        // 步骤2：分析用户输入（20%）
        events.add(createThinkingStep(
                ThinkingStep.ANALYZING, 20,
                userMessage.length() > 30 ?
                        "正在分析您的长消息..." :
                        "理解了您的简短消息~"));

        // 步骤3：情感检测（35%）
        if (userEmotion != null && !"NEUTRAL".equals(userEmotion)) {
            String emotionContent = String.format("感受到您有点%s呢~",
                    translateEmotion(userEmotion));
            events.add(createThinkingStep(
                    ThinkingStep.EMOTION_CHECK, 35, emotionContent));
        }

        // 步骤4：回忆相关记忆（50%）
        if (memoryCount != null && memoryCount > 0) {
            String memoryContent = String.format("想起您之前提到的 %d 件相关事情...", memoryCount);
            events.add(createThinkingStep(
                    ThinkingStep.MEMORY_RECALL, 50, memoryContent));
        } else {
            events.add(createThinkingStep(
                    ThinkingStep.MEMORY_RECALL, 50, "暂时没有相关的记忆呢~"));
        }

        // 步骤5：构建上下文（70%）
        events.add(createThinkingStep(
                ThinkingStep.CONTEXT_BUILDING, 70,
                "结合您的背景和当前情境思考中..."));

        // 步骤6：组织回应（85%）
        events.add(createThinkingStep(
                ThinkingStep.RESPONSE_ORGANIZING, 85,
                "思考如何回应最能让您感到温暖呢~"));

        // 步骤7：个性化调整（95%）
        events.add(createThinkingStep(
                ThinkingStep.PERSONALIZING, 95,
                "添加一些爱莉希雅特有的语气词和表情~"));

        // 步骤8：思考完成
        events.add(ThinkingEvent.builder()
                .event("thinking_complete")
                .step("complete")
                .content("思考完成，准备回复啦！")
                .progress(100)
                .timestamp(LocalDateTime.now())
                .build());

        log.debug("生成思考过程: userId={}, steps={}", userId, events.size());
        return events;
    }

    private ThinkingEvent createThinkingStep(ThinkingStep step, int progress, String customContent) {
        return ThinkingEvent.builder()
                .event("thinking_step")
                .step(step.getChinese())
                .content(customContent != null ? customContent : step.getDefaultContent())
                .progress(progress)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private String translateEmotion(String emotion) {
        switch (emotion) {
            case "HAPPY": return "开心";
            case "SAD": return "难过";
            case "ANGRY": return "生气";
            case "ANXIOUS": return "焦虑";
            case "EXCITED": return "兴奋";
            default: return "平静";
        }
    }
}