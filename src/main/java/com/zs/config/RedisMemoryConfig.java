package com.zs.config;

import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

@Configuration
public class RedisMemoryConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.timeout:5000}")
    private int timeout;

    /**
     * Redis连接工厂 - 配置连接池
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        JedisConnectionFactory factory = new JedisConnectionFactory();
        factory.setHostName(host);
        factory.setPort(port);
        factory.setDatabase(database);

        if (StringUtils.hasText(password)) {
            factory.setPassword(password);
        }

        // 连接池配置
        factory.getPoolConfig().setMaxTotal(20);
        factory.getPoolConfig().setMaxIdle(10);
        factory.getPoolConfig().setMinIdle(5);
        factory.getPoolConfig().setMaxWaitMillis(timeout);
        factory.getPoolConfig().setTestOnBorrow(true);

        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * Spring AI Alibaba的Redis记忆仓库
     */
    @Bean
    public RedisChatMemoryRepository redisChatMemoryRepository() {
        System.out.println("🎯 创建 RedisChatMemoryRepository");
        System.out.println("💡 Host: " + host + ", Port: " + port);

        RedisChatMemoryRepository.RedisBuilder builder = RedisChatMemoryRepository.builder()
                .host(host)
                .port(port)
                .timeout(timeout);

        if (StringUtils.hasText(password)) {
            builder = builder.password(password);
            System.out.println("💡 使用密码连接Redis");
        } else {
            System.out.println("💡 无密码连接Redis");
        }

        return builder.build();
    }

    /**
     * 创建支持Java 8时间类型的ObjectMapper
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 注册JavaTimeModule以支持LocalDateTime等时间类型
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    /**
     * Redis操作模板 - 修正版
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       ObjectMapper redisObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用配置好的ObjectMapper创建序列化器
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        // 设置序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        System.out.println("✅ RedisTemplate配置完成，支持Java 8时间类型");
        return template;
    }
}