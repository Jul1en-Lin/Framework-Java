package com.lien.adminservice.dict.service.impl;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lien.adminservice.config.MybatisPlusConfig;
import com.lien.adminservice.dict.domain.entity.SysArgument;
import com.lien.adminservice.dict.mapper.SysArgumentMapper;
import com.lien.adminservice.dict.service.ISysArgumentService;
import com.lien.api.dict.domain.dto.ArgumentAddReqDTO;
import com.lien.api.dict.domain.dto.ArgumentEditReqDTO;
import com.lien.api.dict.domain.dto.ArgumentListReqDTO;
import com.lien.api.dict.domain.vo.ArgumentVO;
import domain.exception.ServiceException;
import domain.vo.BasePageVO;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 参数服务远端 MySQL 集成测试。所有写操作在事务中执行并回滚。 */
@SpringBootTest(classes = SysArgumentServiceIntegrationTest.TestBoot.class)
@TestPropertySource(properties = {
    "RUN_ENV=test",
    "spring.cloud.bootstrap.enabled=false",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.datasource.url=${IT_MYSQL_URL:jdbc:mysql://134.175.107.242:3306/frameworkjava_dev?useSSL=false&autoReconnect=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&serverTimezone=GMT%2B8}",
    "spring.datasource.username=${IT_MYSQL_USERNAME:liendev}",
    "spring.datasource.password=${IT_MYSQL_PASSWORD:lien@123}",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver"
})
@Transactional
@Rollback
class SysArgumentServiceIntegrationTest {

    private static final String PREFIX = "it_arg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    @Autowired
    private ISysArgumentService service;

    @Autowired
    private SysArgumentMapper mapper;

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

    @Test
    void addAndList_readsPersistedArgument() {
        Long id = service.add(addRequest(PREFIX + "_key", "测试参数", "30"));
        assertNotNull(id);

        ArgumentListReqDTO request = new ArgumentListReqDTO();
        request.setConfigKey(PREFIX + "_key");
        request.setName("测试");
        request.setPageNo(1);
        request.setPageSize(10);

        BasePageVO<ArgumentVO> result = service.list(request);
        assertEquals(1, result.getTotals());
        assertEquals(PREFIX + "_key", result.getList().get(0).getConfigKey());
        assertEquals("30", result.getList().get(0).getValue());
    }

    @Test
    void add_whenKeyExists_rejectsDuplicate() {
        String key = PREFIX + "_duplicate";
        service.add(addRequest(key, "参数", "1"));

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.add(addRequest(key, "另一个参数", "2")));
        assertEquals("已存在参数主键", exception.getMsg());
    }

    @Test
    void edit_persistsChangedValue() {
        String key = PREFIX + "_edit";
        Long id = service.add(addRequest(key, "旧名称", "old"));

        ArgumentEditReqDTO request = new ArgumentEditReqDTO();
        request.setConfigKey(key);
        request.setName("新名称");
        request.setValue("new");
        request.setRemark("已修改");
        assertEquals(id, service.edit(request));

        SysArgument saved = mapper.selectOne(new LambdaQueryWrapper<SysArgument>()
            .eq(SysArgument::getConfigKey, key));
        assertEquals("新名称", saved.getName());
        assertEquals("new", saved.getValue());
        assertEquals("已修改", saved.getRemark());
    }

    private ArgumentAddReqDTO addRequest(String key, String name, String value) {
        ArgumentAddReqDTO request = new ArgumentAddReqDTO();
        request.setConfigKey(key);
        request.setName(name);
        request.setValue(value);
        return request;
    }
}
