package com.lien.adminservice.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lien.adminservice.user.config.RabbitMqConfig;
import com.lien.adminservice.user.domain.entity.AppUser;
import com.lien.adminservice.user.mapper.AppUserMapper;
import com.lien.adminservice.user.service.IAppUserService;
import com.lien.api.appuser.domain.dto.AppUserDTO;
import com.lien.api.appuser.domain.dto.UserEditReqDTO;
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
}
