package com.lien.adminservice.user.controller;

import com.lien.adminservice.user.domain.dto.PasswordLoginDTO;
import com.lien.adminservice.user.service.ISysUserService;
import domain.dto.TokenDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SysUserController 登录接口测试。
 *
 * <p>使用 MockMvc + Mock Service，不连接数据库，验证请求参数、服务调用和响应结构。</p>
 */
@ExtendWith(MockitoExtension.class)
class SysUserControllerTest {

    @Mock
    private ISysUserService sysUserService;

    @InjectMocks
    private SysUserController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void login_returnsToken() throws Exception {
        TokenDTO token = new TokenDTO();
        token.setAccessToken("test-access-token");
        token.setExpires(7_200_000L);
        when(sysUserService.login(org.mockito.ArgumentMatchers.any(PasswordLoginDTO.class)))
                .thenReturn(token);

        mockMvc.perform(post("/sys_user/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "13800138000",
                                  "password": "encrypted-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200000))
                .andExpect(jsonPath("$.msg").value("操作成功"))
                .andExpect(jsonPath("$.data.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.data.expires").value(7_200_000));

        ArgumentCaptor<PasswordLoginDTO> captor = ArgumentCaptor.forClass(PasswordLoginDTO.class);
        verify(sysUserService).login(captor.capture());
        assertEquals("13800138000", captor.getValue().getPhone());
        assertEquals("encrypted-password", captor.getValue().getPassword());
    }

    @Test
    void login_whenPhoneMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/sys_user/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "encrypted-password"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(sysUserService, never()).login(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void login_whenPasswordMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/sys_user/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "13800138000"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(sysUserService, never()).login(org.mockito.ArgumentMatchers.any());
    }
}
