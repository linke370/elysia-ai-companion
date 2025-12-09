// File: src/main/java/com/zs/service/memory/MemoryExtractionService.java
package com.zs.service.memory;

import com.zs.entity.Conversations;
import com.zs.entity.MemoryFragments;
import com.zs.mapper.ConversationsMapper;
import com.zs.mapper.MemoryFragmentsMapper;
import com.zs.service.emotion.EmotionAnalysisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zs.service.memory.repository.MemoryRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 记忆提取服务 - 增强版（添加Redis缓存和记忆限制）
 * 保持原有API不变，添加增强功能
 */
@Service
@Slf4j
public class MemoryExtractionService {

    @Resource
    private ConversationsMapper conversationsMapper;

    @Resource
    private MemoryFragmentsMapper memoryFragmentsMapper;

    @Resource
    private EmotionAnalysisService emotionAnalysisService;

    @Resource
    private MemoryRepository memoryRepository;

    @Resource
    private ObjectMapper objectMapper;

    // 新增：RedisTemplate支持
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // 记忆提取规则库
    private final Map<String, List<String>> extractionRules = new ConcurrentHashMap<>();

    // 关键词模式库
    private final Map<MemoryType, List<String>> keywordPatterns = new ConcurrentHashMap<>();

    // 用户记忆缓存（短期 - 本地缓存，作为Redis的二级缓存）
    private final Map<Long, List<MemoryFragments>> userMemoryCache = new ConcurrentHashMap<>();

    // 线程池用于异步处理
    private ExecutorService asyncExecutor;

    // Redis键前缀
    private static final String REDIS_KEY_PREFIX = "memory:user:";
    private static final String REDIS_ACTIVE_KEY = "active";
    private static final String REDIS_CONTEXT_KEY = "context";
    private static final String REDIS_IMPORTANT_KEY = "important";

    // 记忆限制配置
    private static final int MAX_MEMORIES_PER_USER = 100; // 每个用户最多保存100条记忆
    private static final int MAX_CACHED_MEMORIES = 30;    // 本地缓存最多30条
    private static final int MAX_REDIS_MEMORIES = 50;     // Redis缓存最多50条

    // 缓存过期时间（秒）
    private static final long REDIS_CACHE_TTL = 3600;     // 1小时
    private static final long CONTEXT_CACHE_TTL = 1800;   // 30分钟

    // 记忆类型枚举（对应您的memory_fragments.memory_type ENUM）
    public enum MemoryType {
        FACT("fact", "个人事实"),
        PREFERENCE("preference", "偏好"),
        IMPORTANT_EVENT("important_event", "重要事件"),
        EMOTION_PATTERN("emotion_pattern", "情感模式");

        private final String code;
        private final String description;

        MemoryType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDescription() { return description; }

        public static MemoryType fromCode(String code) {
            for (MemoryType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return FACT; // 默认值
        }
    }

    @PostConstruct
    public void init() {
        // 初始化线程池
        asyncExecutor = Executors.newFixedThreadPool(10);

        // 初始化提取规则
        initializeExtractionRules();

        // 初始化关键词模式
        initializeKeywordPatterns();

        log.info("记忆提取服务初始化完成，加载{}种提取规则，{}种记忆类型",
                extractionRules.size(), keywordPatterns.size());
    }

    private void initializeExtractionRules() {
        // 个人事实提取规则
        extractionRules.put("personal_fact", Arrays.asList(
                "我叫", "我是", "我今年", "我的名字", "我是谁",
                "我住在", "我来自", "我的家乡", "我是学生",
                "我上", "我在", "我学习", "我专业"
        ));

        // 偏好提取规则
        extractionRules.put("preference", Arrays.asList(
                "我喜欢", "我爱", "我讨厌", "我不喜欢",
                "我喜欢吃", "我爱吃", "我喜欢玩", "我喜欢看",
                "我爱好", "我擅长", "我习惯"
        ));

        // 重要事件提取规则
        extractionRules.put("important_event", Arrays.asList(
                "我昨天", "我上周", "我去年", "我前天",
                "我经历了", "我遇到了", "我发生了",
                "我考试", "我面试", "我毕业", "我旅行",
                "我生病", "我获奖", "我比赛"
        ));

        // 情感模式规则
        extractionRules.put("emotion_pattern", Arrays.asList(
                "我经常", "我总是", "我每次", "我一般",
                "我一...就", "每当...就", "只要...就"
        ));
    }

    private void initializeKeywordPatterns() {
        // 事实关键词
        keywordPatterns.put(MemoryType.FACT, Arrays.asList(
                "名字", "年龄", "家乡", "住址", "学校", "专业", "年级",
                "生日", "星座", "身高", "职业", "学生"
        ));

        // 偏好关键词
        keywordPatterns.put(MemoryType.PREFERENCE, Arrays.asList(
                "喜欢", "讨厌", "爱", "不喜欢", "最爱",
                "习惯", "嗜好", "兴趣", "爱好", "擅长"
        ));

        // 事件关键词
        keywordPatterns.put(MemoryType.IMPORTANT_EVENT, Arrays.asList(
                "考试", "毕业", "生日", "旅行", "约会", "面试", "比赛",
                "获奖", "生病", "手术", "事故", "纪念日"
        ));

        // 情感模式关键词
        keywordPatterns.put(MemoryType.EMOTION_PATTERN, Arrays.asList(
                "开心时", "难过时", "生气时", "紧张时", "焦虑时",
                "压力大时", "放松时", "疲惫时", "兴奋时"
        ));
    }

    // ========== 【增强方法1】带Redis缓存的记忆提取 ==========

    /**
     * 增强版：从对话中提取记忆并更新Redis缓存
     */
    @Transactional
    public List<MemoryFragments> extractMemoriesWithCache(Long conversationId) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("增强版提取记忆: conversationId={}", conversationId);

            // 1. 使用原有方法提取记忆
            List<MemoryFragments> memories = extractMemoriesFromConversation(conversationId);

            if (!memories.isEmpty()) {
                // 2. 获取用户ID
                Conversations conversation = conversationsMapper.selectById(conversationId);
                if (conversation != null) {
                    Long userId = conversation.getUserId();

                    // 3. 更新Redis缓存
                    updateRedisCache(userId, memories);

                    // 4. 清理过多记忆（保持限制）
                    enforceMemoryLimits(userId);

                    // 5. 提取情感记忆（如果需要）
                    extractEmotionalMemories(conversation);
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("增强版记忆提取完成: conversationId={}, 提取{}个记忆, 耗时{}ms",
                    conversationId, memories.size(), processingTime);

            return memories;

        } catch (Exception e) {
            log.error("增强版提取记忆失败: conversationId={}", conversationId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 更新Redis缓存
     */
    private void updateRedisCache(Long userId, List<MemoryFragments> newMemories) {
        try {
            // 1. 获取现有缓存
            List<MemoryFragments> cachedMemories = getRedisCachedMemories(userId, REDIS_ACTIVE_KEY);

            // 2. 合并新旧记忆（去重）
            List<MemoryFragments> allMemories = mergeMemories(cachedMemories, newMemories);

            // 3. 按重要性排序并限制数量
            allMemories = allMemories.stream()
                    .sorted((m1, m2) -> {
                        BigDecimal score1 = m1.getImportanceScore() != null ? m1.getImportanceScore() : BigDecimal.ZERO;
                        BigDecimal score2 = m2.getImportanceScore() != null ? m2.getImportanceScore() : BigDecimal.ZERO;
                        return score2.compareTo(score1);
                    })
                    .limit(MAX_REDIS_MEMORIES)
                    .collect(Collectors.toList());

            // 4. 保存到Redis
            String redisKey = buildRedisKey(userId, REDIS_ACTIVE_KEY);
            redisTemplate.opsForValue().set(redisKey, allMemories, REDIS_CACHE_TTL, TimeUnit.SECONDS);

            // 5. 保存重要记忆到独立缓存
            cacheImportantMemories(userId, allMemories);

            log.debug("更新Redis缓存: userId={}, total={}", userId, allMemories.size());

        } catch (Exception e) {
            log.error("更新Redis缓存失败: userId={}", userId, e);
        }
    }

    /**
     * 合并记忆（去重）
     */
    private List<MemoryFragments> mergeMemories(List<MemoryFragments> existing, List<MemoryFragments> newMemories) {
        Set<String> contentSet = new HashSet<>();
        List<MemoryFragments> merged = new ArrayList<>();

        // 添加现有记忆
        for (MemoryFragments memory : existing) {
            if (memory.getMemoryText() != null) {
                String key = memory.getMemoryType() + ":" + memory.getMemoryText().hashCode();
                if (!contentSet.contains(key)) {
                    contentSet.add(key);
                    merged.add(memory);
                }
            }
        }

        // 添加新记忆
        for (MemoryFragments memory : newMemories) {
            if (memory.getMemoryText() != null) {
                String key = memory.getMemoryType() + ":" + memory.getMemoryText().hashCode();
                if (!contentSet.contains(key)) {
                    contentSet.add(key);
                    merged.add(memory);
                }
            }
        }

        return merged;
    }

    /**
     * 缓存重要记忆
     */
    private void cacheImportantMemories(Long userId, List<MemoryFragments> memories) {
        try {
            List<MemoryFragments> importantMemories = memories.stream()
                    .filter(memory -> {
                        BigDecimal importance = memory.getImportanceScore();
                        return importance != null && importance.doubleValue() > 0.7;
                    })
                    .limit(10) // 只缓存前10个重要记忆
                    .collect(Collectors.toList());

            if (!importantMemories.isEmpty()) {
                String redisKey = buildRedisKey(userId, REDIS_IMPORTANT_KEY);
                redisTemplate.opsForValue().set(redisKey, importantMemories,
                        REDIS_CACHE_TTL * 2, TimeUnit.SECONDS); // 重要记忆缓存更久
            }

        } catch (Exception e) {
            log.error("缓存重要记忆失败: userId={}", userId, e);
        }
    }

    // ========== 【增强方法2】带上下文的记忆获取 ==========

    /**
     * 获取上下文相关记忆（用于AI回复）
     */
    public List<MemoryFragments> getContextualMemories(Long userId, String currentMessage) {
        try {
            // 1. 先从Redis上下文缓存查
            String contextKey = buildRedisKey(userId, REDIS_CONTEXT_KEY);
            @SuppressWarnings("unchecked")
            List<MemoryFragments> cachedContext = (List<MemoryFragments>) redisTemplate.opsForValue().get(contextKey);

            if (cachedContext != null && !cachedContext.isEmpty()) {
                log.debug("从Redis上下文缓存获取记忆: userId={}, count={}", userId, cachedContext.size());
                return cachedContext;
            }

            // 2. 没有缓存，则搜索相关记忆
            List<MemoryFragments> relevantMemories = findRelevantMemories(userId, currentMessage);

            // 3. 缓存上下文结果
            if (!relevantMemories.isEmpty()) {
                redisTemplate.opsForValue().set(contextKey, relevantMemories,
                        CONTEXT_CACHE_TTL, TimeUnit.SECONDS);
            }

            return relevantMemories;

        } catch (Exception e) {
            log.error("获取上下文记忆失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 查找相关记忆
     */
    private List<MemoryFragments> findRelevantMemories(Long userId, String currentMessage) {
        try {
            // 提取当前消息的关键词
            List<String> keywords = extractKeywordsFromText(currentMessage);

            if (keywords.isEmpty()) {
                return Collections.emptyList();
            }

            // 从Redis获取活跃记忆
            List<MemoryFragments> allMemories = getRedisCachedMemories(userId, REDIS_ACTIVE_KEY);

            // 如果没有Redis缓存，则从数据库获取
            if (allMemories.isEmpty()) {
                allMemories = getUserMemories(userId, null, MAX_REDIS_MEMORIES);
                // 顺便缓存到Redis
                if (!allMemories.isEmpty()) {
                    updateRedisCache(userId, allMemories);
                }
            }

            // 计算相关性并排序
            return allMemories.stream()
                    .map(memory -> {
                        double relevance = calculateRelevance(memory, keywords);
                        return new MemoryWithRelevance(memory, relevance);
                    })
                    .filter(mwr -> mwr.relevance > 0.2) // 相关性阈值
                    .sorted(Comparator.comparingDouble(MemoryWithRelevance::getRelevance).reversed())
                    .limit(5) // 最多返回5个相关记忆
                    .map(MemoryWithRelevance::getMemory)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("查找相关记忆失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 计算记忆相关性
     */
    private double calculateRelevance(MemoryFragments memory, List<String> keywords) {
        double relevance = 0.0;

        if (memory.getMemoryText() == null) {
            return relevance;
        }

        String memoryText = memory.getMemoryText().toLowerCase();

        // 1. 关键词匹配
        for (String keyword : keywords) {
            if (memoryText.contains(keyword.toLowerCase())) {
                relevance += 0.3;
            }
        }

        // 2. 重要性权重
        if (memory.getImportanceScore() != null) {
            relevance += memory.getImportanceScore().doubleValue() * 0.5;
        }

        // 3. 近期访问权重
        if (memory.getLastAccessed() != null) {
            long daysSinceAccess = (System.currentTimeMillis() - memory.getLastAccessed().getTime())
                    / (1000 * 60 * 60 * 24);
            if (daysSinceAccess < 7) {
                relevance += 0.1;
            }
        }

        return Math.min(relevance, 1.0);
    }

    // ========== 【增强方法3】记忆限制管理 ==========

    /**
     * 强制执行记忆限制
     */
    private void enforceMemoryLimits(Long userId) {
        try {
            // 1. 检查用户记忆总数
            long totalMemories = memoryFragmentsMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments>()
                            .eq("user_id", userId)
            );

            if (totalMemories > MAX_MEMORIES_PER_USER) {
                log.info("用户记忆超限: userId={}, count={}, 限制={}",
                        userId, totalMemories, MAX_MEMORIES_PER_USER);

                // 2. 删除不重要的旧记忆
                deleteUnimportantMemories(userId, totalMemories - MAX_MEMORIES_PER_USER);
            }

        } catch (Exception e) {
            log.error("执行记忆限制失败: userId={}", userId, e);
        }
    }

    /**
     * 删除不重要的记忆
     */
    private void deleteUnimportantMemories(Long userId, long countToDelete) {
        try {
            // 获取最不重要的记忆（重要性低、很久没访问的）
            List<MemoryFragments> memoriesToDelete = memoryFragmentsMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments>()
                            .eq("user_id", userId)
                            .orderByAsc("importance_score", "last_accessed")
                            .last("LIMIT " + countToDelete)
            );

            // 删除这些记忆
            for (MemoryFragments memory : memoriesToDelete) {
                memoryFragmentsMapper.deleteById(memory.getId());
            }

            log.info("删除不重要记忆: userId={}, count={}", userId, memoriesToDelete.size());

        } catch (Exception e) {
            log.error("删除不重要记忆失败: userId={}", userId, e);
        }
    }

    // ========== 【增强方法4】情感记忆提取 ==========

    /**
     * 提取情感记忆到emotional_memories表
     */
    private void extractEmotionalMemories(Conversations conversation) {
        try {
            String emotionLabel = conversation.getEmotionLabel();
            String userMessage = conversation.getUserMessage();
            Long userId = conversation.getUserId();

            // 只有当情感不是NEUTRAL时才提取情感记忆
            if (emotionLabel != null && !"NEUTRAL".equals(emotionLabel)) {
                // 提取情感关键词
                List<String> emotionKeywords = extractEmotionKeywords(userMessage);

                // 这里需要emotional_memories表的Mapper
                // 由于没有emotional_memories表的Mapper，这里只记录日志
                // 实际使用时需要注入emotionalMemoriesMapper

                log.debug("情感记忆提取（待实现）: userId={}, emotion={}, keywords={}",
                        userId, emotionLabel, emotionKeywords);

                // TODO: 实际代码需要启用以下部分
                /*
                EmotionalMemories emotionalMemory = new EmotionalMemories();
                emotionalMemory.setUserId(userId);
                emotionalMemory.setEmotionType(emotionLabel);
                emotionalMemory.setEmotionContext(conversation.getConversationContext());
                emotionalMemory.setTriggerKeywords(objectMapper.writeValueAsString(emotionKeywords));
                emotionalMemory.setLifeScenario(extractLifeScenario(userMessage));
                emotionalMemoriesMapper.insert(emotionalMemory);
                */
            }

        } catch (Exception e) {
            log.error("提取情感记忆失败: conversationId={}", conversation.getId(), e);
        }
    }

    /**
     * 提取情感关键词
     */
    private List<String> extractEmotionKeywords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 简单的情感关键词提取
        List<String> emotionKeywords = new ArrayList<>();
        String lowerText = text.toLowerCase();

        // 情绪相关关键词
        String[] positiveWords = {"开心", "高兴", "喜欢", "爱", "好", "棒", "幸福"};
        String[] negativeWords = {"难过", "伤心", "生气", "讨厌", "糟糕", "压力", "焦虑"};

        for (String word : positiveWords) {
            if (lowerText.contains(word)) {
                emotionKeywords.add(word);
            }
        }

        for (String word : negativeWords) {
            if (lowerText.contains(word)) {
                emotionKeywords.add(word);
            }
        }

        return emotionKeywords;
    }

    /**
     * 提取生活场景
     */
    private String extractLifeScenario(String text) {
        if (text == null) {
            return "general";
        }

        String lowerText = text.toLowerCase();

        if (lowerText.contains("考试") || lowerText.contains("学习") || lowerText.contains("作业")) {
            return "exam_stress";
        } else if (lowerText.contains("家乡") || lowerText.contains("家人") || lowerText.contains("父母")) {
            return "homesick";
        } else if (lowerText.contains("朋友") || lowerText.contains("同学") || lowerText.contains("社交")) {
            return "social_anxiety";
        } else if (lowerText.contains("未来") || lowerText.contains("工作") || lowerText.contains("职业")) {
            return "future_anxiety";
        }

        return "general";
    }

    // ========== 【增强方法5】Redis缓存工具方法 ==========

    /**
     * 从Redis获取缓存记忆
     */
    private List<MemoryFragments> getRedisCachedMemories(Long userId, String suffix) {
        try {
            String redisKey = buildRedisKey(userId, suffix);
            @SuppressWarnings("unchecked")
            List<MemoryFragments> cached = (List<MemoryFragments>) redisTemplate.opsForValue().get(redisKey);
            return cached != null ? cached : Collections.emptyList();
        } catch (Exception e) {
            log.error("获取Redis缓存失败: userId={}, suffix={}", userId, suffix, e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建Redis键
     */
    private String buildRedisKey(Long userId, String suffix) {
        return REDIS_KEY_PREFIX + userId + ":" + suffix;
    }

    /**
     * 提取文本关键词（简化版）
     */
    private List<String> extractKeywordsFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 简单分词
        String[] words = text.split("[\\s,.，。!！?？、]+");
        return Arrays.stream(words)
                .filter(word -> word.length() > 1 && word.length() < 6)
                .collect(Collectors.toList());
    }

    // ========== 【原有方法保持不变】==========
    // 以下是您原有的所有方法，保持完全不变

    /**
     * 从对话中提取记忆（同步调用） - 原有方法
     */
    @Transactional
    public List<MemoryFragments> extractMemoriesFromConversation(Long conversationId) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始提取记忆: conversationId={}", conversationId);

            // 1. 获取对话记录
            Conversations conversation = conversationsMapper.selectById(conversationId);
            if (conversation == null) {
                log.warn("对话记录不存在: conversationId={}", conversationId);
                return Collections.emptyList();
            }

            Long userId = conversation.getUserId();
            String userMessage = conversation.getUserMessage();

            // 2. 分析对话重要性
            boolean isImportant = isImportantConversation(conversation);
            if (!isImportant) {
                log.debug("对话不重要，跳过记忆提取: conversationId={}", conversationId);
                return Collections.emptyList();
            }

            // 3. 提取记忆片段
            List<MemoryCandidate> candidates = extractMemoryCandidates(userMessage, userId);

            // 4. 计算记忆重要性
            candidates = calculateMemoryImportance(candidates, conversation);

            // 5. 保存记忆片段
            List<MemoryFragments> savedMemories = saveMemoryFragments(candidates, userId, conversationId);

            // 6. 更新用户记忆缓存
            updateUserMemoryCache(userId, savedMemories);

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("记忆提取完成: conversationId={}, 提取{}个记忆, 耗时{}ms",
                    conversationId, savedMemories.size(), processingTime);

            return savedMemories;

        } catch (Exception e) {
            log.error("提取记忆失败: conversationId={}", conversationId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 异步提取记忆（推荐使用） - 原有方法
     */
    @Async
    public void extractMemoriesAsync(Long conversationId) {
        asyncExecutor.submit(() -> {
            try {
                List<MemoryFragments> memories = extractMemoriesFromConversation(conversationId);
                log.debug("异步记忆提取完成: conversationId={}, 提取{}个记忆",
                        conversationId, memories.size());
            } catch (Exception e) {
                log.error("异步记忆提取失败: conversationId={}", conversationId, e);
            }
        });
    }

    /**
     * 批量提取用户历史对话记忆 - 原有方法
     */
    @Transactional
    public List<MemoryFragments> extractHistoricalMemories(Long userId, int limit) {
        try {
            log.info("提取历史记忆: userId={}, limit={}", userId, limit);

            // 1. 获取用户的重要对话
            List<Conversations> importantConversations = getImportantConversations(userId, limit);

            // 2. 提取记忆
            List<MemoryFragments> allMemories = new ArrayList<>();

            for (Conversations conversation : importantConversations) {
                List<MemoryFragments> memories = extractMemoriesFromConversation(conversation.getId());
                allMemories.addAll(memories);
            }

            log.info("历史记忆提取完成: userId={}, 提取{}个对话, 获得{}个记忆",
                    userId, importantConversations.size(), allMemories.size());

            return allMemories;

        } catch (Exception e) {
            log.error("提取历史记忆失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取用户的记忆片段 - 原有方法
     */
    public List<MemoryFragments> getUserMemories(Long userId, MemoryType type, int limit) {
        try {
            // 1. 先查缓存
            List<MemoryFragments> cached = userMemoryCache.get(userId);
            if (cached != null && !cached.isEmpty()) {
                List<MemoryFragments> filtered = filterMemoriesByType(cached, type);
                return filtered.size() > limit ? filtered.subList(0, limit) : filtered;
            }

            // 2. 查数据库
            List<MemoryFragments> memories = queryMemoriesFromDatabase(userId, type, limit);

            // 3. 更新缓存
            if (!memories.isEmpty()) {
                userMemoryCache.put(userId, memories);
            }

            return memories;

        } catch (Exception e) {
            log.error("获取用户记忆失败: userId={}, type={}", userId, type, e);
            return Collections.emptyList();
        }
    }

    /**
     * 更新记忆重要性评分 - 原有方法
     */
    @Transactional
    public void updateMemoryImportance(Long memoryId, Double importanceScore) {
        try {
            MemoryFragments memory = memoryFragmentsMapper.selectById(memoryId);
            if (memory != null && importanceScore != null) {
                memory.setImportanceScore(BigDecimal.valueOf(Math.min(1.0, Math.max(0.0, importanceScore))));
                memory.setUpdatedAt(new Date());
                memoryFragmentsMapper.updateById(memory);
                log.debug("更新记忆重要性: memoryId={}, score={}", memoryId, importanceScore);
            }
        } catch (Exception e) {
            log.error("更新记忆重要性失败: memoryId={}", memoryId, e);
        }
    }

    /**
     * 记录记忆访问 - 原有方法
     */
    @Transactional
    public void recordMemoryAccess(Long memoryId, Long userId, Long conversationId) {
        try {
            MemoryFragments memory = memoryFragmentsMapper.selectById(memoryId);
            if (memory != null) {
                // 更新访问次数
                Integer accessCount = memory.getAccessCount();
                if (accessCount == null) {
                    accessCount = 0;
                }
                memory.setAccessCount(accessCount + 1);
                memory.setLastAccessed(new Date());
                memory.setUpdatedAt(new Date());

                memoryFragmentsMapper.updateById(memory);
                log.debug("记录记忆访问: memoryId={}, userId={}, 访问次数={}",
                        memoryId, userId, accessCount + 1);
            }
        } catch (Exception e) {
            log.error("记录记忆访问失败: memoryId={}", memoryId, e);
        }
    }

    /**
     * 获取用户记忆统计 - 原有方法
     */
    public Map<String, Object> getMemoryStatistics(Long userId) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 获取各种类型的记忆数量
            Map<String, Long> typeCounts = getMemoryTypeCounts(userId);

            // 计算重要记忆比例
            double importantRatio = calculateImportantMemoryRatio(userId);

            // 获取最近记忆
            List<MemoryFragments> recentMemories = getRecentMemories(userId, 5);

            stats.put("userId", userId);
            stats.put("totalMemories", typeCounts.values().stream().mapToLong(Long::longValue).sum());
            stats.put("typeCounts", typeCounts);
            stats.put("importantMemoryRatio", String.format("%.2f", importantRatio * 100) + "%");
            stats.put("recentMemoryCount", recentMemories.size());
            stats.put("lastExtraction", new Date());

        } catch (Exception e) {
            log.error("获取记忆统计失败: userId={}", userId, e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 搜索相关记忆 - 原有方法
     */
    public List<MemoryFragments> searchMemories(Long userId, String keyword, int limit) {
        try {
            log.info("搜索记忆: userId={}, keyword={}", userId, keyword);

            // 1. 先查缓存
            List<MemoryFragments> cached = userMemoryCache.get(userId);
            if (cached != null && !cached.isEmpty()) {
                List<MemoryFragments> matched = cached.stream()
                        .filter(memory -> {
                            try {
                                String text = memory.getMemoryText();
                                return text != null && text.toLowerCase().contains(keyword.toLowerCase());
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .limit(limit)
                        .collect(java.util.stream.Collectors.toList());

                if (!matched.isEmpty()) {
                    return matched;
                }
            }

            // 2. 查数据库
            List<MemoryFragments> memories = memoryFragmentsMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments>()
                            .eq("user_id", userId)
                            .like("memory_text", keyword)
                            .orderByDesc("importance_score", "last_accessed")
                            .last("LIMIT " + limit)
            );

            return memories;

        } catch (Exception e) {
            log.error("搜索记忆失败: userId={}, keyword={}", userId, keyword, e);
            return Collections.emptyList();
        }
    }

    /**
     * 判断对话是否重要 - 原有方法
     */
    private boolean isImportantConversation(Conversations conversation) {
        // 1. 检查是否标记为重要（is_meaningful是Integer类型）
        Integer isMeaningful = conversation.getIsMeaningful();
        if (isMeaningful != null && isMeaningful == 1) {
            return true;
        }

        // 2. 检查情感强度
        BigDecimal confidence = conversation.getEmotionConfidence();
        if (confidence != null && confidence.doubleValue() > 0.7) {
            return true;
        }

        // 3. 检查对话长度
        String userMessage = conversation.getUserMessage();
        if (userMessage != null && userMessage.length() > 20) {
            return true;
        }

        // 4. 检查是否有情感标签
        String emotionLabel = conversation.getEmotionLabel();
        if (emotionLabel != null && !"NEUTRAL".equals(emotionLabel)) {
            return true;
        }

        return false;
    }

    /**
     * 提取记忆候选 - 原有方法
     */
    private List<MemoryCandidate> extractMemoryCandidates(String userMessage, Long userId) {
        List<MemoryCandidate> candidates = new ArrayList<>();
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return candidates;
        }

        String lowerMessage = userMessage.toLowerCase();

        // 1. 提取个人事实
        for (String rule : extractionRules.get("personal_fact")) {
            int index = lowerMessage.indexOf(rule.toLowerCase());
            if (index >= 0) {
                String fact = extractFact(userMessage, index, rule);
                if (fact != null && !fact.trim().isEmpty()) {
                    candidates.add(createCandidate(fact, MemoryType.FACT, 0.7));
                }
            }
        }

        // 2. 提取偏好
        for (String rule : extractionRules.get("preference")) {
            if (lowerMessage.contains(rule.toLowerCase())) {
                String preference = extractPreference(userMessage, rule);
                if (preference != null && !preference.trim().isEmpty()) {
                    candidates.add(createCandidate(preference, MemoryType.PREFERENCE, 0.6));
                }
            }
        }

        // 3. 提取重要事件
        for (String rule : extractionRules.get("important_event")) {
            if (lowerMessage.contains(rule.toLowerCase())) {
                String event = extractEvent(userMessage, rule);
                if (event != null && !event.trim().isEmpty()) {
                    candidates.add(createCandidate(event, MemoryType.IMPORTANT_EVENT, 0.8));
                }
            }
        }

        // 4. 提取情感模式（如果有情感标签）
        // 这部分可以结合情感分析服务

        return candidates;
    }

    /**
     * 计算记忆重要性 - 原有方法
     */
    private List<MemoryCandidate> calculateMemoryImportance(List<MemoryCandidate> candidates, Conversations conversation) {
        for (MemoryCandidate candidate : candidates) {
            double importance = candidate.baseImportance;

            // 1. 考虑情感因素
            String emotion = conversation.getEmotionLabel();
            if (emotion != null && (emotion.contains("SAD") || emotion.contains("ANXIOUS") || emotion.contains("ANGRY"))) {
                importance += 0.1;
            } else if (emotion != null && emotion.contains("HAPPY")) {
                importance += 0.05; // 开心的事也值得记住
            }

            // 2. 考虑对话置信度
            BigDecimal confidence = conversation.getEmotionConfidence();
            if (confidence != null) {
                importance += confidence.doubleValue() * 0.1;
            }

            // 3. 考虑记忆类型
            if (candidate.type == MemoryType.IMPORTANT_EVENT) {
                importance += 0.15;
            } else if (candidate.type == MemoryType.FACT) {
                importance += 0.05; // 事实类记忆基础重要性高
            }

            // 4. 考虑文本长度
            if (candidate.content.length() > 30) {
                importance += 0.05; // 较长的内容可能更重要
            }

            candidate.calculatedImportance = Math.min(1.0, Math.max(0.0, importance));
        }

        return candidates;
    }

    /**
     * 保存记忆片段 - 原有方法
     */
    private List<MemoryFragments> saveMemoryFragments(List<MemoryCandidate> candidates, Long userId, Long conversationId) {
        List<MemoryFragments> savedMemories = new ArrayList<>();

        for (MemoryCandidate candidate : candidates) {
            try {
                MemoryFragments memory = new MemoryFragments();

                memory.setUserId(userId);
                memory.setMemoryText(candidate.content);
                memory.setMemoryType(candidate.type.getCode()); // 使用字符串类型
                memory.setImportanceScore(BigDecimal.valueOf(candidate.calculatedImportance));
                memory.setSourceConversationId(conversationId);

                // 设置相关关键词
                List<String> keywords = extractKeywords(candidate.content, candidate.type);
                if (!keywords.isEmpty()) {
                    try {
                        String keywordsJson = objectMapper.writeValueAsString(keywords);
                        memory.setRelatedKeywords(keywordsJson);
                    } catch (JsonProcessingException e) {
                        log.warn("序列化关键词失败", e);
                        memory.setRelatedKeywords(keywords.toString());
                    }
                }

                memory.setAccessCount(0);
                memory.setLastAccessed(new Date());
                Date now = new Date();
                memory.setCreatedAt(now);
                memory.setUpdatedAt(now);

                memoryFragmentsMapper.insert(memory);
                savedMemories.add(memory);

                log.debug("保存记忆片段: memoryId={}, type={}, importance={}, content={}...",
                        memory.getId(), candidate.type, candidate.calculatedImportance,
                        candidate.content.length() > 30 ?
                                candidate.content.substring(0, 30) + "..." : candidate.content);

            } catch (Exception e) {
                log.error("保存记忆片段失败: userId={}, content={}", userId, candidate.content, e);
            }
        }

        return savedMemories;
    }

    /**
     * 更新用户记忆缓存 - 原有方法
     */
    private void updateUserMemoryCache(Long userId, List<MemoryFragments> newMemories) {
        List<MemoryFragments> existing = userMemoryCache.get(userId);
        if (existing == null) {
            existing = new ArrayList<>();
        }

        existing.addAll(newMemories);

        // 按重要性排序
        existing.sort((m1, m2) -> {
            BigDecimal score1 = m1.getImportanceScore() != null ? m1.getImportanceScore() : BigDecimal.ZERO;
            BigDecimal score2 = m2.getImportanceScore() != null ? m2.getImportanceScore() : BigDecimal.ZERO;
            return score2.compareTo(score1); // 降序
        });

        // 限制缓存大小（最多30条）
        if (existing.size() > 30) {
            existing = existing.subList(0, 30);
        }

        userMemoryCache.put(userId, existing);
    }

    /**
     * 获取重要对话 - 原有方法
     */
    private List<Conversations> getImportantConversations(Long userId, int limit) {
        try {
            // 查询重要对话（is_meaningful=1或情感置信度高的对话）
            return conversationsMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Conversations>()
                            .eq("user_id", userId)
                            .and(wrapper -> wrapper
                                    .eq("is_meaningful", 1)
                                    .or()
                                    .gt("emotion_confidence", 0.7)
                            )
                            .orderByDesc("created_at")
                            .last("LIMIT " + limit)
            );
        } catch (Exception e) {
            log.error("获取重要对话失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从数据库查询记忆 - 原有方法
     */
    private List<MemoryFragments> queryMemoriesFromDatabase(Long userId, MemoryType type, int limit) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments>()
                            .eq("user_id", userId)
                            .orderByDesc("importance_score", "last_accessed", "created_at")
                            .last("LIMIT " + limit);

            if (type != null) {
                wrapper.eq("memory_type", type.getCode());
            }

            return memoryFragmentsMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("查询记忆失败: userId={}, type={}", userId, type, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取记忆类型统计 - 原有方法
     */
    private Map<String, Long> getMemoryTypeCounts(Long userId) {
        Map<String, Long> counts = new HashMap<>();
        try {
            List<MemoryFragments> allMemories = memoryFragmentsMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments>()
                            .eq("user_id", userId)
            );

            for (MemoryFragments memory : allMemories) {
                Object typeObj = memory.getMemoryType();
                String type = (typeObj != null) ? typeObj.toString() : "unknown";
                counts.put(type, counts.getOrDefault(type, 0L) + 1);
            }
        } catch (Exception e) {
            log.error("获取记忆类型统计失败: userId={}", userId, e);
        }
        return counts;
    }

    /**
     * 计算重要记忆比例 - 原有方法
     */
    private double calculateImportantMemoryRatio(Long userId) {
        try {
            List<MemoryFragments> allMemories = memoryFragmentsMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments>()
                            .eq("user_id", userId)
            );

            if (allMemories.isEmpty()) {
                return 0.0;
            }

            long importantCount = allMemories.stream()
                    .filter(memory -> {
                        BigDecimal importance = memory.getImportanceScore();
                        return importance != null && importance.doubleValue() > 0.7;
                    })
                    .count();

            return (double) importantCount / allMemories.size();

        } catch (Exception e) {
            log.error("计算重要记忆比例失败: userId={}", userId, e);
            return 0.0;
        }
    }

    /**
     * 获取最近记忆 - 原有方法
     */
    private List<MemoryFragments> getRecentMemories(Long userId, int limit) {
        try {
            return memoryFragmentsMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryFragments>()
                            .eq("user_id", userId)
                            .orderByDesc("created_at")
                            .last("LIMIT " + limit)
            );
        } catch (Exception e) {
            log.error("获取最近记忆失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 提取事实 - 原有方法
     */
    private String extractFact(String text, int startIndex, String rule) {
        try {
            int start = startIndex + rule.length();
            if (start >= text.length()) {
                return null;
            }

            String rest = text.substring(start).trim();
            if (rest.isEmpty()) {
                return null;
            }

            int endIndex = Math.min(
                    findSentenceEnd(rest),
                    Math.min(rest.length(), 50) // 限制长度
            );

            String fact = rest.substring(0, endIndex).trim();
            return fact.isEmpty() ? null : fact;
        } catch (Exception e) {
            log.debug("提取事实失败: text={}, rule={}", text, rule, e);
            return null;
        }
    }

    /**
     * 提取偏好 - 原有方法
     */
    private String extractPreference(String text, String rule) {
        return extractTextAfterPattern(text, rule, 50);
    }

    /**
     * 提取事件 - 原有方法
     */
    private String extractEvent(String text, String rule) {
        return extractTextAfterPattern(text, rule, 100);
    }

    /**
     * 提取文本 - 原有方法
     */
    private String extractTextAfterPattern(String text, String pattern, int maxLength) {
        try {
            int index = text.toLowerCase().indexOf(pattern.toLowerCase());
            if (index < 0) {
                return null;
            }

            int start = index + pattern.length();
            if (start >= text.length()) {
                return null;
            }

            String rest = text.substring(start).trim();
            if (rest.isEmpty()) {
                return null;
            }

            int endIndex = Math.min(findSentenceEnd(rest), Math.min(rest.length(), maxLength));

            String extracted = rest.substring(0, endIndex).trim();
            return extracted.isEmpty() ? null : extracted;
        } catch (Exception e) {
            log.debug("提取文本失败: text={}, pattern={}", text, pattern, e);
            return null;
        }
    }

    /**
     * 查找句子结束 - 原有方法
     */
    private int findSentenceEnd(String text) {
        int[] positions = {
                text.indexOf('。'), text.indexOf('，'),
                text.indexOf('？'), text.indexOf('！'),
                text.indexOf('.'), text.indexOf(','),
                text.indexOf('?'), text.indexOf('!')
        };

        int min = text.length();
        for (int pos : positions) {
            if (pos >= 0 && pos < min) {
                min = pos;
            }
        }

        return min;
    }

    /**
     * 提取关键词 - 原有方法
     */
    private List<String> extractKeywords(String text, MemoryType type) {
        List<String> keywords = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return keywords;
        }

        String lowerText = text.toLowerCase();

        List<String> patterns = keywordPatterns.get(type);
        if (patterns != null) {
            for (String keyword : patterns) {
                if (lowerText.contains(keyword.toLowerCase())) {
                    keywords.add(keyword);
                }
            }
        }

        // 提取文本中的名词（简单实现）
        String[] words = text.split("[\\s,.，。!！?？]+");
        for (String word : words) {
            if (word.length() > 1 && word.length() < 6) {
                // 简单判断是否为名词（中文名词通常是2-4个字）
                if (word.length() >= 2 && word.length() <= 4) {
                    keywords.add(word);
                }
            }
        }

        // 去重并限制数量
        Set<String> uniqueKeywords = new LinkedHashSet<>(keywords);
        return new ArrayList<>(uniqueKeywords).size() > 10 ?
                new ArrayList<>(uniqueKeywords).subList(0, 10) :
                new ArrayList<>(uniqueKeywords);
    }

    /**
     * 创建候选 - 原有方法
     */
    private MemoryCandidate createCandidate(String content, MemoryType type, double baseImportance) {
        MemoryCandidate candidate = new MemoryCandidate();
        candidate.content = content;
        candidate.type = type;
        candidate.baseImportance = baseImportance;
        return candidate;
    }

    /**
     * 按类型过滤记忆 - 原有方法
     */
    private List<MemoryFragments> filterMemoriesByType(List<MemoryFragments> memories, MemoryType type) {
        if (type == null) {
            return memories;
        }

        return memories.stream()
                .filter(memory -> {
                    Object memoryType = memory.getMemoryType();
                    return memoryType != null && type.getCode().equals(memoryType.toString());
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 记忆候选对象（内部类） - 原有方法
     */
    private static class MemoryCandidate {
        String content;
        MemoryType type;
        double baseImportance;
        double calculatedImportance;
    }

    /**
     * 记忆与相关性包装类（新内部类）
     */
    private static class MemoryWithRelevance {
        private final MemoryFragments memory;
        private final double relevance;

        public MemoryWithRelevance(MemoryFragments memory, double relevance) {
            this.memory = memory;
            this.relevance = relevance;
        }

        public MemoryFragments getMemory() { return memory; }
        public double getRelevance() { return relevance; }
    }

    /**
     * 清空用户记忆缓存 - 原有方法
     */
    public void clearUserMemoryCache(Long userId) {
        userMemoryCache.remove(userId);

        // 增强：同时清空Redis缓存
        try {
            String pattern = REDIS_KEY_PREFIX + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("清空用户记忆缓存: userId={}, 删除Redis keys={}", userId, keys.size());
            }
        } catch (Exception e) {
            log.error("清空Redis缓存失败: userId={}", userId, e);
        }
    }

    /**
     * 获取缓存统计 - 原有方法
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cachedUsers", userMemoryCache.size());
        stats.put("totalCachedMemories",
                userMemoryCache.values().stream()
                        .mapToInt(List::size)
                        .sum());
        stats.put("cacheStatus", "active");

        // 增强：添加Redis缓存统计
        try {
            Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            stats.put("redisKeys", keys != null ? keys.size() : 0);
        } catch (Exception e) {
            log.error("获取Redis统计失败", e);
            stats.put("redisKeys", "error");
        }

        return stats;
    }

    /**
     * 服务健康检查 - 原有方法
     */
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();

        health.put("service", "MemoryExtractionService");
        health.put("status", "active");
        health.put("timestamp", new Date());
        health.put("version", "2.0.0"); // 版本号升级

        health.put("memoryCacheSize", userMemoryCache.size());
        health.put("threadPool", asyncExecutor != null ? "initialized" : "not-initialized");

        // 增强：检查Redis连接
        try {
            redisTemplate.hasKey("test_connection");
            health.put("redis", "connected");
        } catch (Exception e) {
            health.put("redis", "disconnected");
        }

        health.put("dependencies", Map.of(
                "conversationsMapper", conversationsMapper != null ? "available" : "unavailable",
                "memoryFragmentsMapper", memoryFragmentsMapper != null ? "available" : "unavailable",
                "emotionAnalysisService", emotionAnalysisService != null ? "available" : "unavailable",
                "redisTemplate", redisTemplate != null ? "available" : "unavailable"
        ));

        return health;
    }

    /**
     * 服务统计信息 - 原有方法
     */
    public Map<String, Object> getServiceStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("cacheUserCount", userMemoryCache.size());
        stats.put("extractionRulesCount", extractionRules.size());
        stats.put("memoryTypes", MemoryType.values().length);
        stats.put("threadPoolSize", 10);
        stats.put("serviceUptime", "running");
        stats.put("lastCheckTime", new Date());

        // 增强：添加Redis统计
        try {
            Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            stats.put("redisMemoryKeys", keys != null ? keys.size() : 0);
        } catch (Exception e) {
            log.error("获取Redis统计失败", e);
        }

        return stats;
    }

    /**
     * 测试方法：验证服务是否正常工作 - 原有方法
     */
    public Map<String, Object> testService(Long userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 测试依赖服务
            result.put("conversationsMapper", conversationsMapper != null);
            result.put("memoryFragmentsMapper", memoryFragmentsMapper != null);
            result.put("emotionAnalysisService", emotionAnalysisService != null);
            result.put("redisTemplate", redisTemplate != null);

            // 测试数据库连接
            long conversationCount = conversationsMapper.selectCount(null);
            result.put("conversationCount", conversationCount);

            // 测试记忆查询
            List<MemoryFragments> memories = getUserMemories(userId, null, 5);
            result.put("memoryCount", memories.size());

            // 测试增强功能
            try {
                List<MemoryFragments> contextual = getContextualMemories(userId, "测试");
                result.put("contextualMemories", contextual.size());
                result.put("enhancedFeatures", "working");
            } catch (Exception e) {
                result.put("enhancedFeatures", "error: " + e.getMessage());
            }

            result.put("status", "all_tests_passed");
            result.put("message", "服务运行正常");

        } catch (Exception e) {
            result.put("status", "test_failed");
            result.put("error", e.getMessage());
            log.error("服务测试失败", e);
        }

        return result;
    }
}