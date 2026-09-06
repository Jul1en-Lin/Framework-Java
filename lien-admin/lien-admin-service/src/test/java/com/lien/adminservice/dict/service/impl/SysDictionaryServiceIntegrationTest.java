package com.lien.adminservice.dict.service.impl;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lien.adminservice.config.MybatisPlusConfig;
import com.lien.adminservice.dict.domain.dto.DictTypeWriteReqDTO;
import com.lien.adminservice.dict.domain.entity.SysDictionaryData;
import com.lien.adminservice.dict.domain.entity.SysDictionaryType;
import com.lien.adminservice.dict.mapper.SysDictionaryDataMapper;
import com.lien.adminservice.dict.mapper.SysDictionaryTypeMapper;
import com.lien.api.dict.domain.dto.DictDataAddReqDTO;
import com.lien.api.dict.domain.dto.DictDataEditReqDTO;
import com.lien.api.dict.domain.dto.DictDataListReqDTO;
import com.lien.api.dict.domain.dto.DictTypeListReqDTO;
import com.lien.api.dict.domain.vo.DictDataVO;
import com.lien.api.dict.domain.vo.DictTypeVO;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SysDictionaryServiceImpl 集成测试（连接远端 MySQL）
 * <p>
 * 连接信息可通过环境变量 IT_MYSQL_URL / IT_MYSQL_USERNAME / IT_MYSQL_PASSWORD 覆盖。
 * 测试在事务内执行并自动回滚，不会在共享开发库中留下脏数据。
 */
@SpringBootTest(classes = SysDictionaryServiceIntegrationTest.TestBoot.class)
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
class SysDictionaryServiceIntegrationTest {

    /**
     * 测试数据统一前缀，保证与库中已有数据隔离
     */
    private static final String PREFIX = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @Autowired
    private com.lien.adminservice.dict.service.ISysDictionaryService service;

    @Autowired
    private SysDictionaryTypeMapper typeMapper;

    @Autowired
    private SysDictionaryDataMapper dataMapper;

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

    private DictTypeWriteReqDTO buildTypeWrite(String typeKey, String value, String remark) {
        DictTypeWriteReqDTO dto = new DictTypeWriteReqDTO();
        dto.setTypeKey(typeKey);
        dto.setValue(value);
        dto.setRemark(remark);
        return dto;
    }

    private SysDictionaryType insertType(String typeKey, String value) {
        SysDictionaryType type = new SysDictionaryType();
        type.setTypeKey(typeKey);
        type.setValue(value);
        typeMapper.insert(type);
        return type;
    }

    // ---------- addType ----------

    @Test
    void addType_success_and_persisted() {
        DictTypeWriteReqDTO dto = buildTypeWrite(PREFIX + "_type", "性别" + PREFIX, "集成测试");
        Long id = service.addType(dto);

        assertNotNull(id);
        SysDictionaryType saved = typeMapper.selectById(id);
        assertNotNull(saved);
        assertEquals(PREFIX + "_type", saved.getTypeKey());
        assertEquals("性别" + PREFIX, saved.getValue());
        assertEquals("集成测试", saved.getRemark());
    }

    @Test
    void addType_whenKeyOrValueExists_throws() {
        insertType(PREFIX + "_dup", "已存在值" + PREFIX);

        // 相同 typeKey
        ServiceException ex1 = assertThrows(ServiceException.class,
            () -> service.addType(buildTypeWrite(PREFIX + "_dup", "新值" + PREFIX, null)));
        assertEquals("字典类型的键或者值已存在", ex1.getMsg());

        // 相同 value、不同 typeKey
        ServiceException ex2 = assertThrows(ServiceException.class,
            () -> service.addType(buildTypeWrite(PREFIX + "_other", "已存在值" + PREFIX, null)));
        assertEquals("字典类型的键或者值已存在", ex2.getMsg());
    }

    @Test
    void addType_whenRemarkBlank_usesDbDefault() {
        Long id = service.addType(buildTypeWrite(PREFIX + "_blank", "值" + PREFIX, "  "));

        SysDictionaryType saved = typeMapper.selectById(id);
        assertNotNull(saved);
        assertTrue(saved.getRemark() == null || saved.getRemark().isEmpty());
    }

    // ---------- listType ----------

    @Test
    void listType_paginates_and_filters_byValuePrefix() {
        insertType(PREFIX + "_a", "测试类型" + PREFIX + "_a");
        insertType(PREFIX + "_b", "测试类型" + PREFIX + "_b");
        insertType(PREFIX + "_c", "测试类型" + PREFIX + "_c");

        DictTypeListReqDTO reqDTO = new DictTypeListReqDTO();
        reqDTO.setPageNo(1);
        reqDTO.setPageSize(2);
        reqDTO.setValue("测试类型" + PREFIX);

        BasePageVO<DictTypeVO> page1 = service.listType(reqDTO);
        assertEquals(3, page1.getTotals());
        assertEquals(2, page1.getTotalPages());
        assertEquals(2, page1.getList().size());
        page1.getList().forEach(vo -> assertTrue(vo.getValue().startsWith("测试类型" + PREFIX)));

        reqDTO.setPageNo(2);
        BasePageVO<DictTypeVO> page2 = service.listType(reqDTO);
        assertEquals(1, page2.getList().size());
    }

    @Test
    void listType_filters_byTypeKey() {
        insertType(PREFIX + "_a", "值A" + PREFIX);
        insertType(PREFIX + "_b", "值B" + PREFIX);

        DictTypeListReqDTO reqDTO = new DictTypeListReqDTO();
        reqDTO.setTypeKey(PREFIX + "_b");

        BasePageVO<DictTypeVO> result = service.listType(reqDTO);
        assertEquals(1, result.getTotals());
        assertEquals(PREFIX + "_b", result.getList().get(0).getTypeKey());
        assertEquals("值B" + PREFIX, result.getList().get(0).getValue());
    }

    // ---------- editType ----------

    @Test
    void editType_success_and_persisted() {
        insertType(PREFIX + "_edit", "旧值" + PREFIX);

        DictTypeWriteReqDTO dto = buildTypeWrite(PREFIX + "_edit", "新值" + PREFIX, "新备注");
        Long id = service.editType(dto);

        SysDictionaryType saved = typeMapper.selectById(id);
        assertEquals("新值" + PREFIX, saved.getValue());
        assertEquals("新备注", saved.getRemark());
    }

    @Test
    void editType_whenTypeKeyNotFound_throws() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.editType(buildTypeWrite(PREFIX + "_missing", "任意值", null)));
        assertEquals("字典类型（TypeKey）不存在", ex.getMsg());
    }

    @Test
    void editType_whenValueTakenByOtherType_throws() {
        insertType(PREFIX + "_t1", "被占用值" + PREFIX);
        insertType(PREFIX + "_t2", "另一个值" + PREFIX);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.editType(buildTypeWrite(PREFIX + "_t2", "被占用值" + PREFIX, null)));
        assertEquals("已有字典类型 TypeKey 相同的值（value）存在，不允许编辑", ex.getMsg());

        // 原数据未被修改
        SysDictionaryType saved = typeMapper.selectOne(
            new LambdaQueryWrapper<SysDictionaryType>()
                .eq(SysDictionaryType::getTypeKey, PREFIX + "_t2"));
        assertEquals("另一个值" + PREFIX, saved.getValue());
    }

    // ---------- addData ----------

    @Test
    void addData_success_and_persisted() {
        insertType(PREFIX + "_parent", "父类型" + PREFIX);

        DictDataAddReqDTO dto = new DictDataAddReqDTO();
        dto.setTypeKey(PREFIX + "_parent");
        dto.setDataKey("man");
        dto.setValue("男" + PREFIX);
        dto.setSort(3);
        dto.setRemark("男性");

        Long id = service.addData(dto);
        assertNotNull(id);

        SysDictionaryData saved = dataMapper.selectById(id);
        assertEquals(PREFIX + "_parent", saved.getTypeKey());
        assertEquals("man", saved.getDataKey());
        assertEquals("男" + PREFIX, saved.getValue());
        assertEquals(3, saved.getSort());
        assertEquals("男性", saved.getRemark());
    }

    @Test
    void addData_whenParentTypeMissing_throws() {
        DictDataAddReqDTO dto = new DictDataAddReqDTO();
        dto.setTypeKey(PREFIX + "_no_parent");
        dto.setDataKey("man");
        dto.setValue("男" + PREFIX);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.addData(dto));
        assertEquals("上级字典类型不存在，不允许添加新数据", ex.getMsg());
    }

    @Test
    void addData_whenDataKeyOrValueDuplicated_throws() {
        insertType(PREFIX + "_parent", "父类型" + PREFIX);
        DictDataAddReqDTO first = new DictDataAddReqDTO();
        first.setTypeKey(PREFIX + "_parent");
        first.setDataKey("man");
        first.setValue("男" + PREFIX);
        service.addData(first);

        // 相同 dataKey
        DictDataAddReqDTO sameKey = new DictDataAddReqDTO();
        sameKey.setTypeKey(PREFIX + "_parent");
        sameKey.setDataKey("man");
        sameKey.setValue("另一个值" + PREFIX);
        ServiceException ex1 = assertThrows(ServiceException.class, () -> service.addData(sameKey));
        assertEquals("字典数据的键或值已存在，不允许添加", ex1.getMsg());

        // 相同 value、不同 dataKey
        DictDataAddReqDTO sameValue = new DictDataAddReqDTO();
        sameValue.setTypeKey(PREFIX + "_parent");
        sameValue.setDataKey("woman");
        sameValue.setValue("男" + PREFIX);
        ServiceException ex2 = assertThrows(ServiceException.class, () -> service.addData(sameValue));
        assertEquals("字典数据的键或值已存在，不允许添加", ex2.getMsg());
    }

    @Test
    void addData_whenSortAndRemarkAbsent_usesDbDefault() {
        insertType(PREFIX + "_parent", "父类型" + PREFIX);

        DictDataAddReqDTO dto = new DictDataAddReqDTO();
        dto.setTypeKey(PREFIX + "_parent");
        dto.setDataKey("man");
        dto.setValue("男" + PREFIX);

        Long id = service.addData(dto);
        SysDictionaryData saved = dataMapper.selectById(id);
        assertNotNull(saved);
        // 库中 sort 默认 1，remark 默认 ''
        assertEquals(1, saved.getSort());
        assertTrue(saved.getRemark() == null || saved.getRemark().isEmpty());
    }

    // ---------- listData ----------

    private void insertData(String typeKey, String dataKey, String value, int sort) {
        SysDictionaryData data = new SysDictionaryData();
        data.setTypeKey(typeKey);
        data.setDataKey(dataKey);
        data.setValue(value);
        data.setSort(sort);
        dataMapper.insert(data);
    }

    @Test
    void listData_ordersBySort_and_paginates() {
        insertType(PREFIX + "_parent", "父类型" + PREFIX);
        insertData(PREFIX + "_parent", "k3", "项三" + PREFIX, 3);
        insertData(PREFIX + "_parent", "k1", "项一" + PREFIX, 1);
        insertData(PREFIX + "_parent", "k2", "项二" + PREFIX, 2);

        DictDataListReqDTO reqDTO = new DictDataListReqDTO();
        reqDTO.setPageNo(1);
        reqDTO.setPageSize(2);
        reqDTO.setTypeKey(PREFIX + "_parent");

        BasePageVO<DictDataVO> page1 = service.listData(reqDTO);
        assertEquals(3, page1.getTotals());
        assertEquals(2, page1.getTotalPages());
        // 按 sort 升序
        assertEquals("k1", page1.getList().get(0).getDataKey());
        assertEquals("k2", page1.getList().get(1).getDataKey());

        reqDTO.setPageNo(2);
        BasePageVO<DictDataVO> page2 = service.listData(reqDTO);
        assertEquals(1, page2.getList().size());
        assertEquals("k3", page2.getList().get(0).getDataKey());
    }

    @Test
    void listData_filtersByValuePrefix_onValueOrDataKey() {
        insertType(PREFIX + "_parent", "父类型" + PREFIX);
        insertData(PREFIX + "_parent", "男1", "男甲" + PREFIX, 1);
        insertData(PREFIX + "_parent", "男2", "男乙" + PREFIX, 2);
        insertData(PREFIX + "_parent", "女1", "女丙" + PREFIX, 3);

        DictDataListReqDTO reqDTO = new DictDataListReqDTO();
        reqDTO.setTypeKey(PREFIX + "_parent");
        reqDTO.setValue("男");

        BasePageVO<DictDataVO> result = service.listData(reqDTO);
        assertEquals(2, result.getTotals());
        assertEquals(List.of("男1", "男2"),
            result.getList().stream().map(DictDataVO::getDataKey).toList());
    }

    // ---------- editData ----------

    @Test
    void editData_success_and_persisted() {
        insertType(PREFIX + "_parent", "父类型" + PREFIX);
        insertData(PREFIX + "_parent", "man", "男" + PREFIX, 9);

        DictDataEditReqDTO dto = new DictDataEditReqDTO();
        dto.setDataKey("man");
        dto.setValue("男性" + PREFIX);
        dto.setSort(2);
        dto.setRemark("新备注");

        Long id = service.editData(dto);
        SysDictionaryData saved = dataMapper.selectById(id);
        assertEquals("男性" + PREFIX, saved.getValue());
        assertEquals(2, saved.getSort());
        assertEquals("新备注", saved.getRemark());
    }

    @Test
    void editData_whenDataKeyNotFound_throws() {
        DictDataEditReqDTO dto = new DictDataEditReqDTO();
        dto.setDataKey(PREFIX + "_missing");
        dto.setValue("任意值");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.editData(dto));
        assertEquals("字典数据（dataKey）不存在", ex.getMsg());
    }

    @Test
    void editData_whenValueTakenByOtherData_throws() {
        insertType(PREFIX + "_parent", "父类型" + PREFIX);
        insertData(PREFIX + "_parent", "woman", "女性" + PREFIX, 1);
        insertData(PREFIX + "_parent", "man", "男性" + PREFIX, 2);

        DictDataEditReqDTO dto = new DictDataEditReqDTO();
        dto.setDataKey("man");
        dto.setValue("女性" + PREFIX);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.editData(dto));
        assertEquals("已有字典数据 dataKey 相同的值（value）存在，不允许编辑", ex.getMsg());

        SysDictionaryData saved = dataMapper.selectOne(
            new LambdaQueryWrapper<SysDictionaryData>()
                .eq(SysDictionaryData::getDataKey, "man"));
        assertEquals("男性" + PREFIX, saved.getValue());
    }

    @Test
    void editData_whenSortAndRemarkAbsent_keepsOriginal() {
        insertType(PREFIX + "_parent", "父类型" + PREFIX);
        insertData(PREFIX + "_parent", "man", "男" + PREFIX, 9);
        dataMapper.update(null,
            new LambdaUpdateWrapper<SysDictionaryData>()
                .eq(SysDictionaryData::getDataKey, "man").set(SysDictionaryData::getRemark, "原备注"));

        DictDataEditReqDTO dto = new DictDataEditReqDTO();
        dto.setDataKey("man");
        dto.setValue("男性" + PREFIX);

        service.editData(dto);

        SysDictionaryData saved = dataMapper.selectOne(
            new LambdaQueryWrapper<SysDictionaryData>()
                .eq(SysDictionaryData::getDataKey, "man"));
        assertEquals("男性" + PREFIX, saved.getValue());
        assertEquals(9, saved.getSort());
        assertEquals("原备注", saved.getRemark());
    }

    // 注意：value/dataKey 的查重是全局的（不限 typeKey），测试值均带 UUID 前缀避免与库中已有数据冲突

    @Test
    void contextLoads_mappersWired() {
        assertNotNull(typeMapper);
        assertNotNull(dataMapper);
        assertNull(typeMapper.selectOne(
            new LambdaQueryWrapper<SysDictionaryType>()
                .eq(SysDictionaryType::getTypeKey, PREFIX + "_never_created")));
    }
}
