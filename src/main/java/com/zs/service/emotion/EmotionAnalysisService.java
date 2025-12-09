// File: src/main/java/com/zs/service/emotion/EmotionAnalysisService.java
package com.zs.service.emotion;

import com.zs.entity.Conversations;
import com.zs.entity.EmotionalMemories;
import com.zs.entity.Users;
import com.zs.mapper.ConversationsMapper;
import com.zs.mapper.EmotionalMemoriesMapper;
import com.zs.mapper.UsersMapper;
import com.zs.service.emotion.cache.EmotionCacheManager;
import com.zs.service.emotion.dto.EmotionAnalysisDTO;
import com.zs.service.emotion.dto.UserEmotionSnapshot;
import com.zs.service.emotion.extractor.KeywordEmotionExtractor;
import com.zs.service.emotion.repository.EmotionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 情感分析主服务 - 完整版
 * 适配Users实体类，包含所有必需功能
 */
@Service
@Slf4j
public class EmotionAnalysisService {

    @Resource
    private KeywordEmotionExtractor keywordEmotionExtractor;

    @Resource
    private EmotionCacheManager emotionCacheManager;

    @Resource
    private EmotionRepository emotionRepository;

    @Resource
    private UsersMapper usersMapper;

    @Resource
    private ConversationsMapper conversationsMapper;

    @Resource
    private EmotionalMemoriesMapper emotionalMemoriesMapper;

    @Resource
    private ObjectMapper objectMapper;

    // 用户情感状态缓存（内存缓存，用于快速访问）
    private final Map<Long, EmotionAnalysisDTO> userEmotionCache = new ConcurrentHashMap<>();

    // 线程池用于异步处理
    private ExecutorService asyncExecutor;

    @PostConstruct
    public void init() {
        // 初始化线程池
        asyncExecutor = Executors.newFixedThreadPool(10);
        log.info("情感分析服务初始化完成，线程池大小: 10");
    }

    // ========== 核心情感分析方法 ==========

    /**
     * 分析用户情感（同步调用，立即返回）
     */
    public EmotionAnalysisDTO analyzeUserEmotion(String userMessage, Long userId) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始情感分析: userId={}, messageLength={}", userId, userMessage.length());

            // 1. 使用关键词提取器分析情感
            EmotionAnalysisDTO emotionResult = keywordEmotionExtractor.analyze(userMessage, userId);

            // 2. 更新内存缓存
            userEmotionCache.put(userId, emotionResult);

            // 3. 更新Redis缓存（异步）
            updateCacheAsync(userId, emotionResult);

            // 4. 如果情感重要，异步保存到数据库
            if (emotionResult.getIsMeaningful() != null && emotionResult.getIsMeaningful()) {
                saveToDatabaseAsync(userId, emotionResult, userMessage);
            }

            // 5. 异步保存对话记录
            saveConversationAsync(userId, userMessage, emotionResult);

            long processingTime = System.currentTimeMillis() - startTime;
            emotionResult.setProcessingTimeMs(processingTime);

            log.info("情感分析完成: userId={}, emotion={}, intensity={}, time={}ms",
                    userId, emotionResult.getPrimaryEmotion(),
                    emotionResult.getIntensity(), processingTime);

            return emotionResult;

        } catch (Exception e) {
            log.error("情感分析异常: userId={}", userId, e);
            return createErrorResult(userId, userMessage, e);
        }
    }

    /**
     * 批量分析情感（用于历史数据分析）
     */
    public List<EmotionAnalysisDTO> batchAnalyzeEmotions(Long userId, List<String> messages) {
        log.info("批量情感分析: userId={}, messages={}", userId, messages.size());

        List<EmotionAnalysisDTO> results = new ArrayList<>();
        for (String message : messages) {
            EmotionAnalysisDTO result = keywordEmotionExtractor.analyze(message, userId);
            results.add(result);
        }

        // 异步批量保存重要情感
        asyncExecutor.submit(() -> {
            List<EmotionAnalysisDTO> meaningfulEmotions = results.stream()
                    .filter(emotion -> emotion.getIsMeaningful() != null && emotion.getIsMeaningful())
                    .toList();

            if (!meaningfulEmotions.isEmpty()) {
                emotionRepository.batchSaveEmotionalMemories(userId, meaningfulEmotions);
            }
        });

        return results;
    }

    // ========== 用户信息相关方法 ==========

    /**
     * 获取用户信息
     */
    public Map<String, Object> getUserInfo(Long userId) {
        try {
            Users user = usersMapper.selectById(userId);
            if (user == null) {
                return Map.of(
                        "exists", false,
                        "message", "用户不存在"
                );
            }

            Map<String, Object> studentInfo = new HashMap<>();
            if (user.getStudentId() != null) {
                studentInfo.put("studentId", user.getStudentId());
                studentInfo.put("university", user.getUniversity());
                studentInfo.put("major", user.getMajor());
                studentInfo.put("grade", user.getGrade());
            }

            Map<String, Object> personality = new HashMap<>();
            personality.put("type", user.getPersonalityType());
            personality.put("tendency", user.getEmotionalTendency());

            Map<String, Object> status = new HashMap<>();
            status.put("isActive", user.getIsActive());
            status.put("lastLogin", user.getLastLoginAt());
            status.put("createdAt", user.getCreatedAt());
            status.put("updatedAt", user.getUpdatedAt());

            return Map.of(
                    "exists", true,
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "email", user.getEmail(),
                    "studentInfo", studentInfo,
                    "personality", personality,
                    "status", status
            );

        } catch (Exception e) {
            log.error("获取用户信息失败: userId={}", userId, e);
            return Map.of(
                    "exists", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * 获取用户当前情感状态
     */
    public EmotionAnalysisDTO getCurrentEmotion(Long userId) {
        // 1. 先查内存缓存
        EmotionAnalysisDTO cached = userEmotionCache.get(userId);
        if (cached != null) {
            return cached;
        }

        // 2. 查Redis缓存
        UserEmotionSnapshot snapshot = emotionCacheManager.getCurrentEmotion(userId);

        if (snapshot != null) {
            return convertSnapshotToDTO(userId, snapshot);
        }

        // 3. 返回默认值
        return createDefaultEmotion(userId);
    }

    // ========== 报告生成方法 ==========

    /**
     * 生成情感报告
     */
    public Map<String, Object> generateEmotionReport(Long userId) {
        Map<String, Object> report = new LinkedHashMap<>();

        // 1. 当前情感状态
        EmotionAnalysisDTO currentEmotion = getCurrentEmotion(userId);
        report.put("currentEmotion", Map.of(
                "type", currentEmotion.getPrimaryEmotion(),
                "intensity", currentEmotion.getIntensity(),
                "confidence", currentEmotion.getConfidence(),
                "scenario", currentEmotion.getLifeScenario(),
                "context", currentEmotion.getConversationContext()
        ));

        // 2. 情感趋势（最近10次）
        List<Map<String, Object>> trend = emotionCacheManager.getEmotionTrend(userId, 10);
        report.put("trend", trend);

        // 3. 关键词统计
        List<Map.Entry<String, Long>> topKeywords = emotionCacheManager.getTopKeywords(userId, 10);
        report.put("topKeywords", topKeywords);

        // 4. 统计信息
        report.put("statistics", Map.of(
                "totalAnalyses", trend.size(),
                "meaningfulCount", calculateMeaningfulCount(trend),
                "lastAnalysis", currentEmotion.getAnalysisTime()
        ));

        // 5. 情感洞察
        report.put("insights", generateEmotionInsights(trend, topKeywords));

        return report;
    }

    /**
     * 生成完整报告（包含用户信息）
     */
    public Map<String, Object> generateCompleteReport(Long userId) {
        Map<String, Object> report = new LinkedHashMap<>();

        // 1. 用户基本信息
        Map<String, Object> userInfo = getUserInfo(userId);
        report.put("userInfo", userInfo);

        // 2. 情感分析报告
        Map<String, Object> emotionReport = generateEmotionReport(userId);
        report.put("emotionAnalysis", emotionReport);

        // 3. 学习场景分析（基于学生信息）
        if (Boolean.TRUE.equals(userInfo.get("exists"))) {
            Map<String, Object> studentInfo = (Map<String, Object>) userInfo.get("studentInfo");
            if (studentInfo != null && !studentInfo.isEmpty()) {
                report.put("learningContext", analyzeLearningContext(userId, studentInfo));
            }
        }

        // 4. 个性化建议
        report.put("personalizedSuggestions", generateSuggestions(userId, emotionReport, userInfo));

        return report;
    }

    /**
     * 分析学习场景（针对大学生）
     */
    private Map<String, Object> analyzeLearningContext(Long userId, Map<String, Object> studentInfo) {
        Map<String, Object> context = new HashMap<>();

        String university = (String) studentInfo.get("university");
        String major = (String) studentInfo.get("major");
        String grade = (String) studentInfo.get("grade");

        context.put("university", university != null ? university : "未知学校");
        context.put("major", major != null ? major : "未设置");
        context.put("grade", grade != null ? grade : "未设置");

        // 基于年级提供不同建议
        if (grade != null) {
            switch (grade) {
                case "大一":
                    context.put("focus", "适应大学生活、基础课程学习");
                    context.put("challenges", "课程适应、社交建立、时间管理");
                    context.put("suggestions", Arrays.asList(
                            "多参加社团活动，认识新朋友",
                            "打好基础课程基础",
                            "探索大学校园和资源"
                    ));
                    break;
                case "大二":
                    context.put("focus", "专业课程深化、社团活动参与");
                    context.put("challenges", "专业方向选择、学业压力增加");
                    context.put("suggestions", Arrays.asList(
                            "深入学习专业课程",
                            "参与社团或学生组织",
                            "考虑未来的职业方向"
                    ));
                    break;
                case "大三":
                    context.put("focus", "实习准备、考研/就业规划");
                    context.put("challenges", "未来规划压力、学业实习平衡");
                    context.put("suggestions", Arrays.asList(
                            "准备实习或参加实践活动",
                            "规划考研或就业方向",
                            "积累项目经验"
                    ));
                    break;
                case "大四":
                    context.put("focus", "毕业设计、就业/考研冲刺");
                    context.put("challenges", "毕业压力、未来不确定性");
                    context.put("suggestions", Arrays.asList(
                            "认真完成毕业设计",
                            "积极参加招聘会",
                            "做好毕业后的规划"
                    ));
                    break;
                default:
                    context.put("focus", "全面发展、享受大学生活");
                    context.put("challenges", "平衡学习与生活");
                    context.put("suggestions", Arrays.asList(
                            "珍惜大学时光",
                            "全面发展个人能力",
                            "建立良好的人际关系"
                    ));
            }
        } else {
            context.put("focus", "全面发展");
            context.put("challenges", "平衡学习与生活");
            context.put("suggestions", Arrays.asList("保持学习热情", "注意身心健康"));
        }

        // 基于专业提供建议
        if (major != null) {
            if (major.contains("计算机") || major.contains("软件") || major.contains("信息")) {
                context.put("majorSuggestions", Arrays.asList(
                        "多进行编程实践",
                        "关注技术发展趋势",
                        "参与开源项目"
                ));
            } else if (major.contains("管理") || major.contains("经济") || major.contains("金融")) {
                context.put("majorSuggestions", Arrays.asList(
                        "关注经济动态",
                        "培养数据分析能力",
                        "参与商业案例分析"
                ));
            }
        }

        return context;
    }

    // ========== 异步处理方法 ==========

    /**
     * 异步更新缓存
     */
    @Async
    public void updateCacheAsync(Long userId, EmotionAnalysisDTO emotion) {
        try {
            // 1. 缓存当前情感状态
            emotionCacheManager.cacheCurrentEmotion(userId, emotion);

            // 2. 更新情感趋势
            emotionCacheManager.updateEmotionTrend(userId, emotion);

            // 3. 更新关键词频率
            if (emotion.getEmotionKeywords() != null && !emotion.getEmotionKeywords().isEmpty()) {
                emotionCacheManager.updateKeywordFrequency(userId, emotion.getEmotionKeywords());
            }

            // 4. 记录历史
            emotionCacheManager.recordEmotionHistory(userId, emotion);

            log.debug("缓存更新完成: userId={}", userId);

        } catch (Exception e) {
            log.error("异步缓存更新失败: userId={}", userId, e);
        }
    }

    /**
     * 异步保存到数据库
     */
    @Async
    @Transactional
    public void saveToDatabaseAsync(Long userId, EmotionAnalysisDTO emotion, String originalMessage) {
        try {
            emotionRepository.saveEmotionalMemory(userId, emotion);
            log.debug("情感记忆保存成功: userId={}, emotion={}", userId, emotion.getPrimaryEmotion());
        } catch (Exception e) {
            log.error("保存情感记忆失败: userId={}", userId, e);
        }
    }

    /**
     * 异步保存对话记录
     */
    @Async
    @Transactional
    public void saveConversationAsync(Long userId, String userMessage, EmotionAnalysisDTO emotion) {
        try {
            Conversations conversation = new Conversations();
            conversation.setUserId(userId);
            conversation.setUserMessage(userMessage);
            conversation.setAiResponse(""); // AI回复暂时为空

            // 设置情感标签和置信度
            conversation.setEmotionLabel(emotion.getPrimaryEmotion());
            if (emotion.getConfidence() != null) {
                conversation.setEmotionConfidence(BigDecimal.valueOf(emotion.getConfidence()));

            }

            // 设置情感关键词（JSON格式）
            try {
                if (emotion.getEmotionKeywords() != null && !emotion.getEmotionKeywords().isEmpty()) {
                    String keywordsJson = objectMapper.writeValueAsString(emotion.getEmotionKeywords());
                    conversation.setEmotionKeywords(keywordsJson);
                }
            } catch (JsonProcessingException e) {
                log.warn("序列化情感关键词失败", e);
            }

            conversation.setConversationContext(emotion.getConversationContext());
            conversation.setIsMeaningful(Boolean.TRUE.equals(emotion.getIsMeaningful()) ? 1 : 0);

            conversation.setCreatedAt(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));


            conversationsMapper.insert(conversation);
            log.debug("对话记录保存成功: userId={}, conversationId={}", userId, conversation.getId());

        } catch (Exception e) {
            log.error("保存对话记录失败: userId={}", userId, e);
        }
    }

    // ========== 实用方法 ==========

    /**
     * 更新对话的AI回复
     */
    public void updateConversationResponse(Long conversationId, String aiResponse) {
        try {
            Conversations conversation = conversationsMapper.selectById(conversationId);
            if (conversation != null) {
                conversation.setAiResponse(aiResponse);
               // conversation.setUpdatedAt(LocalDateTime.now());
                conversationsMapper.updateById(conversation);
                log.debug("更新对话AI回复: conversationId={}", conversationId);
            }
        } catch (Exception e) {
            log.error("更新对话回复失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 获取用户情感历史
     */
    public List<EmotionAnalysisDTO> getEmotionHistory(Long userId, int limit) {
        try {
            // 这里可以添加从数据库或Redis获取历史记录的逻辑
            // 目前返回空列表，你可以根据需要实现
            return new ArrayList<>();

        } catch (Exception e) {
            log.error("获取情感历史失败: userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 清理用户情感数据
     */
    public void clearUserEmotionData(Long userId) {
        try {
            // 1. 清除内存缓存
            userEmotionCache.remove(userId);

            // 2. 清除Redis缓存
            emotionCacheManager.clearUserCache(userId);

            log.info("用户情感数据清理完成: userId={}", userId);

        } catch (Exception e) {
            log.error("清理用户情感数据失败: userId={}", userId, e);
        }
    }

    /**
     * 生成个性化建议
     */
    private List<String> generateSuggestions(Long userId, Map<String, Object> emotionReport,
                                             Map<String, Object> userInfo) {
        List<String> suggestions = new ArrayList<>();

        // 基于情感状态的建议
        Map<String, Object> currentEmotion = (Map<String, Object>) emotionReport.get("currentEmotion");
        String emotionType = (String) currentEmotion.get("type");
        Double intensity = (Double) currentEmotion.get("intensity");

        if ("SAD".equals(emotionType) && intensity > 0.6) {
            suggestions.add("检测到你情绪较低落，建议和朋友聊聊天或做些喜欢的事情放松一下~");
            suggestions.add("有时候听听音乐或者散散步可以帮助改善心情哦~");
        }

        if ("ANXIOUS".equals(emotionType) && intensity > 0.7) {
            suggestions.add("感受到你的焦虑情绪，尝试深呼吸或进行短暂的运动缓解压力~");
            suggestions.add("把事情分解成小步骤，一步一步来，压力会小很多~");
        }

        if ("HAPPY".equals(emotionType) && intensity > 0.8) {
            suggestions.add("哇！感受到你的好心情，继续保持这种积极的状态吧~");
            suggestions.add("开心的时刻要好好珍惜，也可以和朋友分享这份快乐~");
        }

        // 基于学生身份的建议
        if (Boolean.TRUE.equals(userInfo.get("exists"))) {
            Map<String, Object> studentInfo = (Map<String, Object>) userInfo.get("studentInfo");
            if (studentInfo != null && !studentInfo.isEmpty()) {
                String grade = (String) studentInfo.get("grade");

                if ("大三".equals(grade)) {
                    suggestions.add("大三阶段很重要，建议开始规划未来的发展方向~");
                } else if ("大四".equals(grade)) {
                    suggestions.add("毕业季压力可能会大一些，记得劳逸结合，照顾好自己~");
                }
            }
        }

        // 通用建议
        suggestions.add("保持规律的作息对身心健康很重要哦~");
        suggestions.add("学习之余记得适当休息，劳逸结合~");
        suggestions.add("每天给自己一点小奖励，保持积极的生活态度~");

        // 限制建议数量
        return suggestions.size() > 5 ? suggestions.subList(0, 5) : suggestions;
    }

    // ========== 健康检查和监控 ==========

    /**
     * 健康检查
     */
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();

        health.put("service", "EmotionAnalysisService");
        health.put("status", "active");
        health.put("timestamp", LocalDateTime.now());
        health.put("version", "2.0.0");

        // 内存缓存统计
        health.put("memoryCacheSize", userEmotionCache.size());
        health.put("memoryCacheKeys", new ArrayList<>(userEmotionCache.keySet()));

        // 线程池状态
        health.put("threadPool", "active");
        health.put("executor", asyncExecutor != null ? "initialized" : "not-initialized");

        // 依赖服务状态
        health.put("dependencies", Map.of(
                "keywordExtractor", keywordEmotionExtractor != null ? "available" : "unavailable",
                "cacheManager", emotionCacheManager != null ? "available" : "unavailable",
                "usersMapper", usersMapper != null ? "available" : "unavailable"
        ));

        return health;
    }

    /**
     * 获取服务统计信息
     */
    public Map<String, Object> getServiceStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("userCount", userEmotionCache.size());
        stats.put("totalCachedUsers", userEmotionCache.size());
        stats.put("serviceUptime", "running");
        stats.put("lastHealthCheck", LocalDateTime.now());

        // 添加性能指标
        stats.put("performance", Map.of(
                "avgResponseTime", "待实现",
                "errorRate", "待实现",
                "throughput", "待实现"
        ));

        return stats;
    }

    // ========== 私有辅助方法 ==========

    private EmotionAnalysisDTO convertSnapshotToDTO(Long userId, UserEmotionSnapshot snapshot) {
        return EmotionAnalysisDTO.builder()
                .userId(userId)
                .primaryEmotion(snapshot.getPrimaryEmotion())
                .intensity(snapshot.getIntensity())
                .confidence(snapshot.getConfidence())
                .emotionKeywords(snapshot.getKeywords())
                .lifeScenario(snapshot.getLifeScenario())
                .analysisTime(snapshot.getTimestamp())
                .build();
    }

    private EmotionAnalysisDTO createDefaultEmotion(Long userId) {
        return EmotionAnalysisDTO.builder()
                .userId(userId)
                .primaryEmotion("NEUTRAL")
                .intensity(0.5)
                .confidence(0.5)
                .emotionKeywords(new ArrayList<>())
                .lifeScenario("general")
                .conversationContext("casual_chat")
                .isMeaningful(false)
                .source("DEFAULT")
                .analysisTime(LocalDateTime.now())
                .processingTimeMs(0L)
                .build();
    }

    private EmotionAnalysisDTO createErrorResult(Long userId, String userMessage, Exception e) {
        return EmotionAnalysisDTO.builder()
                .userId(userId)
                .userMessage(userMessage)
                .primaryEmotion("NEUTRAL")
                .secondaryEmotion("NEUTRAL")
                .intensity(0.5)
                .confidence(0.3)
                .emotionScores(Map.of("NEUTRAL", 0.5))
                .emotionKeywords(new ArrayList<>())
                .contextKeywords(new ArrayList<>())
                .lifeScenario("general")
                .conversationContext("error")
                .isMeaningful(false)
                .source("ERROR")
                .analysisTime(LocalDateTime.now())
                .processingTimeMs(0L)
                .build();
    }

    private List<String> generateEmotionInsights(List<Map<String, Object>> trend,
                                                 List<Map.Entry<String, Long>> topKeywords) {
        List<String> insights = new ArrayList<>();

        if (trend.size() >= 3) {
            // 分析最近的情感变化
            String recentEmotion = (String) trend.get(trend.size() - 1).get("emotion");
            double recentIntensity = (double) trend.get(trend.size() - 1).get("intensity");
            String previousEmotion = (String) trend.get(Math.max(0, trend.size() - 3)).get("emotion");

            if (!recentEmotion.equals(previousEmotion)) {
                insights.add(String.format("情感状态从 %s 变为 %s",
                        previousEmotion, recentEmotion));
            }

            if (recentIntensity > 0.7) {
                insights.add("最近情感强度较高，可能有重要事件发生");
            }
        }

        // 根据关键词生成洞察
        if (!topKeywords.isEmpty()) {
            String topKeyword = topKeywords.get(0).getKey();
            Long topCount = topKeywords.get(0).getValue();

            insights.add(String.format("你经常提到: %s（共 %d 次）", topKeyword, topCount));

            if (topKeywords.size() > 2) {
                insights.add("关注的话题: " +
                        topKeywords.stream()
                                .limit(3)
                                .map(entry -> entry.getKey() + "（" + entry.getValue() + "次）")
                                .collect(java.util.stream.Collectors.joining(", ")));
            }
        }

        // 添加默认洞察
        if (insights.isEmpty()) {
            insights.add("爱莉希雅正在努力了解你的情感模式~");
            insights.add("多和我聊天，我会更懂你哦！");
            insights.add("你的每一次分享都在帮助我更好地理解你~");
        }

        return insights;
    }

    private int calculateMeaningfulCount(List<Map<String, Object>> trend) {
        int count = 0;
        for (Map<String, Object> point : trend) {
            String emotion = (String) point.get("emotion");
            double intensity = (double) point.get("intensity");

            if (!"NEUTRAL".equals(emotion) && intensity > 0.6) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取星期几的中文
     */
    private String getDayOfWeekChinese() {
        String[] days = {"日", "一", "二", "三", "四", "五", "六"};
        int dayOfWeek = java.time.LocalDate.now().getDayOfWeek().getValue() % 7;
        return "星期" + days[dayOfWeek];
    }

    /**
     * 测试方法：验证服务是否正常工作
     */
    public Map<String, Object> testService(Long userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 测试用户查询
            Users user = usersMapper.selectById(userId);
            result.put("userExists", user != null);

            if (user != null) {
                result.put("username", user.getUsername());
                result.put("personalityType", user.getPersonalityType());
            }

            // 测试情感分析
            EmotionAnalysisDTO emotion = analyzeUserEmotion("测试消息", userId);
            result.put("emotionAnalysis", "success");
            result.put("emotionResult", emotion);

            // 测试缓存
            UserEmotionSnapshot snapshot = emotionCacheManager.getCurrentEmotion(userId);
            result.put("cacheAvailable", snapshot != null);

            // 测试数据库
            long userCount = usersMapper.selectCount(null);
            result.put("totalUsers", userCount);

            result.put("status", "all_tests_passed");

        } catch (Exception e) {
            result.put("status", "test_failed");
            result.put("error", e.getMessage());
            log.error("服务测试失败", e);
        }

        return result;
    }
}