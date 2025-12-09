package com.zs.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String email;
    private String studentId;
    private String university;
    private String major;
    private String grade;
    private String personalityType;
    private String emotionalTendency;
    private Boolean isActive;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}