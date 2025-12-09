package com.zs.mapper;

import com.zs.entity.UserEmotionProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author a1783
* @description 针对表【user_emotion_profile(用户情感画像分析表)】的数据库操作Mapper
* @createDate 2025-12-01 00:23:34
* @Entity com.zs.pojo.UserEmotionProfile
*/
@Mapper
public interface UserEmotionProfileMapper extends BaseMapper<UserEmotionProfile> {

}




