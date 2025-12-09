package com.zs.service.emotion.state;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.zs.entity.AIEmotionalState;
import com.zs.mapper.AIEmotionalStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * AI情感状态服务
 * 新增类，不修改现有代码
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIEmotionService {

    private final AIEmotionalStateMapper aiEmotionalStateMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // Redis键前缀
    private static final String REDIS_AI_STATE_KEY = "ai:emotion:state:user:";

    // 情感状态枚举
    public enum EmotionalState {
        NEUTRAL("平静", 0.5),
        HAPPY("开心", 0.8),
        CONCERNED("关心", 0.6),
        EXCITED("兴奋", 0.9),
        CURIOUS("好奇", 0.7),
        PLAYFUL("调皮", 0.7),
        REFLECTIVE("思考", 0.6);

        private final String chinese;
        private final double baseEnergy;

        EmotionalState(String chinese, double baseEnergy) {
            this.chinese = chinese;
            this.baseEnergy = baseEnergy;
        }

        public String getChinese() { return chinese; }
        public double getBaseEnergy() { return baseEnergy; }

        public static EmotionalState fromString(String state) {
            try {
                return valueOf(state);
            } catch (Exception e) {
                return NEUTRAL;
            }
        }
    }

    /**
     * 更新AI情感状态（基于用户情感）
     */
    public void updateAIEmotion(Long userId, String userEmotion, Double userIntensity) {
        try {
            // 1. 获取当前AI状态
            AIEmotionalState currentState = getCurrentState(userId);

            // 2. 计算新的情感状态
            AIEmotionalState newState = calculateNewState(currentState, userEmotion, userIntensity);

            // 3. 保存到数据库
            saveOrUpdateState(newState);

            // 4. 更新Redis缓存
            updateRedisCache(userId, newState);

            log.debug("更新AI情感状态: userId={}, from={} to={}",
                    userId, currentState.getCurrentState(), newState.getCurrentState());

        } catch (Exception e) {
            log.error("更新AI情感状态失败: userId={}", userId, e);
        }
    }

    /**
     * 获取当前AI情感状态
     */
    public AIEmotionalState getCurrentState(Long userId) {
        try {
            // 1. 先查Redis缓存
            String redisKey = REDIS_AI_STATE_KEY + userId;
            AIEmotionalState cached = (AIEmotionalState) redisTemplate.opsForValue().get(redisKey);

            if (cached != null) {
                return cached;
            }

            // 2. 查数据库
            AIEmotionalState state = aiEmotionalStateMapper.selectById(userId);

            if (state == null) {
                // 3. 创建默认状态
                state = createDefaultState(userId);
                saveOrUpdateState(state);
            }

            // 4. 更新Redis缓存
            updateRedisCache(userId, state);

            return state;

        } catch (Exception e) {
            log.error("获取AI情感状态失败: userId={}", userId, e);
            return createDefaultState(userId);
        }
    }

    /**
     * 获取AI情感报告
     */
    public Map<String, Object> getAIEmotionReport(Long userId) {
        Map<String, Object> report = new LinkedHashMap<>();

        try {
            AIEmotionalState state = getCurrentState(userId);
            EmotionalState emotionalState = EmotionalState.fromString(state.getCurrentState());

            report.put("userId", userId);
            report.put("currentState", state.getCurrentState());
            report.put("currentStateChinese", emotionalState.getChinese());
            report.put("energyLevel", String.format("%.0f%%", state.getEnergyLevel().doubleValue() * 100));
            report.put("lastStateChange", state.getLastStateChange());
            report.put("lastInteraction", state.getLastInteractionTime());

            // 添加状态描述
            report.put("description", getStateDescription(emotionalState));

            // 添加建议
            report.put("suggestion", getStateSuggestion(state));

            return report;

        } catch (Exception e) {
            log.error("获取AI情感报告失败: userId={}", userId, e);
            report.put("error", e.getMessage());
            return report;
        }
    }

    // 私有方法
    private AIEmotionalState calculateNewState(AIEmotionalState currentState,
                                               String userEmotion,
                                               Double userIntensity) {

        String current = currentState.getCurrentState();
        BigDecimal energy = currentState.getEnergyLevel();

        // 1. 基于用户情感更新AI状态
        String newState = calculateStateTransition(current, userEmotion);

        // 2. 更新能量值
        if (userIntensity != null) {
            if (userIntensity > 0.7) {
                // 高强度情感消耗更多能量
                energy = BigDecimal.valueOf(Math.max(0.2, energy.doubleValue() - 0.1));
            } else if (userIntensity < 0.3) {
                // 低强度情感恢复能量
                energy = BigDecimal.valueOf(Math.min(1.0, energy.doubleValue() + 0.05));
            }
        }

        // 3. 随时间衰减能量（如果很久没互动）
        if (currentState.getLastInteractionTime() != null) {
            long hoursSinceLast = ChronoUnit.HOURS.between(
                    currentState.getLastInteractionTime(), LocalDateTime.now());
            if (hoursSinceLast > 2) {
                double decay = hoursSinceLast * 0.05;
                energy = BigDecimal.valueOf(Math.max(0.3, energy.doubleValue() - decay));
            }
        }

        // 4. 确保能量值在合理范围内
        if (energy.doubleValue() < 0.3) {
            newState = "NEUTRAL"; // 能量低时回到平静状态
        }

        // 5. 构建新状态
        return AIEmotionalState.builder()
                .userId(currentState.getUserId())
                .currentState(newState)
                .energyLevel(energy)
                .lastStateChange(LocalDateTime.now())
                .lastInteractionTime(LocalDateTime.now())
                .createdAt(currentState.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private String calculateStateTransition(String currentAIState, String userEmotion) {
        // 简单的状态转移规则
        if ("SAD".equals(userEmotion) || "ANXIOUS".equals(userEmotion)) {
            return "CONCERNED";
        } else if ("HAPPY".equals(userEmotion) || "EXCITED".equals(userEmotion)) {
            return "HAPPY";
        } else if (currentAIState.equals("NEUTRAL") &&
                (userEmotion == null || "NEUTRAL".equals(userEmotion))) {
            return Math.random() > 0.7 ? "CURIOUS" : "NEUTRAL";
        }
        return currentAIState;
    }

    private void saveOrUpdateState(AIEmotionalState state) {
        try {
            AIEmotionalState existing = aiEmotionalStateMapper.selectById(state.getUserId());
            if (existing == null) {
                aiEmotionalStateMapper.insert(state);
            } else {
                aiEmotionalStateMapper.updateById(state);
            }
        } catch (Exception e) {
            log.error("保存AI情感状态失败: userId={}", state.getUserId(), e);
        }
    }

    private void updateRedisCache(Long userId, AIEmotionalState state) {
        try {
            String redisKey = REDIS_AI_STATE_KEY + userId;
            redisTemplate.opsForValue().set(redisKey, state);
            redisTemplate.expire(redisKey, 2, java.util.concurrent.TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("更新AI状态缓存失败: userId={}", userId, e);
        }
    }

    private AIEmotionalState createDefaultState(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return AIEmotionalState.builder()
                .userId(userId)
                .currentState("NEUTRAL")
                .energyLevel(BigDecimal.valueOf(0.7))
                .lastStateChange(now)
                .lastInteractionTime(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private String getStateDescription(EmotionalState state) {
        switch (state) {
            case HAPPY:
                return "爱莉希雅现在心情很好，很乐意和你聊天~";
            case CONCERNED:
                return "爱莉希雅在关心你的状态，希望能帮助你~";
            case EXCITED:
                return "爱莉希雅很兴奋，充满了活力！";
            case CURIOUS:
                return "爱莉希雅对你说的话很好奇~";
            case PLAYFUL:
                return "爱莉希雅想调皮一下，开个玩笑~";
            case REFLECTIVE:
                return "爱莉希雅在认真思考你说的话~";
            default:
                return "爱莉希雅处于平常状态，准备好和你聊天了~";
        }
    }

    private String getStateSuggestion(AIEmotionalState state) {
        if (state.getEnergyLevel().doubleValue() < 0.3) {
            return "爱莉希雅能量有点低，可能需要休息一下~";
        } else if (state.getEnergyLevel().doubleValue() > 0.8) {
            return "爱莉希雅能量满满，可以聊得更活跃一些~";
        }
        return "爱莉希雅状态良好，随时准备回应你~";
    }
}