package com.zs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket配置类 - 实现实时聊天功能
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 配置消息代理
     * - 客户端订阅地址：/topic（广播消息）
     * - 客户端发送地址：/app（发送到服务端）
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单的消息代理，目的地以"/topic"开头的消息会广播给所有连接的客户端
        config.enableSimpleBroker("/topic", "/queue");

        // 设置应用程序目的地前缀，客户端发送消息时需要添加"/app"前缀
        config.setApplicationDestinationPrefixes("/app");

        // 设置用户目的地前缀，用于点对点通信
        config.setUserDestinationPrefix("/user");
    }

    /**
     * 注册WebSocket端点 - 已增强跨域支持
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册一个WebSocket端点，客户端通过这个端点连接
        registry.addEndpoint("/ws-chat")
                // 允许所有来源（开发环境）
                .setAllowedOriginPatterns("*")
                // 启用SockJS支持，提供兼容性
                .withSockJS();

        // 添加一个普通WebSocket端点（不适用SockJS）
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*");

        System.out.println("✅ WebSocket跨域配置已生效");
    }
}