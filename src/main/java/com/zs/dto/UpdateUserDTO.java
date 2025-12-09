package com.zs.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UpdateUserDTO {
    @Email(message = "邮箱格式不正确")
    private String newEmail;

    private String studentId;
    private String university;
    private String major;
    private String grade;
    private String personalityType;
    private String emotionalTendency;
}