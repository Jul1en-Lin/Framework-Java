package com.lien.adminservice.dict.controller;

import com.lien.adminservice.dict.service.ISysArgumentService;
import com.lien.api.dict.domain.dto.ArgumentDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ArgumentFeignClient 两个查询接口的 HTTP 层测试（提供方 ArgumentController 实现）
 * <p>
 * MockMvc 独立环境 + Mock Service，验证请求路径、参数绑定、返回体结构与空结果行为。
 * 不连接数据库。
 */
@ExtendWith(MockitoExtension.class)
class ArgumentFeignEndpointsTest {

    @Mock
    private ISysArgumentService sysArgumentService;

    @InjectMocks
    private ArgumentController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ArgumentDTO dto(Long id, String configKey, String name, String value, String remark) {
        ArgumentDTO dto = new ArgumentDTO();
        dto.setId(id);
        dto.setConfigKey(configKey);
        dto.setName(name);
        dto.setValue(value);
        dto.setRemark(remark);
        return dto;
    }

    // ---------- GET /argument/key ----------

    @Test
    void getByConfigKey_returnsDto() throws Exception {
        when(sysArgumentService.getByConfigKey("app.timeout"))
            .thenReturn(dto(1L, "app.timeout", "超时时间", "30", "秒"));

        mockMvc.perform(get("/argument/key").param("configKey", "app.timeout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.configKey").value("app.timeout"))
            .andExpect(jsonPath("$.name").value("超时时间"))
            .andExpect(jsonPath("$.value").value("30"))
            .andExpect(jsonPath("$.remark").value("秒"));

        verify(sysArgumentService).getByConfigKey("app.timeout");
    }

    @Test
    void getByConfigKey_whenMissing_returnsNullBody() throws Exception {
        when(sysArgumentService.getByConfigKey("no_such_key")).thenReturn(null);

        mockMvc.perform(get("/argument/key").param("configKey", "no_such_key"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(sysArgumentService).getByConfigKey("no_such_key");
    }

    @Test
    void getByConfigKey_whenParamAbsent_returns400() throws Exception {
        mockMvc.perform(get("/argument/key"))
            .andExpect(status().isBadRequest());
    }

    // ---------- GET /argument/keys ----------

    /** Feign 默认按 EXPLODED 展开集合参数：configKeys=a&configKeys=b */
    @Test
    void getByConfigKeys_repeatedParamFormat_returnsList() throws Exception {
        when(sysArgumentService.getByConfigKeys(List.of("app.timeout", "app.retry")))
            .thenReturn(List.of(
                dto(1L, "app.timeout", "超时时间", "30", null),
                dto(2L, "app.retry", "重试次数", "3", null)));

        mockMvc.perform(get("/argument/keys")
                .param("configKeys", "app.timeout", "app.retry"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].configKey").value("app.timeout"))
            .andExpect(jsonPath("$[0].value").value("30"))
            .andExpect(jsonPath("$[1].configKey").value("app.retry"))
            .andExpect(jsonPath("$[1].value").value("3"));

        verify(sysArgumentService).getByConfigKeys(List.of("app.timeout", "app.retry"));
    }

    /** Spring MVC 也支持逗号分隔的集合参数：configKeys=a,b */
    @Test
    void getByConfigKeys_commaSeparatedFormat_returnsList() throws Exception {
        when(sysArgumentService.getByConfigKeys(List.of("app.timeout", "app.retry")))
            .thenReturn(List.of(dto(1L, "app.timeout", "超时时间", "30", null)));

        mockMvc.perform(get("/argument/keys").param("configKeys", "app.timeout,app.retry"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].configKey").value("app.timeout"));

        verify(sysArgumentService).getByConfigKeys(List.of("app.timeout", "app.retry"));
    }

    @Test
    void getByConfigKeys_whenNoData_returnsNullBody() throws Exception {
        when(sysArgumentService.getByConfigKeys(List.of("no_such_key"))).thenReturn(null);

        mockMvc.perform(get("/argument/keys").param("configKeys", "no_such_key"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    /** 空集合入参：Service 直接返回 null，响应体为空 */
    @Test
    void getByConfigKeys_whenParamEmpty_returnsNullBody() throws Exception {
        when(sysArgumentService.getByConfigKeys(List.of())).thenReturn(null);

        mockMvc.perform(get("/argument/keys").param("configKeys", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(sysArgumentService).getByConfigKeys(List.of());
    }

    @Test
    void getByConfigKeys_whenParamAbsent_returns400() throws Exception {
        mockMvc.perform(get("/argument/keys"))
            .andExpect(status().isBadRequest());
    }
}
