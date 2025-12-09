package com.zs.mapper;

import com.zs.entity.MemoryFragments;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author a1783
* @description 针对表【memory_fragments(用户长期记忆存储表)】的数据库操作Mapper
* @createDate 2025-12-01 00:23:34
* @Entity com.zs.pojo.MemoryFragments
*/
@Mapper
public interface MemoryFragmentsMapper extends BaseMapper<MemoryFragments> {

}




