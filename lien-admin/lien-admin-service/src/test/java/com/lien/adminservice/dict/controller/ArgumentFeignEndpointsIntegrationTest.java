package com.lien.adminservice.dict.controller;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lien.adminservice.config.MybatisPlusConfig;
import com.lien.adminservice.dict.domain.entity.SysArgument;
import com.lien.adminservice.dict.mapper.SysArgumentMapper;
import com.lien.adminservice.dict.service.ISysArgumentService;
import com.lien.adminservice.dict.service.impl.SysArgumentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ArgumentFeignClient 两个查询接口的集成测试（连接远端 MySQL，走 MockMvc -> Controller -> Service -> DB 全链路）
 * <p>
 * 连接信息可通过环境变量 IT_MYSQL_URL / IT_MYSQL_USERNAME / IT_MYSQL_PASSWORD 覆盖。
 * 测试在事务内执行并自动回滚，不会在共享开发库中留下脏数据。
 * config_key 全局唯一，测试数据统一带 UUID 前缀，避免与库中已有数据冲突。
 */
@SpringBootTest(classes = ArgumentFeignEndpointsIntegrationTest.TestBoot.class)
@TestPropertySource(properties = {
    // 满足 main 下 bootstrap.yml 中 ${RUN_ENV} 占位符；测试不走 Nacos，直接连库
    "RUN_ENV=test",
    "spring.cloud.bootstrap.enabled=false",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.datasource.url=${IT_MYSQL_URL:jdbc:mysql://134.175.107.242:3306/frameworkjava_dev"
        + "?useSSL=false&autoReconnect=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&serverTimezone=GMT%2B8}",
    "spring.datasource.username=${IT_MYSQL_USERNAME:liendev}",
    "spring.datasource.password=${IT_MYSQL_PASSWORD:lien@123}",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver"
})
@Transactional
@Rollback
class ArgumentFeignEndpointsIntegrationTest {

    /** 测试数据统一前缀，保证与库中已有数据隔离 */
    private static final String PREFIX = "it_arg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @Autowired
    private ISysArgumentService service;

    @Autowired
    private SysArgumentMapper mapper;

    private MockMvc mockMvc;

    /**
     * 精简的测试上下文：只装配数据源、MyBatis-Plus、Mapper 和被测服务，不启动完整应用（避免 Nacos/缓存等依赖）。
     */
    @Configuration
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
    })
    @Import({MybatisPlusConfig.class, SysArgumentServiceImpl.class})
    @MapperScan("com.lien.adminservice.dict.mapper")
    static class TestBoot {
    }

    @BeforeEach
    void setUp() {
        ArgumentController controller = new ArgumentController();
        ReflectionTestUtils.setField(controller, "iSysArgumentService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private void insert(String configKey, String name, String value, String remark) {
        SysArgument argument = new SysArgument();
        argument.setConfigKey(configKey);
        argument.setName(name);
        argument.setValue(value);
        argument.setRemark(remark);
        mapper.insert(argument);
    }

    // ---------- GET /argument/key ----------

    @Test
    void getByConfigKey_returnsMappedDto() throws Exception {
        insert(PREFIX + "_timeout", "超时时间" + PREFIX, "30", "秒" + PREFIX);

        mockMvc.perform(get("/argument/key").param("configKey", PREFIX + "_timeout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configKey").value(PREFIX + "_timeout"))
            .andExpect(jsonPath("$.name").value("超时时间" + PREFIX))
            .andExpect(jsonPath("$.value").value("30"))
            .andExpect(jsonPath("$.remark").value("秒" + PREFIX))
            .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void getByConfigKey_whenMissing_returnsEmptyBody() throws Exception {
        mockMvc.perform(get("/argument/key").param("configKey", PREFIX + "_missing"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    // ---------- GET /argument/keys ----------

    @Test
    void getByConfigKeys_returnsOnlyExistingRows() throws Exception {
        insert(PREFIX + "_timeout", "超时时间" + PREFIX, "30", null);
        insert(PREFIX + "_retry", "重试次数" + PREFIX, "3", null);
        // 干扰数据：不在查询键中，不应被返回
        insert(PREFIX + "_other", "干扰项" + PREFIX, "999", null);

        mockMvc.perform(get("/argument/keys")
                .param("configKeys", PREFIX + "_timeout", PREFIX + "_missing", PREFIX + "_retry"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].configKey").value(org.hamcrest.Matchers.containsInAnyOrder(
                PREFIX + "_timeout", PREFIX + "_retry")))
            .andExpect(jsonPath("$[*].value").value(org.hamcrest.Matchers.containsInAnyOrder("30", "3")));
    }

    @Test
    void getByConfigKeys_whenNoneExists_returnsEmptyBody() throws Exception {
        mockMvc.perform(get("/argument/keys")
                .param("configKeys", PREFIX + "_none1", PREFIX + "_none2"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    /** 空集合入参时 Service 直接返回 null（不会生成 SQL），响应体为空 */
    @Test
    void getByConfigKeys_whenEmptyList_returnsNull() {
        assertNull(service.getByConfigKeys(java.util.List.of()));
    }
}
