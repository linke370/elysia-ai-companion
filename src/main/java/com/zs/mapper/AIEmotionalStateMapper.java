package com.zs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zs.entity.AIEmotionalState;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI情感状态Mapper
 * 新增接口，不修改现有代码
 */
@Mapper
public interface AIEmotionalStateMapper extends BaseMapper<AIEmotionalState> {
    // 继承BaseMapper，自带CRUD方法
}