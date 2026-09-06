package com.lien.adminservice.dict.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysDictionaryServiceImpl 服务层单元测试
 */
@ExtendWith(MockitoExtension.class)
class SysDictionaryServiceImplTest {

    @Mock
    private SysDictionaryTypeMapper sysDictTypeMapper;

    @Mock
    private SysDictionaryDataMapper sysDictionaryDataMapper;

    private SysDictionaryServiceImpl sysDictionaryService;

    /**
     * 纯单测环境下没有 MyBatis-Plus 运行时，
     * 需手动初始化实体 TableInfo，否则 LambdaQueryWrapper 解析方法引用会抛
     * "can not find lambda cache for this entity"
     */
    @BeforeAll
    static void initEntityTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysDictionaryType.class);
        TableInfoHelper.initTableInfo(assistant, SysDictionaryData.class);
    }

    @BeforeEach
    void setUp() {
        sysDictionaryService = new SysDictionaryServiceImpl();
        ReflectionTestUtils.setField(sysDictionaryService, "sysDictTypeMapper", sysDictTypeMapper);
        ReflectionTestUtils.setField(sysDictionaryService, "sysDictionaryDataMapper", sysDictionaryDataMapper);
    }

    // ---------- addType ----------

    @Test
    void addType_success() {
        DictTypeWriteReqDTO reqDTO = new DictTypeWriteReqDTO();
        reqDTO.setValue("性别");
        reqDTO.setTypeKey("sys_user_sex");
        reqDTO.setRemark("用户性别");

        when(sysDictTypeMapper.selectOne(any())).thenReturn(null);
        when(sysDictTypeMapper.insert(any(SysDictionaryType.class))).thenAnswer(invocation -> {
            SysDictionaryType entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        });

        Long id = sysDictionaryService.addType(reqDTO);

        assertEquals(100L, id);

        ArgumentCaptor<SysDictionaryType> captor = ArgumentCaptor.forClass(SysDictionaryType.class);
        verify(sysDictTypeMapper).insert(captor.capture());
        SysDictionaryType inserted = captor.getValue();
        assertEquals("性别", inserted.getValue());
        assertEquals("sys_user_sex", inserted.getTypeKey());
        assertEquals("用户性别", inserted.getRemark());
    }

    @Test
    void addType_whenRemarkBlank_notSet() {
        DictTypeWriteReqDTO reqDTO = new DictTypeWriteReqDTO();
        reqDTO.setValue("性别");
        reqDTO.setTypeKey("sys_user_sex");
        reqDTO.setRemark("  ");

        when(sysDictTypeMapper.selectOne(any())).thenReturn(null);
        when(sysDictTypeMapper.insert(any(SysDictionaryType.class))).thenReturn(1);

        sysDictionaryService.addType(reqDTO);

        ArgumentCaptor<SysDictionaryType> captor = ArgumentCaptor.forClass(SysDictionaryType.class);
        verify(sysDictTypeMapper).insert(captor.capture());
        assertNull(captor.getValue().getRemark());
    }

    @Test
    void addType_whenKeyOrValueExists_throws() {
        DictTypeWriteReqDTO reqDTO = new DictTypeWriteReqDTO();
        reqDTO.setValue("性别");
        reqDTO.setTypeKey("sys_user_sex");

        when(sysDictTypeMapper.selectOne(any())).thenReturn(new SysDictionaryType());

        ServiceException ex = assertThrows(ServiceException.class, () -> sysDictionaryService.addType(reqDTO));
        assertEquals("字典类型的键或者值已存在", ex.getMsg());
        verify(sysDictTypeMapper, never()).insert(any(SysDictionaryType.class));
    }

    // ---------- listType ----------

    @Test
    void listType_success() {
        DictTypeListReqDTO reqDTO = new DictTypeListReqDTO();
        reqDTO.setPageNo(2);
        reqDTO.setPageSize(5);
        reqDTO.setValue("性");
        reqDTO.setTypeKey("sys_user_sex");

        SysDictionaryType type = new SysDictionaryType();
        type.setId(1L);
        type.setTypeKey("sys_user_sex");
        type.setValue("性别");
        type.setRemark("备注");
        type.setStatus(1);

        Page<SysDictionaryType> page = new Page<>(2, 5);
        page.setRecords(List.of(type));
        page.setTotal(8);
        when(sysDictTypeMapper.selectPage(any(), any())).thenReturn(page);

        BasePageVO<DictTypeVO> result = sysDictionaryService.listType(reqDTO);

        assertEquals(8, result.getTotals());
        assertEquals(2, result.getTotalPages());
        assertEquals(1, result.getList().size());
        DictTypeVO vo = result.getList().get(0);
        assertEquals(1L, vo.getId());
        assertEquals("sys_user_sex", vo.getTypeKey());
        assertEquals("性别", vo.getValue());
        assertEquals("备注", vo.getRemark());
        assertEquals(1, vo.getStatus());

        ArgumentCaptor<Page<SysDictionaryType>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(sysDictTypeMapper).selectPage(pageCaptor.capture(), any());
        assertEquals(2, pageCaptor.getValue().getCurrent());
        assertEquals(5, pageCaptor.getValue().getSize());
    }

    @Test
    void listType_emptyResult() {
        DictTypeListReqDTO reqDTO = new DictTypeListReqDTO();

        Page<SysDictionaryType> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(sysDictTypeMapper.selectPage(any(), any())).thenReturn(page);

        BasePageVO<DictTypeVO> result = sysDictionaryService.listType(reqDTO);

        assertEquals(0, result.getTotals());
        assertEquals(0, result.getTotalPages());
        assertTrue(result.getList().isEmpty());
    }

    // ---------- editType ----------

    @Test
    void editType_success() {
        DictTypeWriteReqDTO reqDTO = new DictTypeWriteReqDTO();
        reqDTO.setValue("性别_新");
        reqDTO.setTypeKey("sys_user_sex");
        reqDTO.setRemark("新备注");

        SysDictionaryType existing = new SysDictionaryType();
        existing.setId(1L);
        existing.setTypeKey("sys_user_sex");
        existing.setValue("性别");

        when(sysDictTypeMapper.selectOne(any())).thenReturn(existing, (SysDictionaryType) null);
        when(sysDictTypeMapper.updateById(any(SysDictionaryType.class))).thenReturn(1);

        Long id = sysDictionaryService.editType(reqDTO);

        assertEquals(1L, id);
        ArgumentCaptor<SysDictionaryType> captor = ArgumentCaptor.forClass(SysDictionaryType.class);
        verify(sysDictTypeMapper).updateById(captor.capture());
        assertEquals("性别_新", captor.getValue().getValue());
        assertEquals("新备注", captor.getValue().getRemark());
    }

    @Test
    void editType_whenTypeKeyNotFound_throws() {
        DictTypeWriteReqDTO reqDTO = new DictTypeWriteReqDTO();
        reqDTO.setValue("性别_新");
        reqDTO.setTypeKey("not_exist");

        when(sysDictTypeMapper.selectOne(any())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> sysDictionaryService.editType(reqDTO));
        assertEquals("字典类型（TypeKey）不存在", ex.getMsg());
        verify(sysDictTypeMapper, never()).updateById(any(SysDictionaryType.class));
    }

    @Test
    void editType_whenValueDuplicated_throws() {
        DictTypeWriteReqDTO reqDTO = new DictTypeWriteReqDTO();
        reqDTO.setValue("性别_新");
        reqDTO.setTypeKey("sys_user_sex");

        SysDictionaryType existing = new SysDictionaryType();
        existing.setId(1L);
        existing.setTypeKey("sys_user_sex");
        when(sysDictTypeMapper.selectOne(any())).thenReturn(existing, new SysDictionaryType());

        ServiceException ex = assertThrows(ServiceException.class, () -> sysDictionaryService.editType(reqDTO));
        assertEquals("已有字典类型 TypeKey 相同的值（value）存在，不允许编辑", ex.getMsg());
        verify(sysDictTypeMapper, never()).updateById(any(SysDictionaryType.class));
    }

    // ---------- addData ----------

    @Test
    void addData_success() {
        DictDataAddReqDTO reqDTO = new DictDataAddReqDTO();
        reqDTO.setTypeKey("sys_user_sex");
        reqDTO.setDataKey("man");
        reqDTO.setValue("男");
        reqDTO.setSort(1);
        reqDTO.setRemark("男性");

        when(sysDictTypeMapper.selectOne(any())).thenReturn(new SysDictionaryType());
        when(sysDictionaryDataMapper.selectOne(any())).thenReturn(null);
        when(sysDictionaryDataMapper.insert(any(SysDictionaryData.class))).thenAnswer(invocation -> {
            SysDictionaryData entity = invocation.getArgument(0);
            entity.setId(200L);
            return 1;
        });

        Long id = sysDictionaryService.addData(reqDTO);

        assertEquals(200L, id);
        ArgumentCaptor<SysDictionaryData> captor = ArgumentCaptor.forClass(SysDictionaryData.class);
        verify(sysDictionaryDataMapper).insert(captor.capture());
        SysDictionaryData inserted = captor.getValue();
        assertEquals("sys_user_sex", inserted.getTypeKey());
        assertEquals("man", inserted.getDataKey());
        assertEquals("男", inserted.getValue());
        assertEquals(1, inserted.getSort());
        assertEquals("男性", inserted.getRemark());
    }

    @Test
    void addData_whenParentTypeNotFound_throws() {
        DictDataAddReqDTO reqDTO = new DictDataAddReqDTO();
        reqDTO.setTypeKey("not_exist");
        reqDTO.setDataKey("man");
        reqDTO.setValue("男");

        when(sysDictTypeMapper.selectOne(any())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> sysDictionaryService.addData(reqDTO));
        assertEquals("上级字典类型不存在，不允许添加新数据", ex.getMsg());
        verify(sysDictionaryDataMapper, never()).insert(any(SysDictionaryData.class));
    }

    @Test
    void addData_whenDataKeyOrValueExists_throws() {
        DictDataAddReqDTO reqDTO = new DictDataAddReqDTO();
        reqDTO.setTypeKey("sys_user_sex");
        reqDTO.setDataKey("man");
        reqDTO.setValue("男");

        when(sysDictTypeMapper.selectOne(any())).thenReturn(new SysDictionaryType());
        when(sysDictionaryDataMapper.selectOne(any())).thenReturn(new SysDictionaryData());

        ServiceException ex = assertThrows(ServiceException.class, () -> sysDictionaryService.addData(reqDTO));
        assertEquals("字典数据的键或值已存在，不允许添加", ex.getMsg());
        verify(sysDictionaryDataMapper, never()).insert(any(SysDictionaryData.class));
    }

    @Test
    void addData_whenSortAndRemarkAbsent_notSet() {
        DictDataAddReqDTO reqDTO = new DictDataAddReqDTO();
        reqDTO.setTypeKey("sys_user_sex");
        reqDTO.setDataKey("man");
        reqDTO.setValue("男");

        when(sysDictTypeMapper.selectOne(any())).thenReturn(new SysDictionaryType());
        when(sysDictionaryDataMapper.selectOne(any())).thenReturn(null);
        when(sysDictionaryDataMapper.insert(any(SysDictionaryData.class))).thenReturn(1);

        sysDictionaryService.addData(reqDTO);

        ArgumentCaptor<SysDictionaryData> captor = ArgumentCaptor.forClass(SysDictionaryData.class);
        verify(sysDictionaryDataMapper).insert(captor.capture());
        assertNull(captor.getValue().getSort());
        assertNull(captor.getValue().getRemark());
    }

    // ---------- listData ----------

    @Test
    void listData_success() {
        DictDataListReqDTO reqDTO = new DictDataListReqDTO();
        reqDTO.setPageNo(1);
        reqDTO.setPageSize(10);
        reqDTO.setTypeKey("sys_user_sex");
        reqDTO.setValue("男");

        SysDictionaryData data = new SysDictionaryData();
        data.setId(1L);
        data.setTypeKey("sys_user_sex");
        data.setDataKey("man");
        data.setValue("男");
        data.setSort(1);
        data.setStatus(1);

        Page<SysDictionaryData> page = new Page<>(1, 10);
        page.setRecords(List.of(data));
        page.setTotal(1);
        when(sysDictionaryDataMapper.selectPage(any(), any())).thenReturn(page);

        BasePageVO<DictDataVO> result = sysDictionaryService.listData(reqDTO);

        assertEquals(1, result.getTotals());
        assertEquals(1, result.getTotalPages());
        assertEquals(1, result.getList().size());
        DictDataVO vo = result.getList().get(0);
        assertEquals(1L, vo.getId());
        assertEquals("sys_user_sex", vo.getTypeKey());
        assertEquals("man", vo.getDataKey());
        assertEquals("男", vo.getValue());
        assertEquals(1, vo.getSort());
        assertEquals(1, vo.getStatus());

        ArgumentCaptor<Page<SysDictionaryData>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(sysDictionaryDataMapper).selectPage(pageCaptor.capture(), any());
        assertEquals(1, pageCaptor.getValue().getCurrent());
        assertEquals(10, pageCaptor.getValue().getSize());
    }

    @Test
    void listData_emptyResult() {
        DictDataListReqDTO reqDTO = new DictDataListReqDTO();
        reqDTO.setTypeKey("sys_user_sex");

        Page<SysDictionaryData> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(sysDictionaryDataMapper.selectPage(any(), any())).thenReturn(page);

        BasePageVO<DictDataVO> result = sysDictionaryService.listData(reqDTO);

        assertEquals(0, result.getTotals());
        assertTrue(result.getList().isEmpty());
    }

    // ---------- editData ----------

    @Test
    void editData_success() {
        DictDataEditReqDTO reqDTO = new DictDataEditReqDTO();
        reqDTO.setDataKey("man");
        reqDTO.setValue("男性");
        reqDTO.setSort(2);
        reqDTO.setRemark("新备注");

        SysDictionaryData existing = new SysDictionaryData();
        existing.setId(1L);
        existing.setDataKey("man");
        existing.setValue("男");

        when(sysDictionaryDataMapper.selectOne(any())).thenReturn(existing, (SysDictionaryData) null);
        when(sysDictionaryDataMapper.updateById(any(SysDictionaryData.class))).thenReturn(1);

        Long id = sysDictionaryService.editData(reqDTO);

        assertEquals(1L, id);
        ArgumentCaptor<SysDictionaryData> captor = ArgumentCaptor.forClass(SysDictionaryData.class);
        verify(sysDictionaryDataMapper).updateById(captor.capture());
        assertEquals("男性", captor.getValue().getValue());
        assertEquals(2, captor.getValue().getSort());
        assertEquals("新备注", captor.getValue().getRemark());
    }

    @Test
    void editData_whenDataNotFound_throws() {
        DictDataEditReqDTO reqDTO = new DictDataEditReqDTO();
        reqDTO.setDataKey("not_exist");
        reqDTO.setValue("男性");

        when(sysDictionaryDataMapper.selectOne(any())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> sysDictionaryService.editData(reqDTO));
        assertEquals("字典数据（dataKey）不存在", ex.getMsg());
        verify(sysDictionaryDataMapper, never()).updateById(any(SysDictionaryData.class));
    }

    @Test
    void editData_whenValueDuplicated_throws() {
        DictDataEditReqDTO reqDTO = new DictDataEditReqDTO();
        reqDTO.setDataKey("man");
        reqDTO.setValue("女性");

        SysDictionaryData existing = new SysDictionaryData();
        existing.setId(1L);
        existing.setDataKey("man");
        when(sysDictionaryDataMapper.selectOne(any())).thenReturn(existing, new SysDictionaryData());

        ServiceException ex = assertThrows(ServiceException.class, () -> sysDictionaryService.editData(reqDTO));
        assertEquals("已有字典数据 dataKey 相同的值（value）存在，不允许编辑", ex.getMsg());
        verify(sysDictionaryDataMapper, never()).updateById(any(SysDictionaryData.class));
    }

    @Test
    void editData_whenSortAndRemarkAbsent_keepOriginal() {
        DictDataEditReqDTO reqDTO = new DictDataEditReqDTO();
        reqDTO.setDataKey("man");
        reqDTO.setValue("男性");

        SysDictionaryData existing = new SysDictionaryData();
        existing.setId(1L);
        existing.setDataKey("man");
        existing.setValue("男");
        existing.setSort(9);
        existing.setRemark("原备注");

        when(sysDictionaryDataMapper.selectOne(any())).thenReturn(existing, (SysDictionaryData) null);
        when(sysDictionaryDataMapper.updateById(any(SysDictionaryData.class))).thenReturn(1);

        sysDictionaryService.editData(reqDTO);

        ArgumentCaptor<SysDictionaryData> captor = ArgumentCaptor.forClass(SysDictionaryData.class);
        verify(sysDictionaryDataMapper, times(1)).updateById(captor.capture());
        assertEquals(9, captor.getValue().getSort());
        assertEquals("原备注", captor.getValue().getRemark());
    }
}
