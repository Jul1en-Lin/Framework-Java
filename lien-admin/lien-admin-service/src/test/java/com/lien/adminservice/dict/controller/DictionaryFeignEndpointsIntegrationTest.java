package com.lien.adminservice.dict.controller;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lien.adminservice.config.MybatisPlusConfig;
import com.lien.adminservice.dict.domain.entity.SysDictionaryData;
import com.lien.adminservice.dict.domain.entity.SysDictionaryType;
import com.lien.adminservice.dict.mapper.SysDictionaryDataMapper;
import com.lien.adminservice.dict.mapper.SysDictionaryTypeMapper;
import com.lien.adminservice.dict.service.ISysDictionaryService;
import com.lien.adminservice.dict.service.impl.SysDictionaryServiceImpl;
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
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DictionaryFeignClient 四个查询接口的集成测试（连接远端 MySQL，走 MockMvc -> Controller -> Service -> DB 全链路）
 * <p>
 * 连接信息可通过环境变量 IT_MYSQL_URL / IT_MYSQL_USERNAME / IT_MYSQL_PASSWORD 覆盖。
 * 测试在事务内执行并自动回滚，不会在共享开发库中留下脏数据。
 * 注意：dataKey/value 的查重是全局的，测试数据统一带 UUID 前缀，避免与库中已有数据冲突。
 */
@SpringBootTest(classes = DictionaryFeignEndpointsIntegrationTest.TestBoot.class)
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
class DictionaryFeignEndpointsIntegrationTest {

    /**
     * 测试数据统一前缀，保证与库中已有数据隔离
     */
    private static final String PREFIX = "it_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @Autowired
    private ISysDictionaryService service;

    @Autowired
    private SysDictionaryTypeMapper typeMapper;

    @Autowired
    private SysDictionaryDataMapper dataMapper;

    private MockMvc mockMvc;

    /**
     * 精简的测试上下文：只装配数据源、MyBatis-Plus、Mapper 和被测服务，不启动完整应用（避免 Nacos/缓存等依赖）。
     * 注意不能用 @TestConfiguration，否则主启动类会被自动加入上下文。
     */
    @Configuration
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
    })
    @Import({MybatisPlusConfig.class, SysDictionaryServiceImpl.class})
    @MapperScan("com.lien.adminservice.dict.mapper")
    static class TestBoot {
    }

    @BeforeEach
    void setUp() {
        DictionaryController controller = new DictionaryController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "sysDictionaryService", service);
        StandaloneMockMvcBuilder builder = MockMvcBuilders.standaloneSetup(controller);
        mockMvc = builder.build();
    }

    private void insertType(String typeKey, String value) {
        SysDictionaryType type = new SysDictionaryType();
        type.setTypeKey(typeKey);
        type.setValue(value);
        typeMapper.insert(type);
    }

    private void insertData(String typeKey, String dataKey, String value, int sort) {
        SysDictionaryData data = new SysDictionaryData();
        data.setTypeKey(typeKey);
        data.setDataKey(dataKey);
        data.setValue(value);
        data.setSort(sort);
        dataMapper.insert(data);
    }

    // ---------- GET /dictionary_data/type ----------

    @Test
    void selectDictDataByType_returnsAllRowsOfTheType() throws Exception {
        insertType(PREFIX + "_gender", "性别" + PREFIX);
        insertData(PREFIX + "_gender", PREFIX + "_man", "男" + PREFIX, 1);
        insertData(PREFIX + "_gender", PREFIX + "_woman", "女" + PREFIX, 2);
        // 其他类型的数据不应被查出来
        insertType(PREFIX + "_other", "其他" + PREFIX);
        insertData(PREFIX + "_other", PREFIX + "_x", "干扰项" + PREFIX, 1);

        mockMvc.perform(get("/dictionary_data/type").param("typeKey", PREFIX + "_gender"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].typeKey")
                .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo(PREFIX + "_gender"))))
            .andExpect(jsonPath("$[*].dataKey")
                .value(org.hamcrest.Matchers.containsInAnyOrder(PREFIX + "_man", PREFIX + "_woman")))
            .andExpect(jsonPath("$[*].value")
                .value(org.hamcrest.Matchers.containsInAnyOrder("男" + PREFIX, "女" + PREFIX)));
    }

    @Test
    void selectDictDataByType_whenTypeHasNoData_returnsEmptyBody() throws Exception {
        mockMvc.perform(get("/dictionary_data/type").param("typeKey", PREFIX + "_no_data"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    // ---------- POST /dictionary_data/types ----------

    @Test
    void selectDictDataByTypes_groupsRowsByTypeKey_andOmitsEmptyTypes() throws Exception {
        insertType(PREFIX + "_gender", "性别" + PREFIX);
        insertData(PREFIX + "_gender", PREFIX + "_man", "男" + PREFIX, 1);
        insertData(PREFIX + "_gender", PREFIX + "_woman", "女" + PREFIX, 2);
        insertType(PREFIX + "_status", "状态" + PREFIX);
        insertData(PREFIX + "_status", PREFIX + "_on", "启用" + PREFIX, 1);
        // 没有 dataKey 有数据的类型不应出现在结果 Map 中
        insertType(PREFIX + "_empty", "空类型" + PREFIX);

        mockMvc.perform(post("/dictionary_data/types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"" + PREFIX + "_gender\",\"" + PREFIX + "_status\",\"" + PREFIX + "_empty\"]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$." + PREFIX + "_gender.length()").value(2))
            .andExpect(jsonPath("$." + PREFIX + "_gender[*].dataKey")
                .value(org.hamcrest.Matchers.containsInAnyOrder(PREFIX + "_man", PREFIX + "_woman")))
            .andExpect(jsonPath("$." + PREFIX + "_status.length()").value(1))
            .andExpect(jsonPath("$." + PREFIX + "_status[0].value").value("启用" + PREFIX))
            .andExpect(jsonPath("$." + PREFIX + "_empty").doesNotExist());
    }

    @Test
    void selectDictDataByTypes_whenNoTypeHasData_returnsEmptyBody() throws Exception {
        mockMvc.perform(post("/dictionary_data/types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"" + PREFIX + "_none1\",\"" + PREFIX + "_none2\"]"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    // ---------- GET /dictionary_data/key ----------

    @Test
    void getDicDataByKey_returnsMappedDto() throws Exception {
        insertType(PREFIX + "_gender", "性别" + PREFIX);
        insertData(PREFIX + "_gender", PREFIX + "_man", "男" + PREFIX, 3);

        mockMvc.perform(get("/dictionary_data/key").param("dataKey", PREFIX + "_man"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.typeKey").value(PREFIX + "_gender"))
            .andExpect(jsonPath("$.dataKey").value(PREFIX + "_man"))
            .andExpect(jsonPath("$.value").value("男" + PREFIX))
            .andExpect(jsonPath("$.sort").value(3));
    }

    @Test
    void getDicDataByKey_whenMissing_returnsEmptyBody() throws Exception {
        mockMvc.perform(get("/dictionary_data/key").param("dataKey", PREFIX + "_missing"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    // ---------- POST /dictionary_data/keys ----------

    @Test
    void getDicDataByKeys_returnsOnlyExistingRows() throws Exception {
        insertType(PREFIX + "_gender", "性别" + PREFIX);
        insertData(PREFIX + "_gender", PREFIX + "_man", "男" + PREFIX, 1);
        insertData(PREFIX + "_gender", PREFIX + "_woman", "女" + PREFIX, 2);

        mockMvc.perform(post("/dictionary_data/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"" + PREFIX + "_man\",\"" + PREFIX + "_missing\",\"" + PREFIX + "_woman\"]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].dataKey")
                .value(org.hamcrest.Matchers.containsInAnyOrder(PREFIX + "_man", PREFIX + "_woman")))
            .andExpect(jsonPath("$[*].value")
                .value(org.hamcrest.Matchers.containsInAnyOrder("男" + PREFIX, "女" + PREFIX)));
    }

    @Test
    void getDicDataByKeys_whenNoneExists_returnsEmptyBody() throws Exception {
        mockMvc.perform(post("/dictionary_data/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"" + PREFIX + "_none1\",\"" + PREFIX + "_none2\"]"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }
}
