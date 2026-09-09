package com.lien.adminservice.dict.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lien.adminservice.dict.domain.entity.SysArgument;
import com.lien.adminservice.dict.mapper.SysArgumentMapper;
import com.lien.api.dict.domain.dto.ArgumentAddReqDTO;
import com.lien.api.dict.domain.dto.ArgumentDTO;
import com.lien.api.dict.domain.dto.ArgumentEditReqDTO;
import com.lien.api.dict.domain.dto.ArgumentListReqDTO;
import com.lien.api.dict.domain.vo.ArgumentVO;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysArgumentServiceImplTest {

    @Mock
    private SysArgumentMapper mapper;

    private SysArgumentServiceImpl service;

    @BeforeAll
    static void initEntityTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysArgument.class);
    }

    @BeforeEach
    void setUp() {
        service = new SysArgumentServiceImpl();
        ReflectionTestUtils.setField(service, "sysArgumentMapper", mapper);
    }

    @Test
    void add_success_mapsAllFieldsAndReturnsId() {
        ArgumentAddReqDTO request = addRequest("app.timeout", "超时时间", "30", "备注");
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(SysArgument.class))).thenAnswer(invocation -> {
            SysArgument argument = invocation.getArgument(0);
            argument.setId(10L);
            return 1;
        });

        assertEquals(10L, service.add(request));

        ArgumentCaptor<SysArgument> captor = ArgumentCaptor.forClass(SysArgument.class);
        verify(mapper).insert(captor.capture());
        assertEquals("app.timeout", captor.getValue().getConfigKey());
        assertEquals("超时时间", captor.getValue().getName());
        assertEquals("30", captor.getValue().getValue());
        assertEquals("备注", captor.getValue().getRemark());
    }

    @Test
    void add_whenKeyExists_throwsWithoutInsert() {
        when(mapper.selectOne(any())).thenReturn(new SysArgument());

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.add(addRequest("app.timeout", "超时时间", "30", null)));

        assertEquals("已存在参数主键", exception.getMsg());
        verify(mapper, never()).insert(any(SysArgument.class));
    }

    @Test
    void list_returnsPageAndMapsKeyToConfigKey() {
        ArgumentListReqDTO request = new ArgumentListReqDTO();
        request.setConfigKey("app.timeout");
        request.setName("超时");
        request.setPageNo(2);
        request.setPageSize(5);

        SysArgument argument = new SysArgument();
        argument.setId(10L);
        argument.setConfigKey("app.timeout");
        argument.setName("超时时间");
        argument.setValue("30");
        argument.setRemark("备注");
        Page<SysArgument> page = new Page<>(2, 5);
        page.setTotal(6);
        page.setRecords(List.of(argument));
        when(mapper.selectPage(any(), any())).thenReturn(page);

        BasePageVO<ArgumentVO> result = service.list(request);

        assertNotNull(result);
        assertEquals(6, result.getTotals());
        assertEquals(2, result.getTotalPages());
        assertEquals(1, result.getList().size());
        ArgumentVO vo = result.getList().get(0);
        assertEquals(10L, vo.getId());
        assertEquals("app.timeout", vo.getConfigKey());
        assertEquals("超时时间", vo.getName());
        assertEquals("30", vo.getValue());
        assertEquals("备注", vo.getRemark());
    }

    @Test
    void edit_success_updatesExistingArgument() {
        SysArgument existing = new SysArgument();
        existing.setId(10L);
        existing.setConfigKey("app.timeout");
        existing.setName("旧名称");
        when(mapper.selectOne(any())).thenReturn(existing, null);
        when(mapper.updateById(any(SysArgument.class))).thenReturn(1);

        ArgumentEditReqDTO request = new ArgumentEditReqDTO();
        request.setConfigKey("app.timeout");
        request.setName("新名称");
        request.setValue("60");
        request.setRemark("新备注");

        assertEquals(10L, service.edit(request));
        verify(mapper).updateById(existing);
        assertEquals("新名称", existing.getName());
        assertEquals("60", existing.getValue());
        assertEquals("新备注", existing.getRemark());
    }

    @Test
    void edit_whenArgumentMissing_throws() {
        when(mapper.selectOne(any())).thenReturn(null);
        ArgumentEditReqDTO request = editRequest("missing", "名称", "值");

        ServiceException exception = assertThrows(ServiceException.class, () -> service.edit(request));

        assertEquals("不存在要修改的参数主键", exception.getMsg());
        verify(mapper, never()).updateById(any(SysArgument.class));
    }

    @Test
    void edit_whenNameTaken_throwsWithoutUpdate() {
        SysArgument existing = new SysArgument();
        existing.setId(10L);
        existing.setConfigKey("app.timeout");
        when(mapper.selectOne(any())).thenReturn(existing, new SysArgument());

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.edit(editRequest("app.timeout", "已存在名称", "60")));

        assertEquals("已存在参数名称，不允许修改", exception.getMsg());
        verify(mapper, never()).updateById(any(SysArgument.class));
    }

    // ---------- getByConfigKey / getByConfigKeys（Feign 查询接口） ----------

    @Test
    void getByConfigKey_mapsAllFieldsToDto() {
        SysArgument argument = new SysArgument();
        argument.setId(10L);
        argument.setConfigKey("app.timeout");
        argument.setName("超时时间");
        argument.setValue("30");
        argument.setRemark("秒");
        when(mapper.selectOne(any())).thenReturn(argument);

        ArgumentDTO result = service.getByConfigKey("app.timeout");

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("app.timeout", result.getConfigKey());
        assertEquals("超时时间", result.getName());
        assertEquals("30", result.getValue());
        assertEquals("秒", result.getRemark());
    }

    @Test
    void getByConfigKey_whenMissing_returnsNull() {
        when(mapper.selectOne(any())).thenReturn(null);

        assertNull(service.getByConfigKey("missing"));
    }

    @Test
    void getByConfigKeys_mapsAllRowsToDto() {
        SysArgument first = new SysArgument();
        first.setId(1L);
        first.setConfigKey("app.timeout");
        first.setName("超时时间");
        first.setValue("30");
        SysArgument second = new SysArgument();
        second.setId(2L);
        second.setConfigKey("app.retry");
        second.setName("重试次数");
        second.setValue("3");
        when(mapper.selectList(any())).thenReturn(List.of(first, second));

        List<ArgumentDTO> result = service.getByConfigKeys(List.of("app.timeout", "app.retry"));

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("app.timeout", result.get(0).getConfigKey());
        assertEquals("30", result.get(0).getValue());
        assertEquals("app.retry", result.get(1).getConfigKey());
        assertEquals("3", result.get(1).getValue());
    }

    @Test
    void getByConfigKeys_whenEmptyInput_returnsNullWithoutQuery() {
        assertNull(service.getByConfigKeys(List.of()));
        verify(mapper, never()).selectList(any());
    }

    @Test
    void getByConfigKeys_whenNoMatch_returnsNull() {
        when(mapper.selectList(any())).thenReturn(List.of());

        assertNull(service.getByConfigKeys(List.of("missing")));
    }

    private ArgumentAddReqDTO addRequest(String key, String name, String value, String remark) {
        ArgumentAddReqDTO request = new ArgumentAddReqDTO();
        request.setConfigKey(key);
        request.setName(name);
        request.setValue(value);
        request.setRemark(remark);
        return request;
    }

    private ArgumentEditReqDTO editRequest(String key, String name, String value) {
        ArgumentEditReqDTO request = new ArgumentEditReqDTO();
        request.setConfigKey(key);
        request.setName(name);
        request.setValue(value);
        return request;
    }
}
