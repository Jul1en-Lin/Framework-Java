package com.lien.portalservice.service.impl;

import com.lien.api.appuser.domain.vo.AppUserVO;
import com.lien.api.appuser.feign.AppUserFeignClient;
import com.lien.portalservice.domain.dto.WechatLoginDTO;
import domain.EnumCode;
import domain.Result;
import domain.dto.LoginUserDTO;
import domain.dto.TokenDTO;
import domain.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import service.TokenService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl - 登录逻辑单元测试")
class UserServiceImplTest {

    @Mock
    private AppUserFeignClient appUserFeignClient;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private UserServiceImpl userService;

    private static final String OPEN_ID = "test_open_id_123";

    private AppUserVO mockAppUserVO() {
        AppUserVO vo = new AppUserVO();
        vo.setUserId(1L);
        vo.setNickName("用户1234");
        vo.setOpenId(OPEN_ID);
        return vo;
    }

    private TokenDTO mockTokenDTO() {
        TokenDTO dto = new TokenDTO();
        dto.setAccessToken("mock.jwt.token");
        dto.setExpires(7200000L);
        return dto;
    }

    // -------------------------------------------------------------------------
    // 场景一：老用户登录（数据库里已存在该 openId）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("微信登录 - 老用户（已注册）：应直接返回 token，不触发注册")
    void wechatLogin_existingUser_shouldReturnTokenWithoutRegister() {
        // Given
        WechatLoginDTO loginDTO = new WechatLoginDTO();
        loginDTO.setOpenId(OPEN_ID);

        Result<AppUserVO> findResult = Result.success(mockAppUserVO());
        when(appUserFeignClient.findByOpenId(OPEN_ID)).thenReturn(findResult);
        when(tokenService.createToken(any(LoginUserDTO.class))).thenReturn(mockTokenDTO());

        // When
        TokenDTO token = userService.login(loginDTO);

        // Then
        assertThat(token).isNotNull();
        assertThat(token.getAccessToken()).isEqualTo("mock.jwt.token");

        // 不应调用注册接口
        verify(appUserFeignClient, never()).registerByOpenId(anyString());
    }

    @Test
    @DisplayName("微信登录 - 老用户：生成令牌时应传入正确的 userId、userFrom、userName")
    void wechatLogin_existingUser_shouldPassCorrectUserInfoToTokenService() {
        // Given
        WechatLoginDTO loginDTO = new WechatLoginDTO();
        loginDTO.setOpenId(OPEN_ID);

        AppUserVO appUserVO = mockAppUserVO();
        when(appUserFeignClient.findByOpenId(OPEN_ID)).thenReturn(Result.success(appUserVO));
        when(tokenService.createToken(any(LoginUserDTO.class))).thenReturn(mockTokenDTO());

        ArgumentCaptor<LoginUserDTO> captor = ArgumentCaptor.forClass(LoginUserDTO.class);

        // When
        userService.login(loginDTO);

        // Then
        verify(tokenService).createToken(captor.capture());
        LoginUserDTO captured = captor.getValue();
        assertThat(captured.getUserId()).isEqualTo(appUserVO.getUserId());
        assertThat(captured.getUserName()).isEqualTo(appUserVO.getNickName());
        assertThat(captured.getUserFrom()).isEqualTo("app");
    }

    // -------------------------------------------------------------------------
    // 场景二：新用户登录（数据库里不存在该 openId，需要自动注册）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("微信登录 - 新用户（未注册）：应自动注册并返回 token")
    void wechatLogin_newUser_shouldRegisterThenReturnToken() {
        // Given
        WechatLoginDTO loginDTO = new WechatLoginDTO();
        loginDTO.setOpenId(OPEN_ID);

        // findByOpenId 返回 data 为 null，模拟用户不存在
        Result<AppUserVO> emptyResult = new Result<>();
        emptyResult.setCode(EnumCode.SUCCESS.getCode());
        emptyResult.setData(null);
        when(appUserFeignClient.findByOpenId(OPEN_ID)).thenReturn(emptyResult);

        // registerByOpenId 返回新创建的用户
        when(appUserFeignClient.registerByOpenId(OPEN_ID)).thenReturn(Result.success(mockAppUserVO()));
        when(tokenService.createToken(any(LoginUserDTO.class))).thenReturn(mockTokenDTO());

        // When
        TokenDTO token = userService.login(loginDTO);

        // Then
        assertThat(token).isNotNull();
        verify(appUserFeignClient).registerByOpenId(OPEN_ID);
    }

    @Test
    @DisplayName("微信登录 - 新用户：findByOpenId 返回非 SUCCESS 状态码时也应触发注册")
    void wechatLogin_newUser_findReturnsErrorCode_shouldRegister() {
        // Given
        WechatLoginDTO loginDTO = new WechatLoginDTO();
        loginDTO.setOpenId(OPEN_ID);

        Result<AppUserVO> errorResult = Result.fail("服务异常");
        when(appUserFeignClient.findByOpenId(OPEN_ID)).thenReturn(errorResult);
        when(appUserFeignClient.registerByOpenId(OPEN_ID)).thenReturn(Result.success(mockAppUserVO()));
        when(tokenService.createToken(any(LoginUserDTO.class))).thenReturn(mockTokenDTO());

        // When
        TokenDTO token = userService.login(loginDTO);

        // Then
        assertThat(token).isNotNull();
        verify(appUserFeignClient).registerByOpenId(OPEN_ID);
    }

    // -------------------------------------------------------------------------
    // 场景三：注册失败（admin-service 注册接口异常）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("微信登录 - 注册失败：registerByOpenId 返回非 SUCCESS 时应抛出 ServiceException")
    void wechatLogin_registerFails_shouldThrowServiceException() {
        // Given
        WechatLoginDTO loginDTO = new WechatLoginDTO();
        loginDTO.setOpenId(OPEN_ID);

        // 用户不存在，需要注册
        Result<AppUserVO> emptyResult = new Result<>();
        emptyResult.setCode(EnumCode.SUCCESS.getCode());
        emptyResult.setData(null);
        when(appUserFeignClient.findByOpenId(OPEN_ID)).thenReturn(emptyResult);

        // 注册接口返回失败
        when(appUserFeignClient.registerByOpenId(OPEN_ID)).thenReturn(Result.fail("注册异常"));

        // When / Then
        assertThatThrownBy(() -> userService.login(loginDTO))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getMsg()).contains("微信用户注册失败"));

        // 注册失败后不应调用 tokenService
        verify(tokenService, never()).createToken(any());
    }

    @Test
    @DisplayName("微信登录 - 注册失败：registerByOpenId 返回 data 为 null 时应抛出 ServiceException")
    void wechatLogin_registerReturnsNullData_shouldThrowServiceException() {
        // Given
        WechatLoginDTO loginDTO = new WechatLoginDTO();
        loginDTO.setOpenId(OPEN_ID);

        Result<AppUserVO> emptyFind = new Result<>();
        emptyFind.setCode(EnumCode.SUCCESS.getCode());
        emptyFind.setData(null);
        when(appUserFeignClient.findByOpenId(OPEN_ID)).thenReturn(emptyFind);

        Result<AppUserVO> nullDataRegister = new Result<>();
        nullDataRegister.setCode(EnumCode.SUCCESS.getCode());
        nullDataRegister.setData(null);
        when(appUserFeignClient.registerByOpenId(OPEN_ID)).thenReturn(nullDataRegister);

        // When / Then
        assertThatThrownBy(() -> userService.login(loginDTO))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getMsg()).contains("微信用户注册失败"));
    }
}
