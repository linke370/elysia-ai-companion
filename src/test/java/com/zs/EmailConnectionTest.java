package com.zs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
public class EmailConnectionTest {

    @Autowired
    private JavaMailSender mailSender;

    @Test
    public void testEmailConnection() {
        log.info("=== 开始邮件连接测试 ===");

        try {
            // 1. 测试简单连接
            log.info("测试邮件服务器连接...");

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("3335851297@qq.com");
            message.setTo("3331602776@qq.com"); // 可以改为自己的另一个邮箱测试
            message.setSubject("邮件连接测试");
            message.setText("这是一封测试邮件，用于验证SMTP配置是否正确。\n" +
                    "发送时间: " + java.time.LocalDateTime.now());

            log.info("准备发送邮件...");
            mailSender.send(message);

            log.info("✅ 邮件发送成功！");
            System.out.println("✅ 邮件发送成功！");

        } catch (Exception e) {
            log.error("❌ 邮件发送失败: {}", e.getMessage());
            e.printStackTrace();

            // 提供详细的错误解决建议
            provideDetailedSolution(e);
        }
    }

    private void provideDetailedSolution(Exception e) {
        System.err.println("\n" + "=".repeat(60));
        System.err.println("⚠️  邮件发送失败问题排查指南");
        System.err.println("=".repeat(60));

        if (e.getMessage().contains("535")) {
            System.err.println("\n1. 🔑 授权码问题：");
            System.err.println("   - 确认使用的是授权码，不是QQ密码");
            System.err.println("   - 授权码是否正确：tszbztpwmxqycigd");
            System.err.println("   - 授权码是否过期（重新生成）");

            System.err.println("\n2. ⚙️  QQ邮箱设置：");
            System.err.println("   - 访问 https://mail.qq.com");
            System.err.println("   - 登录邮箱 333585127@qq.com");
            System.err.println("   - 进入【设置】→【账户】");
            System.err.println("   - 开启【POP3/SMTP服务】和【IMAP/SMTP服务】");

            System.err.println("\n3. 🔄 重新生成授权码：");
            System.err.println("   - 在QQ邮箱设置中关闭SMTP服务");
            System.err.println("   - 重新开启并生成新的授权码");
            System.err.println("   - 更新配置文件中的密码");
        }

        if (e.getMessage().contains("connect") || e.getMessage().contains("timeout")) {
            System.err.println("\n🌐 网络连接问题：");
            System.err.println("   - 测试端口是否可达：telnet smtp.qq.com 587");
            System.err.println("   - 尝试使用465端口（SSL）");
            System.err.println("   - 检查防火墙设置");
        }

        System.err.println("\n4. 🧪 立即测试：");
        System.err.println("   - 尝试登录网页版QQ邮箱");
        System.err.println("   - 在另一台电脑上测试");
        System.err.println("   - 使用第三方邮件客户端测试");

        System.err.println("\n" + "=".repeat(60));
    }

}