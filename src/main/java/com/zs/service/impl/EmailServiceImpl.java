// src/main/java/com/zs/service/impl/EmailServiceImpl.java
package com.zs.service.impl;

import com.zs.entity.EmailVerification;
import com.zs.mapper.EmailVerificationMapper;
import com.zs.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailVerificationMapper emailVerificationMapper;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${email.verification.expire-minutes:5}")
    private int expireMinutes;

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        try {
            // 1. 保存验证码到数据库
            EmailVerification verification = new EmailVerification();
            verification.setEmail(toEmail);
            verification.setVerificationCode(code);
            verification.setCodeType("REGISTER");
            verification.setIsUsed(false);
            verification.setExpiresAt(LocalDateTime.now().plusMinutes(expireMinutes));
            verification.setCreatedAt(LocalDateTime.now());
            verification.setUpdatedAt(LocalDateTime.now());

            emailVerificationMapper.insert(verification);

            // 2. 发送邮件
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("用户注册验证码");
            message.setText("您的注册验证码是: " + code + "\n" +
                    "验证码有效期为 " + expireMinutes + " 分钟，请尽快使用。\n" +
                    "如果这不是您的操作，请忽略此邮件。");

            mailSender.send(message);
            log.info("验证码发送成功: {} -> {}", toEmail, code);

        } catch (Exception e) {
            log.error("发送验证码邮件失败: {}", e.getMessage(), e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    @Override
    public boolean verifyCode(String email, String code, String codeType) {
        try {
            LocalDateTime now = LocalDateTime.now();
            EmailVerification verification = emailVerificationMapper
                    .findValidCode(email, code, codeType, now);

            if (verification == null) {
                return false;
            }

            // 标记为已使用
            verification.setIsUsed(true);
            verification.setUpdatedAt(now);
            emailVerificationMapper.updateById(verification);

            return true;

        } catch (Exception e) {
            log.error("验证验证码失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String generateVerificationCode() {
        // 生成6位数字验证码
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}