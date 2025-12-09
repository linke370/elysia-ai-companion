// File: src/main/java/com/zs/ElysiaApplication.java
package com.zs;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.zs.mapper")
public class EllysiaApplication {
    public static void main(String[] args) {
        SpringApplication.run(EllysiaApplication.class, args);
        System.out.println("""
            ==========================================
            爱莉希雅 AI数字伴侣 启动成功！ 💖
            
            访问地址: http://localhost:8080
            流式聊天: GET /api/chat/stream?userId=1&message=你好&model=qwen
            简单聊天: POST /api/chat/simple
            ==========================================
            """);
    }
}