package com.lien.adminservice.security;

import config.RedisConfig;
import domain.constants.TokenConstants;
import domain.dto.LoginUserDTO;
import domain.dto.TokenDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import service.RedisService;
import service.TokenService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录用户信息写入 Redis 的集成测试（连接远端 Redis）。
 * <p>
 * 连接信息可通过环境变量 IT_REDIS_HOST / IT_REDIS_PORT / IT_REDIS_PASSWORD 覆盖。
 * 测试结束后会清理自身写入的 Redis key。
 */
@SpringBootTest(classes = TokenServiceRedisCacheTest.TestBoot.class)
@TestPropertySource(properties = {
    // 满足 main 下 bootstrap.yml 中 ${RUN_ENV} 占位符；测试不走 Nacos，直接连远端 Redis
    "RUN_ENV=test",
    "spring.cloud.bootstrap.enabled=false",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.data.redis.host=${IT_REDIS_HOST:134.175.107.242}",
    "spring.data.redis.port=${IT_REDIS_PORT:6379}",
    "spring.data.redis.password=${IT_REDIS_PASSWORD:lien@123}"
})
class TokenServiceRedisCacheTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RedisService redisService;

    private String cachedKey;

    @AfterEach
    void cleanUp() {
        if (cachedKey != null) {
            redisService.deleteObject(cachedKey);
        }
    }

    /**
     * 精简的测试上下文：只装配 Redisson + RedisTemplate + RedisService + TokenService，不启动完整应用。
     */
    @Configuration
    @ImportAutoConfiguration(RedissonAutoConfiguration.class)
    @Import({RedisConfig.class, RedisService.class, TokenService.class})
    static class TestBoot {
    }

    @Test
    void createToken_shouldCacheLoginUserToRedis() {
        LoginUserDTO loginUser = new LoginUserDTO();
        loginUser.setUserId(1001L);
        loginUser.setUserFrom("web");
        loginUser.setUserName("alice");

        // 生成令牌的同时会把登录用户信息写入 Redis
        TokenDTO tokenDTO = tokenService.createToken(loginUser);
        assertNotNull(tokenDTO.getAccessToken());

        // userToken 由 TokenService 内部生成，缓存 key = logintoken: + userToken
        assertNotNull(loginUser.getUserToken());
        cachedKey = TokenConstants.LOGIN_TOKEN_KEY + loginUser.getUserToken();

        // 校验 Redis 中写入的用户信息
        LoginUserDTO cached = redisService.getCacheObject(cachedKey, LoginUserDTO.class);
        assertNotNull(cached, "登录用户信息应已写入 Redis");
        assertEquals(loginUser.getUserToken(), cached.getUserToken());
        assertEquals(1001L, cached.getUserId());
        assertEquals("alice", cached.getUserName());
        assertEquals("web", cached.getUserFrom());
        assertNotNull(cached.getLoginTime());
        assertNotNull(cached.getExpireTime());

        // 缓存有效期为 720 分钟（CacheConstants.EXPIRATION），剩余时间应大于 0 且不超过 720 分钟
        Long expire = redisService.getExpire(cachedKey);
        assertNotNull(expire);
        assertTrue(expire > 0 && expire <= 720 * 60, "缓存有效期应为 720 分钟，实际剩余：" + expire + " 秒");
    }
}
