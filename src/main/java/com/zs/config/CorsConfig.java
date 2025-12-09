package com.zs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 全局跨域配置
 * 新增配置类，不修改现有代码
 */
@Configuration
public class CorsConfig {

    /**
     * 创建CORS过滤器
     * 允许所有来源、所有方法、所有头部
     */
    @Bean
    public CorsFilter corsFilter() {
        // 1. 创建CORS配置对象
        CorsConfiguration config = new CorsConfiguration();

        // 2. 允许所有来源（生产环境请替换为具体域名）
        config.addAllowedOriginPattern("*");

        // 3. 允许所有请求方法
        config.addAllowedMethod("*");

        // 4. 允许所有请求头
        config.addAllowedHeader("*");

        // 5. 允许携带凭证（如cookies）
        config.setAllowCredentials(true);

        // 6. 设置预检请求的有效期（单位：秒）
        config.setMaxAge(3600L);

        // 7. 暴露响应头给前端
        config.addExposedHeader("Content-Type");
        config.addExposedHeader("Cache-Control");
        config.addExposedHeader("Content-Language");
        config.addExposedHeader("Expires");
        config.addExposedHeader("Last-Modified");
        config.addExposedHeader("Pragma");

        // 8. 配置源
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // 9. 对所有路径应用此配置
        source.registerCorsConfiguration("/**", config);

        System.out.println("✅ CORS跨域配置已生效：允许所有来源访问");

        return new CorsFilter(source);
    }
}