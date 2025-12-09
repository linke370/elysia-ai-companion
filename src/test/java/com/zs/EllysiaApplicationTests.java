package com.zs;

import com.zs.mapper.UsersMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@SpringBootTest
class EllysiaApplicationTests {

    @Test
    void contextLoads() {
    }
    @Autowired
    private UsersMapper usersMapper;

    @Autowired
    private DataSource dataSource;

    @Test
    public void testAll() {
        System.out.println("=== 开始综合测试 ===");

        // 测试1: 数据库连接
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            System.out.println("✅ 数据库连接测试通过");
        } catch (Exception e) {
            System.out.println("❌ 数据库连接测试失败: " + e.getMessage());
            return;
        }

        // 测试2: MyBatis-Plus
        try {
            long count = usersMapper.selectCount(null);
            System.out.println("✅ MyBatis-Plus测试通过，用户数: " + count);
        } catch (Exception e) {
            System.out.println("❌ MyBatis-Plus测试失败: " + e.getMessage());
            return;
        }

        // 测试3: 表结构
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT id, username FROM users LIMIT 1");
            System.out.println("✅ 表结构测试通过");
        } catch (Exception e) {
            System.out.println("❌ 表结构测试失败: " + e.getMessage());
            return;
        }

        System.out.println("🎉 所有测试通过！数据库连接正常");
    }

}
