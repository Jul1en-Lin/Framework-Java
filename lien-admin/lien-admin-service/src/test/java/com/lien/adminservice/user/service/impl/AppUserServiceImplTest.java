package com.lien.adminservice.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lien.adminservice.user.domain.dto.AppUserListReqDTO;
import com.lien.adminservice.user.domain.entity.AppUser;
import com.lien.adminservice.user.mapper.AppUserMapper;
import com.lien.api.appuser.domain.dto.AppUserDTO;
import com.lien.common.core.domain.dto.BasePageDTO;
import com.lien.common.core.utils.AESUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppUserServiceImpl - 用户服务单元测试")
class AppUserServiceImplTest {

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AppUserServiceImpl appUserService;

    @BeforeAll
    static void initEntityTableInfo() {
        // 初始化 MyBatis-Plus 的实体元数据，避免 Lambda 表达式解析列名时报错
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AppUser.class);
    }

    @Nested
    @DisplayName("getUserList - 分页查询 C 端用户列表测试")
    class GetUserListTests {

        private AppUser buildAppUser(Long id, String phone, String nickName, String openId, String avatar) {
            AppUser user = new AppUser();
            user.setId(id);
            user.setPhoneNumber(phone);
            user.setNickName(nickName);
            user.setOpenId(openId);
            user.setAvatar(avatar);
            return user;
        }

        @Test
        @DisplayName("全条件查询：手机号应被 AES 加密后作为查询条件，其余条件正常拼接并正确分页")
        void getUserList_withAllConditions_shouldEncryptPhoneAndApplyAllFilters() {
            // Given
            String plainPhone = "13812345678";
            String expectedEncryptedPhone = AESUtil.encryptHex(plainPhone);

            AppUserListReqDTO reqDTO = new AppUserListReqDTO();
            reqDTO.setUserId(100L);
            reqDTO.setPhoneNumber(plainPhone);
            reqDTO.setNickName("测试用户");
            reqDTO.setOpenId("wx_open_id_100");
            reqDTO.setPageNo(2);
            reqDTO.setPageSize(5);

            // 构造 mock 数据
            AppUser user = buildAppUser(100L, expectedEncryptedPhone, "测试用户_1", "wx_open_id_100", "http://avatar.png");
            Page<AppUser> mockPage = new Page<>(2, 5);
            mockPage.setRecords(List.of(user));
            mockPage.setSize(5);
            mockPage.setPages(3);
            mockPage.setTotal(11);

            when(appUserMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

            // When
            BasePageDTO<AppUserDTO> result = appUserService.getUserList(reqDTO);

            // Then
            // 1. 验证分页参数与 QueryWrapper
            ArgumentCaptor<Page<AppUser>> pageCaptor = ArgumentCaptor.forClass(Page.class);
            ArgumentCaptor<LambdaQueryWrapper<AppUser>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(appUserMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());

            Page<AppUser> capturedPage = pageCaptor.getValue();
            assertThat(capturedPage.getCurrent()).isEqualTo(2);
            assertThat(capturedPage.getSize()).isEqualTo(5);

            LambdaQueryWrapper<AppUser> capturedWrapper = wrapperCaptor.getValue();
            // 调用 getCustomSqlSegment 触发 MyBatis-Plus 生成 SQL 片段并填充参数键值对
            capturedWrapper.getCustomSqlSegment();
            Map<String, Object> paramValues = capturedWrapper.getParamNameValuePairs();

            // 核心验证：传入查询 Wrapper 的手机号参数必须是 AES 加密后的密文，绝不能是明文！
            assertThat(paramValues.values()).contains(expectedEncryptedPhone);
            assertThat(paramValues.values()).doesNotContain(plainPhone);
            // 验证其他条件也包含在参数中
            assertThat(paramValues.values()).contains(100L);
            assertThat(paramValues.values()).contains("wx_open_id_100");

            // 2. 验证返回结果映射
            assertThat(result).isNotNull();
            assertThat(result.getList()).hasSize(1);
            AppUserDTO userDTO = result.getList().get(0);
            assertThat(userDTO.getId()).isEqualTo(100L);
            assertThat(userDTO.getNickName()).isEqualTo("测试用户_1");
            assertThat(userDTO.getOpenId()).isEqualTo("wx_open_id_100");
            assertThat(userDTO.getAvatar()).isEqualTo("http://avatar.png");
            assertThat(result.getTotals()).isEqualTo(11);
            assertThat(result.getTotalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("仅按手机号查询：验证查询条件中仅有加密后的手机号，且未混入明文")
        void getUserList_withOnlyPhoneNumber_shouldEncryptPhoneQuery() {
            // Given
            String plainPhone = "15900001111";
            String expectedEncryptedPhone = AESUtil.encryptHex(plainPhone);

            AppUserListReqDTO reqDTO = new AppUserListReqDTO();
            reqDTO.setPhoneNumber(plainPhone);
            reqDTO.setPageNo(1);
            reqDTO.setPageSize(10);

            Page<AppUser> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setSize(10);
            mockPage.setPages(0);
            when(appUserMapper.selectPage(any(), any())).thenReturn(mockPage);

            // When
            appUserService.getUserList(reqDTO);

            // Then
            ArgumentCaptor<LambdaQueryWrapper<AppUser>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(appUserMapper).selectPage(any(), wrapperCaptor.capture());

            LambdaQueryWrapper<AppUser> capturedWrapper = wrapperCaptor.getValue();
            capturedWrapper.getCustomSqlSegment();
            Map<String, Object> paramValues = capturedWrapper.getParamNameValuePairs();
            // 参数中必须包含加密后的手机号
            assertThat(paramValues.values()).contains(expectedEncryptedPhone);
            assertThat(paramValues.values()).doesNotContain(plainPhone);
            // 且不应包含 userId、openId 等条件
            assertThat(paramValues.values()).hasSize(1);
        }

        @Test
        @DisplayName("无过滤条件：userId 为 null、字符串字段为空白时，不追加任何 where 条件")
        void getUserList_withoutFilters_shouldNotAddAnyConditions() {
            // Given
            AppUserListReqDTO reqDTO = new AppUserListReqDTO();
            reqDTO.setUserId(null);
            reqDTO.setPhoneNumber("");
            reqDTO.setNickName("   ");
            reqDTO.setOpenId(null);
            reqDTO.setPageNo(1);
            reqDTO.setPageSize(10);

            Page<AppUser> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setSize(10);
            mockPage.setPages(0);
            when(appUserMapper.selectPage(any(), any())).thenReturn(mockPage);

            // When
            BasePageDTO<AppUserDTO> result = appUserService.getUserList(reqDTO);

            // Then
            ArgumentCaptor<LambdaQueryWrapper<AppUser>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(appUserMapper).selectPage(any(), wrapperCaptor.capture());

            LambdaQueryWrapper<AppUser> capturedWrapper = wrapperCaptor.getValue();
            capturedWrapper.getCustomSqlSegment();
            // 没有追加任何 where 条件
            assertThat(capturedWrapper.getParamNameValuePairs()).isEmpty();
            assertThat(result.getList()).isEmpty();
        }

        @Test
        @DisplayName("多条记录转换：分页结果多条记录应逐一正确映射为 AppUserDTO 列表")
        void getUserList_withMultipleRecords_shouldMapAllToDTO() {
            // Given
            AppUserListReqDTO reqDTO = new AppUserListReqDTO();
            reqDTO.setPageNo(1);
            reqDTO.setPageSize(10);

            AppUser u1 = buildAppUser(1L, AESUtil.encryptHex("13800000001"), "用户1", "open1", "avatar1");
            AppUser u2 = buildAppUser(2L, AESUtil.encryptHex("13800000002"), "用户2", "open2", "avatar2");

            Page<AppUser> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of(u1, u2));
            mockPage.setSize(10);
            mockPage.setPages(1);
            when(appUserMapper.selectPage(any(), any())).thenReturn(mockPage);

            // When
            BasePageDTO<AppUserDTO> result = appUserService.getUserList(reqDTO);

            // Then
            assertThat(result.getList()).hasSize(2);
            assertThat(result.getList().get(0).getId()).isEqualTo(1L);
            assertThat(result.getList().get(0).getNickName()).isEqualTo("用户1");
            assertThat(result.getList().get(1).getId()).isEqualTo(2L);
            assertThat(result.getList().get(1).getNickName()).isEqualTo("用户2");
        }
    }
}
