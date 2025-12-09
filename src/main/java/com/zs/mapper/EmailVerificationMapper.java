// src/main/java/com/zs/mapper/EmailVerificationMapper.java
package com.zs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zs.entity.EmailVerification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface EmailVerificationMapper extends BaseMapper<EmailVerification> {

    @Select("SELECT * FROM email_verification WHERE email = #{email} " +
            "AND verification_code = #{code} AND code_type = #{codeType} " +
            "AND is_used = false AND expires_at > #{now}")
    EmailVerification findValidCode(String email, String code, String codeType, LocalDateTime now);

    @Select("SELECT COUNT(*) FROM email_verification WHERE email = #{email} " +
            "AND created_at > #{startTime} AND is_used = false")
    int countRecentCodes(String email, LocalDateTime startTime);
}