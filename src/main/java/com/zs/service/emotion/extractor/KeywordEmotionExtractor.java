// File: src/main/java/com/zs/service/emotion/extractor/KeywordEmotionExtractor.java
package com.zs.service.emotion.extractor;

import com.zs.entity.Users;
import com.zs.mapper.UsersMapper;
import com.zs.service.emotion.dto.EmotionAnalysisDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于关键词的情感提取器
 * 高性能、低延迟，适合实时分析
 */
@Component
@Slf4j
public class KeywordEmotionExtractor implements EmotionExtractor {

    @Resource
    private UsersMapper usersMapper;

    // 情感关键词库（可配置化）
    private final Map<String, Map<String, Double>> emotionKeywordMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> scenarioKeywordMap = new ConcurrentHashMap<>();

    // 情感类型定义
    public enum EmotionType {
        HAPPY("开心", 0.9),
        EXCITED("兴奋", 0.8),
        CALM("平静", 0.6),
        SAD("难过", 0.9),
        ANGRY("生气", 0.9),
        ANXIOUS("焦虑", 0.8),
        CONCERNED("关心", 0.7),
        CURIOUS("好奇", 0.6),
        PLAYFUL("调皮", 0.7),
        NEUTRAL("中性", 0.5);

        private final String chinese;
        private final double defaultIntensity;

        EmotionType(String chinese, double defaultIntensity) {
            this.chinese = chinese;
            this.defaultIntensity = defaultIntensity;
        }

        public String getChinese() { return chinese; }
        public double getDefaultIntensity() { return defaultIntensity; }
    }

    @PostConstruct
    public void init() {
        initializeEmotionKeywords();
        initializeScenarioKeywords();
        log.info("关键词情感提取器初始化完成，加载{}种情感类型，{}种生活场景",
                emotionKeywordMap.size(), scenarioKeywordMap.size());
    }

    private void initializeEmotionKeywords() {
        // 开心/积极
        Map<String, Double> happyMap = new HashMap<>();
        happyMap.put("开心", 0.9); happyMap.put("高兴", 0.8); happyMap.put("快乐", 0.8);
        happyMap.put("幸福", 0.9); happyMap.put("喜欢", 0.7); happyMap.put("爱你", 0.9);
        happyMap.put("谢谢", 0.6); happyMap.put("美好", 0.7); happyMap.put("幸运", 0.6);
        happyMap.put("不错", 0.5); happyMap.put("很好", 0.6); happyMap.put("棒", 0.7);
        happyMap.put("完美", 0.8); happyMap.put("优秀", 0.7); happyMap.put("精彩", 0.7);
        emotionKeywordMap.put("HAPPY", happyMap);

        // 难过/消极
        Map<String, Double> sadMap = new HashMap<>();
        sadMap.put("难过", 0.9); sadMap.put("伤心", 0.8); sadMap.put("悲伤", 0.8);
        sadMap.put("哭", 0.7); sadMap.put("失望", 0.7); sadMap.put("痛苦", 0.9);
        sadMap.put("郁闷", 0.6); sadMap.put("委屈", 0.7); sadMap.put("孤独", 0.8);
        sadMap.put("寂寞", 0.7); sadMap.put("难受", 0.7); sadMap.put("崩溃", 0.9);
        sadMap.put("无助", 0.8); sadMap.put("绝望", 0.9); sadMap.put("心疼", 0.7);
        emotionKeywordMap.put("SAD", sadMap);

        // 生气/愤怒
        Map<String, Double> angryMap = new HashMap<>();
        angryMap.put("生气", 0.9); angryMap.put("愤怒", 0.9); angryMap.put("讨厌", 0.8);
        angryMap.put("烦", 0.7); angryMap.put("恼火", 0.8); angryMap.put("暴躁", 0.8);
        angryMap.put("恨", 0.9); sadMap.put("不满", 0.6); sadMap.put("气愤", 0.8);
        sadMap.put("怒火", 0.9); sadMap.put("发火", 0.8); sadMap.put("暴躁", 0.8);
        emotionKeywordMap.put("ANGRY", angryMap);

        // 焦虑/压力
        Map<String, Double> anxiousMap = new HashMap<>();
        anxiousMap.put("焦虑", 0.9); anxiousMap.put("紧张", 0.8); anxiousMap.put("担心", 0.7);
        anxiousMap.put("害怕", 0.8); anxiousMap.put("压力", 0.9); anxiousMap.put("慌张", 0.7);
        anxiousMap.put("恐惧", 0.8); anxiousMap.put("不安", 0.7); anxiousMap.put("忧虑", 0.8);
        anxiousMap.put("恐慌", 0.9); anxiousMap.put("担心", 0.7); anxiousMap.put("紧张", 0.8);
        emotionKeywordMap.put("ANXIOUS", anxiousMap);

        // 平静/中性（默认）
        Map<String, Double> neutralMap = new HashMap<>();
        neutralMap.put("正常", 0.5); neutralMap.put("一般", 0.5); neutralMap.put("还行", 0.4);
        neutralMap.put("平常", 0.5); neutralMap.put("普通", 0.5); neutralMap.put("可以", 0.4);
        emotionKeywordMap.put("NEUTRAL", neutralMap);
    }

    private void initializeScenarioKeywords() {
        // 考试压力（基于你的数据库ENUM）
        scenarioKeywordMap.put("exam_stress", Arrays.asList(
                "考试", "期末", "测验", "挂科", "复习", "熬夜", "题库",
                "成绩", "分数", "及格", "补考", "备考", "压力", "紧张",
                "论文", "答辩", "学分", "绩点", "挂科", "重修"
        ));

        // 想家情绪
        scenarioKeywordMap.put("homesick", Arrays.asList(
                "想家", "家乡", "父母", "妈妈", "爸爸", "家人", "回家",
                "故乡", "想念", "思念", "异地", "离家", "归家", "团聚",
                "亲人", "老家", "春节", "过年", "团圆"
        ));

        // 社交焦虑
        scenarioKeywordMap.put("social_anxiety", Arrays.asList(
                "社交", "朋友", "同学", "人际关系", "相处", "交往", "沟通",
                "聊天", "尴尬", "害羞", "内向", "外向", "聚会", "活动",
                "朋友", "室友", "同学", "同事", "陌生人"
        ));

        // 学习疲惫
        scenarioKeywordMap.put("study_fatigue", Arrays.asList(
                "学习", "作业", "论文", "实验", "报告", "课题", "研究",
                "课程", "专业", "学分", "图书馆", "自习", "疲倦", "累",
                "熬夜", "通宵", "作业", "预习", "复习", "考试"
        ));

        // 人际关系（恋爱）
        scenarioKeywordMap.put("relationship", Arrays.asList(
                "恋爱", "男朋友", "女朋友", "分手", "暗恋", "表白", "约会",
                "情侣", "感情", "喜欢", "爱", "吵架", "和好", "异地恋",
                "对象", "老公", "老婆", "恩爱", "分手", "复合"
        ));
    }

    @Override
    public EmotionAnalysisDTO analyze(String text, Long userId) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 获取用户信息（根据你的实体类调整）
            Users user = getUserById(userId);
            String username = user != null ? user.getUsername() : "用户" + userId;
            String personalityType = user != null ? user.getPersonalityType() : "balanced";
            String emotionalTendency = user != null ? user.getEmotionalTendency() : null;
            String studentId = user != null ? user.getStudentId() : null;
            String university = user != null ? user.getUniversity() : null;

            // 2. 情感得分计算
            Map<String, Double> emotionScores = calculateEmotionScores(text);

            // 3. 确定主要和次要情感
            String primaryEmotion = determinePrimaryEmotion(emotionScores);
            String secondaryEmotion = determineSecondaryEmotion(emotionScores, primaryEmotion);
            Double intensity = emotionScores.getOrDefault(primaryEmotion, 0.0);

            // 4. 提取关键词
            List<String> emotionKeywords = extractEmotionKeywords(text);
            List<String> contextKeywords = extractContextKeywords(text);

            // 5. 场景分类
            String lifeScenario = classifyLifeScenario(text);
            String conversationContext = classifyConversationContext(text);

            // 6. 计算置信度
            Double confidence = calculateConfidence(intensity, emotionKeywords.size(), text.length());

            // 7. 判断是否重要
            Boolean isMeaningful = isMeaningfulEmotion(intensity, primaryEmotion);

            // 8. 构建结果 - 添加学生信息
            return EmotionAnalysisDTO.builder()
                    .userId(userId)
                    .username(username)
                    .userMessage(text.length() > 200 ? text.substring(0, 200) + "..." : text)
                    .primaryEmotion(primaryEmotion)
                    .secondaryEmotion(secondaryEmotion)
                    .intensity(intensity)
                    .confidence(confidence)
                    .emotionScores(emotionScores)
                    .emotionKeywords(emotionKeywords)
                    .contextKeywords(contextKeywords)
                    .lifeScenario(lifeScenario)
                    .conversationContext(conversationContext)
                    .isMeaningful(isMeaningful)
                    .source("KEYWORD")
                    .analysisTime(LocalDateTime.now())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .personalityType(personalityType)
                    .emotionalTendency(emotionalTendency)
                    .studentInfo(studentId != null ? Map.of(
                            "studentId", studentId,
                            "university", university != null ? university : "未知学校",
                            "major", user != null ? user.getMajor() : null,
                            "grade", user != null ? user.getGrade() : null
                    ) : null)
                    .build();

        } catch (Exception e) {
            log.error("情感分析失败: userId={}, error={}", userId, e.getMessage(), e);
            return createFallbackResult(userId, text);
        }
    }

    /**
     * 获取用户信息（安全版本）
     */
    private Users getUserById(Long userId) {
        try {
            Users user = usersMapper.selectById(userId);
            if (user != null) {
                log.debug("获取用户信息成功: userId={}, username={}, personalityType={}",
                        userId, user.getUsername(), user.getPersonalityType());
            } else {
                log.warn("用户不存在: userId={}", userId);
            }
            return user;
        } catch (Exception e) {
            log.error("获取用户信息异常: userId={}, error={}", userId, e.getMessage(), e);
            return null;
        }
    }



    private Map<String, Double> calculateEmotionScores(String text) {
        Map<String, Double> scores = new HashMap<>();
        String lowerText = text.toLowerCase();

        // 计算每种情感的得分
        for (Map.Entry<String, Map<String, Double>> entry : emotionKeywordMap.entrySet()) {
            String emotionType = entry.getKey();
            Map<String, Double> keywords = entry.getValue();

            double score = 0.0;
            int matchCount = 0;

            for (Map.Entry<String, Double> keywordEntry : keywords.entrySet()) {
                String keyword = keywordEntry.getKey().toLowerCase();
                double weight = keywordEntry.getValue();

                if (lowerText.contains(keyword)) {
                    score += weight;
                    matchCount++;
                }
            }

            // 归一化处理，考虑匹配数量和权重
            if (score > 0) {
                // 基础分数 + 匹配数量奖励
                double normalizedScore = Math.min((score / 3.0) + (matchCount * 0.05), 1.0);
                scores.put(emotionType, normalizedScore);
            } else {
                scores.put(emotionType, 0.0);
            }
        }

        // 确保至少有一个情感得分
        if (scores.values().stream().allMatch(score -> score <= 0.1)) {
            scores.put("NEUTRAL", 0.5);
        }

        return scores;
    }

    private String determinePrimaryEmotion(Map<String, Double> scores) {
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NEUTRAL");
    }

    private String determineSecondaryEmotion(Map<String, Double> scores, String primary) {
        return scores.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(primary))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NEUTRAL");
    }

    private List<String> extractEmotionKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        String lowerText = text.toLowerCase();

        for (Map<String, Double> emotionMap : emotionKeywordMap.values()) {
            for (String keyword : emotionMap.keySet()) {
                if (lowerText.contains(keyword.toLowerCase())) {
                    keywords.add(keyword);
                }
            }
        }

        // 去重并限制数量
        return keywords.stream()
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<String> extractContextKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        String lowerText = text.toLowerCase();

        for (List<String> scenarioWords : scenarioKeywordMap.values()) {
            for (String word : scenarioWords) {
                if (lowerText.contains(word.toLowerCase())) {
                    keywords.add(word);
                }
            }
        }

        return keywords.stream()
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    private String classifyLifeScenario(String text) {
        String lowerText = text.toLowerCase();
        Map<String, Integer> scenarioScores = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : scenarioKeywordMap.entrySet()) {
            String scenario = entry.getKey();
            List<String> words = entry.getValue();

            int score = 0;
            for (String word : words) {
                if (lowerText.contains(word.toLowerCase())) {
                    score++;
                }
            }

            if (score > 0) {
                scenarioScores.put(scenario, score);
            }
        }

        // 返回得分最高的场景，或默认场景
        return scenarioScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("general");
    }

    private String classifyConversationContext(String text) {
        if (containsAny(text, "?", "？", "什么", "怎么", "为什么", "如何", "吗", "呢", "哪")) {
            return "question";
        } else if (containsAny(text, "心情", "感觉", "情绪", "情感", "开心", "难过", "生气")) {
            return "emotion_sharing";
        } else if (containsAny(text, "帮助", "建议", "怎么办", "求助", "指导", "教我")) {
            return "seeking_help";
        } else if (containsAny(text, "故事", "讲个", "笑话", "娱乐", "聊天", "聊聊")) {
            return "entertainment";
        } else if (containsAny(text, "学习", "考试", "作业", "复习", "论文")) {
            return "study_related";
        } else if (containsAny(text, "朋友", "家人", "同学", "室友", "恋爱")) {
            return "relationship";
        } else {
            return "casual_chat";
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Double calculateConfidence(Double intensity, int keywordCount, int textLength) {
        // 基于三个因素计算置信度：
        // 1. 情感强度 (权重0.6)
        // 2. 关键词数量 (权重0.3)
        // 3. 文本长度 (权重0.1，文本越长置信度越高)

        double intensityScore = intensity * 0.6;
        double keywordScore = Math.min(keywordCount * 0.1, 0.3);
        double lengthScore = Math.min(textLength / 100.0 * 0.1, 0.1);

        return Math.min(intensityScore + keywordScore + lengthScore, 1.0);
    }

    private Boolean isMeaningfulEmotion(Double intensity, String emotion) {
        // 高强度的情感认为是重要的
        if (intensity > 0.7) {
            return true;
        }

        // 特定情感即使强度中等也可能是重要的
        List<String> meaningfulEmotions = Arrays.asList("SAD", "ANXIOUS", "ANGRY");
        if (meaningfulEmotions.contains(emotion) && intensity > 0.5) {
            return true;
        }

        return false;
    }

    private EmotionAnalysisDTO createFallbackResult(Long userId, String text) {
        return EmotionAnalysisDTO.builder()
                .userId(userId)
                .userMessage(text)
                .primaryEmotion("NEUTRAL")
                .secondaryEmotion("NEUTRAL")
                .intensity(0.5)
                .confidence(0.5)
                .emotionScores(Map.of("NEUTRAL", 0.5))
                .emotionKeywords(new ArrayList<>())
                .contextKeywords(new ArrayList<>())
                .lifeScenario("general")
                .conversationContext("casual_chat")
                .isMeaningful(false)
                .source("FALLBACK")
                .analysisTime(LocalDateTime.now())
                .processingTimeMs(0L)
                .build();
    }

    @Override
    public String getName() {
        return "KeywordEmotionExtractor";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }
}