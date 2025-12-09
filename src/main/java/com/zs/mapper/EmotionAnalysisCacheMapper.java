package com.zs.mapper;

import com.zs.entity.EmotionAnalysisCache;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author a1783
* @description 针对表【emotion_analysis_cache(情感分析结果缓存表)】的数据库操作Mapper
* @createDate 2025-12-01 00:23:34
* @Entity com.zs.pojo.EmotionAnalysisCache
*/
@Mapper
public interface EmotionAnalysisCacheMapper extends BaseMapper<EmotionAnalysisCache> {

}




