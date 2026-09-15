package com.lien.adminservice.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lien.adminservice.user.domain.dto.SysUserDTO;
import com.lien.adminservice.user.domain.dto.SysUserListReqDTO;
import com.lien.adminservice.user.domain.entity.SysUser;
import com.lien.adminservice.user.mapper.SysUserMapper;
import com.lien.common.core.utils.AESUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import service.TokenService;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SysUserServiceImpl - 后台用户服务单元测试")
class SysUserServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private SysUserServiceImpl sysUserService;

    @BeforeAll
    static void initEntityTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @Nested
    @DisplayName("getUserList - 查询后台用户列表测试")
    class GetUserListTests {

        private SysUser buildSysUser(Long id, String phoneHex, String nickName, String status) {
            SysUser user = new SysUser();
            user.setId(id);
            user.setPhoneNumber(phoneHex);
            user.setNickName(nickName);
            user.setStatus(status);
            user.setIdentity("admin");
            user.setRemark("测试备注");
            return user;
        }

        @Test
        @DisplayName("全条件查询：手机号加密后传入 QueryWrapper，返回结果时手机号解密")
        void getUserList_withAllConditions_shouldEncryptPhoneQueryAndDecryptResult() {
            // Given
            String plainPhone = "13800138000";
            String encryptedPhone = AESUtil.encryptHex(plainPhone);

            SysUserListReqDTO reqDTO = new SysUserListReqDTO();
            reqDTO.setUserId(1L);
            reqDTO.setPhoneNumber(plainPhone);
            reqDTO.setStatus("0");

            SysUser mockUser = buildSysUser(1L, encryptedPhone, "管理员", "0");
            when(sysUserMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mockUser));

            // When
            List<SysUserDTO> result = sysUserService.getUserList(reqDTO);

            // Then
            // 1. 验证 QueryWrapper 中手机号被 AES 加密
            ArgumentCaptor<LambdaQueryWrapper<SysUser>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(sysUserMapper).selectList(wrapperCaptor.capture());

            LambdaQueryWrapper<SysUser> capturedWrapper = wrapperCaptor.getValue();
            capturedWrapper.getCustomSqlSegment();
            Map<String, Object> paramValues = capturedWrapper.getParamNameValuePairs();

            assertThat(paramValues.values()).contains(encryptedPhone);
            assertThat(paramValues.values()).doesNotContain(plainPhone);
            assertThat(paramValues.values()).contains(1L);
            assertThat(paramValues.values()).contains("0");

            // 2. 验证返回的 DTO 手机号被正确解密回明文
            assertThat(result).hasSize(1);
            SysUserDTO dto = result.get(0);
            assertThat(dto.getUserId()).isEqualTo(1L);
            assertThat(dto.getPhoneNumber()).isEqualTo(plainPhone);
            assertThat(dto.getNickName()).isEqualTo("管理员");
            assertThat(dto.getStatus()).isEqualTo("0");
        }

        @Test
        @DisplayName("仅按手机号查询：参数中仅包含加密后的手机号")
        void getUserList_withOnlyPhoneNumber_shouldEncryptQuery() {
            // Given
            String plainPhone = "13911112222";
            String encryptedPhone = AESUtil.encryptHex(plainPhone);

            SysUserListReqDTO reqDTO = new SysUserListReqDTO();
            reqDTO.setPhoneNumber(plainPhone);

            when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());

            // When
            List<SysUserDTO> result = sysUserService.getUserList(reqDTO);

            // Then
            ArgumentCaptor<LambdaQueryWrapper<SysUser>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(sysUserMapper).selectList(wrapperCaptor.capture());

            LambdaQueryWrapper<SysUser> capturedWrapper = wrapperCaptor.getValue();
            capturedWrapper.getCustomSqlSegment();
            Map<String, Object> paramValues = capturedWrapper.getParamNameValuePairs();

            assertThat(paramValues.values()).contains(encryptedPhone);
            assertThat(paramValues.values()).doesNotContain(plainPhone);
            assertThat(paramValues.values()).hasSize(1);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("无过滤条件：不添加任何 where 条件")
        void getUserList_withoutFilters_shouldNotAddAnyConditions() {
            // Given
            SysUserListReqDTO reqDTO = new SysUserListReqDTO();
            when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());

            // When
            List<SysUserDTO> result = sysUserService.getUserList(reqDTO);

            // Then
            ArgumentCaptor<LambdaQueryWrapper<SysUser>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(sysUserMapper).selectList(wrapperCaptor.capture());

            LambdaQueryWrapper<SysUser> capturedWrapper = wrapperCaptor.getValue();
            capturedWrapper.getCustomSqlSegment();
            assertThat(capturedWrapper.getParamNameValuePairs()).isEmpty();
            assertThat(result).isEmpty();
        }
    }
}
