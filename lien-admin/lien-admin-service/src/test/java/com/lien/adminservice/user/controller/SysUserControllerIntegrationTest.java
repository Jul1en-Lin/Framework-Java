package com.lien.adminservice.user.controller;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.jayway.jsonpath.JsonPath;
import com.lien.adminservice.config.MybatisPlusConfig;
import com.lien.adminservice.user.mapper.SysUserMapper;
import com.lien.adminservice.user.service.impl.SysUserServiceImpl;
import utils.JwtUtil;
import config.RedisConfig;
import domain.constants.TokenConstants;
import domain.dto.LoginUserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mybatis.spring.annotation.MapperScan;
import service.RedisService;
import service.TokenService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录接口全链路集成测试：MockMvc -> Controller -> Service -> MySQL -> Redis。
 *
 * <p>连接信息可以通过 IT_MYSQL_* 和 IT_REDIS_* 环境变量覆盖，测试不会写入 MySQL，
 * 测试生成的登录 Redis key 会保留，便于手动检查。</p>
 */
@SpringBootTest(classes = SysUserControllerIntegrationTest.TestBoot.class)
@TestPropertySource(properties = {
    "RUN_ENV=test",
    "spring.cloud.bootstrap.enabled=false",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.datasource.url=${IT_MYSQL_URL:jdbc:mysql://134.175.107.242:3306/frameworkjava_dev"
        + "?useSSL=false&autoReconnect=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&serverTimezone=GMT%2B8}",
    "spring.datasource.username=${IT_MYSQL_USERNAME:liendev}",
    "spring.datasource.password=${IT_MYSQL_PASSWORD:lien@123}",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.data.redis.host=${IT_REDIS_HOST:134.175.107.242}",
    "spring.data.redis.port=${IT_REDIS_PORT:6379}",
    "spring.data.redis.password=${IT_REDIS_PASSWORD:lien@123}"
})
class SysUserControllerIntegrationTest {

    @Autowired
    private SysUserController controller;

    @Autowired
    private RedisService redisService;

    private MockMvc mockMvc;
    private String cachedKey;

    @BeforeEach
    void setUpController() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void login_withValidUser_writesLoginUserToRedis() throws Exception {
        MvcResult result = mockMvc.perform(post("/sys_user/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "18888888888",
                                  "password": "f10bd64cefdb5cf15e2f2adb5f8dfbc3"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200000))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expires").isNumber())
                .andReturn();

        String accessToken = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.accessToken");
        String userToken = JwtUtil.getUserKey(accessToken);
        assertNotNull(userToken);

        cachedKey = TokenConstants.LOGIN_TOKEN_KEY + userToken;
        LoginUserDTO cachedUser = redisService.getCacheObject(cachedKey, LoginUserDTO.class);

        assertNotNull(cachedUser, "登录成功后用户信息应写入 Redis");
        System.out.println("登录 Redis key = " + cachedKey);
        System.out.println("登录 Redis value = " + cachedUser);
        assertEquals(10000001L, cachedUser.getUserId());
        assertEquals("admin", cachedUser.getUserName());
        assertEquals("sys", cachedUser.getUserFrom());
        assertEquals(userToken, cachedUser.getUserToken());
        assertNotNull(cachedUser.getLoginTime());
        assertNotNull(cachedUser.getExpireTime());

        Long expire = redisService.getExpire(cachedKey);
        assertNotNull(expire);
        assertTrue(expire > 0, "登录缓存应设置有效期");
    }

    @Configuration
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class,
        RedissonAutoConfiguration.class
    })
    @Import({
        MybatisPlusConfig.class,
        RedisConfig.class,
        RedisService.class,
        TokenService.class,
        SysUserController.class,
        SysUserServiceImpl.class
    })
    @MapperScan("com.lien.adminservice.user.mapper")
    static class TestBoot {
    }
}
