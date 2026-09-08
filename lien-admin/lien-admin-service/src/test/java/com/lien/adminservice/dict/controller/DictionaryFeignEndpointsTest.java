package com.lien.adminservice.dict.controller;

import com.lien.adminservice.dict.service.ISysDictionaryService;
import com.lien.api.dict.domain.dto.DictDataDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DictionaryFeignClient 四个查询接口的 HTTP 层测试（DictionaryController 提供方实现）
 * <p>
 * 用 MockMvc 独立环境 + Mock Service，验证请求路径、参数绑定、返回体结构与空结果行为。
 */
@ExtendWith(MockitoExtension.class)
class DictionaryFeignEndpointsTest {

    @Mock
    private ISysDictionaryService sysDictionaryService;

    @InjectMocks
    private DictionaryController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private DictDataDTO data(Long id, String typeKey, String dataKey, String value, Integer sort) {
        DictDataDTO dto = new DictDataDTO();
        dto.setId(id);
        dto.setTypeKey(typeKey);
        dto.setDataKey(dataKey);
        dto.setValue(value);
        dto.setSort(sort);
        return dto;
    }

    // ---------- GET /dictionary_data/type ----------

    @Test
    void selectDictDataByType_returnsList() throws Exception {
        when(sysDictionaryService.selectDictDataByType("gender")).thenReturn(List.of(
            data(1L, "gender", "man", "男", 1),
            data(2L, "gender", "woman", "女", 2)));

        mockMvc.perform(get("/dictionary_data/type").param("typeKey", "gender"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].typeKey").value("gender"))
            .andExpect(jsonPath("$[0].dataKey").value("man"))
            .andExpect(jsonPath("$[0].value").value("男"))
            .andExpect(jsonPath("$[1].dataKey").value("woman"));

        verify(sysDictionaryService).selectDictDataByType("gender");
    }

    @Test
    void selectDictDataByType_whenNoData_returnsNullBody() throws Exception {
        when(sysDictionaryService.selectDictDataByType("missing_type")).thenReturn(null);

        mockMvc.perform(get("/dictionary_data/type").param("typeKey", "missing_type"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(sysDictionaryService).selectDictDataByType("missing_type");
    }

    // ---------- POST /dictionary_data/types ----------

    @Test
    void selectDictDataByTypes_groupsByTypeKey() throws Exception {
        when(sysDictionaryService.selectDictDataByTypes(List.of("gender", "status"))).thenReturn(Map.of(
            "gender", List.of(data(1L, "gender", "man", "男", 1)),
            "status", List.of(data(2L, "status", "on", "启用", 1),
                data(3L, "status", "off", "停用", 2))));

        mockMvc.perform(post("/dictionary_data/types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"gender\",\"status\"]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gender[0].dataKey").value("man"))
            .andExpect(jsonPath("$.status.length()").value(2))
            .andExpect(jsonPath("$.status[0].value").value("启用"))
            .andExpect(jsonPath("$.status[1].value").value("停用"));

        verify(sysDictionaryService).selectDictDataByTypes(List.of("gender", "status"));
    }

    @Test
    void selectDictDataByTypes_whenNoData_returnsNullBody() throws Exception {
        when(sysDictionaryService.selectDictDataByTypes(List.of("missing_type"))).thenReturn(null);

        mockMvc.perform(post("/dictionary_data/types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"missing_type\"]"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    // ---------- GET /dictionary_data/key ----------

    @Test
    void getDicDataByKey_returnsDto() throws Exception {
        when(sysDictionaryService.getDicDataByKey("man"))
            .thenReturn(data(1L, "gender", "man", "男", 1));

        mockMvc.perform(get("/dictionary_data/key").param("dataKey", "man"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.typeKey").value("gender"))
            .andExpect(jsonPath("$.dataKey").value("man"))
            .andExpect(jsonPath("$.value").value("男"));

        verify(sysDictionaryService).getDicDataByKey("man");
    }

    @Test
    void getDicDataByKey_whenMissing_returnsNullBody() throws Exception {
        when(sysDictionaryService.getDicDataByKey("no_such_key")).thenReturn(null);

        mockMvc.perform(get("/dictionary_data/key").param("dataKey", "no_such_key"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    // ---------- POST /dictionary_data/keys ----------

    @Test
    void getDicDataByKeys_returnsListInOrder() throws Exception {
        when(sysDictionaryService.getDicDataByKeys(List.of("man", "woman"))).thenReturn(List.of(
            data(1L, "gender", "man", "男", 1),
            data(2L, "gender", "woman", "女", 2)));

        mockMvc.perform(post("/dictionary_data/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"man\",\"woman\"]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].dataKey").value("man"))
            .andExpect(jsonPath("$[1].dataKey").value("woman"))
            .andExpect(jsonPath("$[1].value").value("女"));

        verify(sysDictionaryService).getDicDataByKeys(List.of("man", "woman"));
    }

    @Test
    void getDicDataByKeys_whenNoData_returnsNullBody() throws Exception {
        when(sysDictionaryService.getDicDataByKeys(List.of("no_such_key"))).thenReturn(null);

        mockMvc.perform(post("/dictionary_data/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"no_such_key\"]"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }
}
