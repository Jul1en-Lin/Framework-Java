package com.lien.adminservice.user.service;

import com.lien.api.appuser.domain.dto.AppUserDTO;

public interface IAppUserService {

    /**
     * 根据微信用户唯一标识注册 C 端用户
     * @param openId 用户唯一标识微信 ID
     * @return C 端用户 DTO
     */
    AppUserDTO registerByOpenId(String openId);

    /**
     * 根据 openId 查询用户信息
     * @param openId 用户微信ID
     * @return C 端用户DTO
     */
    AppUserDTO findByOpenId(String openId);
}
