package com.lien.adminservice.user.service.impl;

import com.lien.adminservice.user.domain.entity.AppUser;
import com.lien.adminservice.user.mapper.AppUserMapper;
import com.lien.adminservice.user.service.IAppUserService;
import com.lien.api.appuser.domain.dto.AppUserDTO;
import com.lien.common.core.utils.BeanUtil;
import domain.EnumCode;
import domain.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
        appUserDTO.setUserId(appUser.getId());
        BeanUtil.copyProperties(appUser, appUserDTO);

        return appUserDTO;
    }
}
