package com.zs.mapper;

import com.zs.entity.Conversations;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author a1783
* @description 针对表【conversations(对话记录与情感分析表)】的数据库操作Mapper
* @createDate 2025-12-01 00:23:34
* @Entity com.zs.pojo.Conversations
*/
@Mapper
public interface ConversationsMapper extends BaseMapper<Conversations> {

}




