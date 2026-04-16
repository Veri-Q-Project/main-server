package com.veriq.veriqbe3.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // [핵심] 빨간 줄이 뜨는 Jackson 시리얼라이저 대신
        // 최신 버전에서 권장하는 json 시리얼라이저 자동 할당 방식을 씁니다.
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        // Key는 문자열로, Value는 JSON으로!
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(jsonSerializer);

        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
    }


