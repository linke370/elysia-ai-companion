// File: src/main/java/com/zs/controller/emotion/EmotionAnalysisController.java
package com.zs.controller.emotion;

import com.zs.service.emotion.EmotionAnalysisService;
import com.zs.service.emotion.dto.EmotionAnalysisDTO;
import com.zs.vo.ResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 情感分析控制器
 */
@RestController
@RequestMapping("/api/emotion")
@Tag(name = "情感分析模块", description = "用户情感分析相关接口")
@Slf4j
public class EmotionAnalysisController {

    @Resource
    private EmotionAnalysisService emotionAnalysisService;

    @PostMapping("/analyze")
    @Operation(summary = "情感分析", description = "分析用户消息的情感")
    public ResultVO<EmotionAnalysisDTO> analyzeEmotion(
            @RequestParam Long userId,
            @RequestParam String message) {

        log.info("收到情感分析请求: userId={}, messageLength={}", userId, message.length());

        try {
            EmotionAnalysisDTO result = emotionAnalysisService.analyzeUserEmotion(message, userId);
            return ResultVO.success("情感分析完成", result);

        } catch (Exception e) {
            log.error("情感分析接口异常: userId={}", userId, e);
            return ResultVO.error("情感分析失败: " + e.getMessage());
        }
    }

    @GetMapping("/current")
    @Operation(summary = "获取当前情感", description = "获取用户当前的情感状态")
    public ResultVO<EmotionAnalysisDTO> getCurrentEmotion(@RequestParam Long userId) {
        try {
            EmotionAnalysisDTO emotion = emotionAnalysisService.getCurrentEmotion(userId);
            return ResultVO.success("获取当前情感成功", emotion);
        } catch (Exception e) {
            log.error("获取当前情感失败: userId={}", userId, e);
            return ResultVO.error("获取当前情感失败");
        }
    }

    @GetMapping("/report")
    @Operation(summary = "情感报告", description = "生成用户情感分析报告")
    public ResultVO<Map<String, Object>> getEmotionReport(@RequestParam Long userId) {
        try {
            Map<String, Object> report = emotionAnalysisService.generateEmotionReport(userId);
            return ResultVO.success("情感报告生成成功", report);
        } catch (Exception e) {
            log.error("生成情感报告失败: userId={}", userId, e);
            return ResultVO.error("生成情感报告失败");
        }
    }

    @GetMapping("/history")
    @Operation(summary = "情感历史", description = "获取用户情感分析历史")
    public ResultVO<List<EmotionAnalysisDTO>> getEmotionHistory(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<EmotionAnalysisDTO> history = emotionAnalysisService.getEmotionHistory(userId, limit);
            return ResultVO.success("获取情感历史成功", history);
        } catch (Exception e) {
            log.error("获取情感历史失败: userId={}", userId, e);
            return ResultVO.error("获取情感历史失败");
        }
    }

    @DeleteMapping("/clear")
    @Operation(summary = "清理情感数据", description = "清理用户的缓存情感数据")
    public ResultVO<String> clearEmotionData(@RequestParam Long userId) {
        try {
            emotionAnalysisService.clearUserEmotionData(userId);
            return ResultVO.success("情感数据清理成功");
        } catch (Exception e) {
            log.error("清理情感数据失败: userId={}", userId, e);
            return ResultVO.error("清理情感数据失败");
        }
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "情感分析服务健康检查")
    public ResultVO<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> health = emotionAnalysisService.healthCheck();
            return ResultVO.success("情感分析服务运行正常", health);
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return ResultVO.error("健康检查失败");
        }
    }
}