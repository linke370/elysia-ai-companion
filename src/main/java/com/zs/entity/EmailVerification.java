// src/main/java/com/zs/entity/EmailVerification.java
package com.zs.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("email_verification")
public class EmailVerification {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("email")
    private String email;

    @TableField("verification_code")
    private String verificationCode;

    @TableField("code_type")
    private String codeType; // REGISTER, RESET_PASSWORD, CHANGE_EMAIL

    @TableField("is_used")
    private Boolean isUsed;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}