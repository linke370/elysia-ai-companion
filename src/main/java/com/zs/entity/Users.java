// src/main/java/com/zs/entity/User.java
package com.zs.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("users")
public class Users {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("email")
    private String email;

    // 大学生信息
    @TableField("student_id")
    private String studentId;

    @TableField("university")
    private String university;

    @TableField("major")
    private String major;

    @TableField("grade")
    private String grade;

    // 情感档案
    @TableField("personality_type")
    private String personalityType;

    @TableField("emotional_tendency")
    private String emotionalTendency;

    // 账户状态
    @TableField("is_active")
    private Boolean isActive;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    // 时间戳
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}