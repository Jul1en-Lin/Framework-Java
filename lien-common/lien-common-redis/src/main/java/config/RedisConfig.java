package config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import utils.JsonUtil;

/**
 * Redis 配置类 设置带有自定义序列化器的RedisTemplate。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 设置序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper());

        // 设置键和值的序列化方式
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.setValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 获取用于 Redis 序列化的 ObjectMapper。
     * 使用与 JsonUtil 同一套配置，以确保序列化/反序列化行为一致。
     * 例如 JsonUtil 配置了 LocalDateTime 的序列化格式（yyyy-MM-dd HH:mm:ss），
     * 如果 Redis 使用不同的 ObjectMapper 配置，会导致存入的日期格式和读取时解析的格式不匹配。
     */
    private ObjectMapper objectMapper() {
        return JsonUtil.getObjectMapper();
    }
}
