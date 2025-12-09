// src/main/java/com/zs/mapper/UsersMapper.java
package com.zs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zs.entity.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UsersMapper extends BaseMapper<Users> {

    /**
     * 根据用户名查找用户
     */
    @Select("SELECT * FROM users WHERE username = #{username} AND is_active = true")
    Users findByUsername(String username);

    /**
     * 根据邮箱查找用户
     */
    @Select("SELECT * FROM users WHERE email = #{email} AND is_active = true")
    Users findByEmail(String email);

    /**
     * 检查用户名是否存在
     */
    @Select("SELECT COUNT(*) FROM users WHERE username = #{username}")
    int countByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    @Select("SELECT COUNT(*) FROM users WHERE email = #{email}")
    int countByEmail(String email);
}