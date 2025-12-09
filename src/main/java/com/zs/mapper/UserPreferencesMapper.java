package com.zs.mapper;

import com.zs.entity.UserPreferences;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author a1783
* @description 针对表【user_preferences(用户个性化设置表)】的数据库操作Mapper
* @createDate 2025-12-01 00:23:34
* @Entity com.zs.pojo.UserPreferences
*/
@Mapper
public interface UserPreferencesMapper extends BaseMapper<UserPreferences> {

}




