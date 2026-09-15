package com.lien.adminservice.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lien.adminservice.user.config.RabbitMqConfig;
import com.lien.adminservice.user.domain.dto.AppUserListReqDTO;
import com.lien.adminservice.user.domain.entity.AppUser;
import com.lien.adminservice.user.mapper.AppUserMapper;
import com.lien.adminservice.user.service.IAppUserService;
import com.lien.api.appuser.domain.dto.AppUserDTO;
import com.lien.api.appuser.domain.dto.UserEditReqDTO;
import com.lien.common.core.domain.dto.BasePageDTO;
import com.lien.common.core.utils.AESUtil;
import com.lien.common.core.utils.BeanUtil;
import domain.EnumCode;
import domain.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * C 端用户服务实现类
 */
@Slf4j
@Service
@RefreshScope
public class AppUserServiceImpl implements IAppUserService {

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${appuser.info.defaultAvatar}")
    private String defaultAvatar;

    /**
     * @param openId 用户唯一标识微信 ID
     * @return AppUserDTO 用户对象 DTO
     */
    @Override
    public AppUserDTO registerByOpenId(String openId) {
        if (!StringUtils.isNotBlank(openId)) {
            throw new ServiceException("微信 Id 不能为空", EnumCode.INVALID_PARA.getCode());
        }
        // 注册新用户
        AppUser appUser = new AppUser();
        appUser.setOpenId(openId);
        appUser.setNickName("用户"+ (int) ((Math.random() * 9000) + 1000));
        appUser.setAvatar(defaultAvatar);
        appUserMapper.insert(appUser);
        // 转换对象
        AppUserDTO appUserDTO = new AppUserDTO();
        BeanUtil.copyProperties(appUser, appUserDTO);
        return appUserDTO;
    }

    /**
     * @param openId 用户微信ID
     * @return
     */
    @Override
    public AppUserDTO findByOpenId(String openId) {
        // 查询数据库
        LambdaQueryWrapper<AppUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AppUser::getOpenId, openId);
        AppUser appUser = appUserMapper.selectOne(queryWrapper);
        if (appUser == null) {
            return null;
        }

        AppUserDTO appUserDTO = new AppUserDTO();
        BeanUtil.copyProperties(appUser, appUserDTO);
        // appUserDTO.setId(appUser.getId());
        return appUserDTO;
    }

    /**
     * @param userEditReqDTO C 端用户 DTO
     */
    @Override
    public Long edit(UserEditReqDTO userEditReqDTO) {
        AppUser appUser = appUserMapper.selectById(userEditReqDTO.getUserId());
        if (appUser == null) {
            throw new ServiceException("用户不存在", EnumCode.FAILED .getCode());
        }
        // 更新用户信息
        appUser.setNickName(userEditReqDTO.getNickName());
        appUser.setAvatar(userEditReqDTO.getAvatar());
        appUserMapper.updateById(appUser);

        // 分发广播消息
        AppUserDTO appUserDTO = new AppUserDTO();
        BeanUtil.copyProperties(appUser, appUserDTO);
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_NAME, "", appUserDTO);
        } catch (AmqpException e) {
            log.error("编辑用户发送消息失败", e);
        }
        return appUser.getId();
    }

    /**
     * @param appUserListReqDTO 查询 C 端用户参数 DTO
     * @return C 端用户列表分页结果
     */
    @Override
    public BasePageDTO<AppUserDTO> getUserList(AppUserListReqDTO appUserListReqDTO) {
        LambdaQueryWrapper<AppUser> queryWrapper = new LambdaQueryWrapper<>();
        // 查询条件拼接
        if (appUserListReqDTO.getUserId() != null) {
            queryWrapper.eq(AppUser::getId, appUserListReqDTO.getUserId());
        }
        // 手机号查询时需要加密后再查询
        if (StringUtils.isNotBlank(appUserListReqDTO.getPhoneNumber())) {
            queryWrapper.eq(AppUser::getPhoneNumber, AESUtil.encryptHex(appUserListReqDTO.getPhoneNumber()));
        }
        if (StringUtils.isNotBlank(appUserListReqDTO.getNickName())) {
            queryWrapper.like(AppUser::getNickName, appUserListReqDTO.getNickName());
        }
        if (StringUtils.isNotBlank(appUserListReqDTO.getOpenId())) {
            queryWrapper.eq(AppUser::getOpenId, appUserListReqDTO.getOpenId());
        }
        // 分页查询
        long pageSize = (long) appUserListReqDTO.getPageSize();
        long pageNumber = (long) appUserListReqDTO.getPageNo();
        Page<AppUser> appUserPage = appUserMapper.selectPage(new Page<>(pageNumber, pageSize), queryWrapper);

        // 对象转换
        List<AppUserDTO> appUserDTOList = appUserPage.getRecords().stream()
                .map(appUser -> {
                    AppUserDTO appUserDTO = new AppUserDTO();
                    BeanUtil.copyProperties(appUser, appUserDTO);
                    return appUserDTO;
                }).toList();

        // 赋值
        BasePageDTO<AppUserDTO> result = new BasePageDTO<>();
        result.setTotals((int) appUserPage.getTotal());
        result.setTotalPages(Integer.parseInt(String.valueOf(appUserPage.getPages())));
        result.setList(appUserDTOList);
        return result;
    }
}
