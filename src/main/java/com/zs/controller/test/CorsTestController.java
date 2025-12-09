package com.zs.controller.test;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * CORS测试控制器
 * 新增类，不修改现有代码
 */
@RestController
@RequestMapping("/api/cors-test")
@CrossOrigin(origins = "*")  // 额外添加控制器级别的跨域注解
public class CorsTestController {

    /**
     * 测试跨域的GET请求
     */
    @GetMapping("/get")
    public Map<String, Object> testGet() {
        return Map.of(
                "status", "success",
                "message", "CORS GET请求测试成功",
                "timestamp", LocalDateTime.now().toString(),
                "allowedOrigins", "*",
                "allowedMethods", "GET, POST, PUT, DELETE, OPTIONS, PATCH"
        );
    }

    /**
     * 测试跨域的POST请求
     */
    @PostMapping("/post")
    public Map<String, Object> testPost(@RequestBody Map<String, String> request) {
        return Map.of(
                "status", "success",
                "message", "CORS POST请求测试成功",
                "receivedData", request,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    /**
     * 测试跨域的OPTIONS预检请求
     */
    @RequestMapping(value = "/options", method = RequestMethod.OPTIONS)
    public void testOptions() {
        // OPTIONS请求由CORS配置自动处理
    }
}