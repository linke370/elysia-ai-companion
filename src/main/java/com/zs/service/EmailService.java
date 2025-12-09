// src/main/java/com/zs/service/EmailService.java
package com.zs.service;

public interface EmailService {

    /**
     * 发送验证码邮件
     */
    void sendVerificationCode(String toEmail, String code);

    /**
     * 验证邮箱验证码
     */
    boolean verifyCode(String email, String code, String codeType);

    /**
     * 生成随机验证码
     */
    String generateVerificationCode();
}