package com.lien.adminservice.user.service;

import com.lien.api.appuser.domain.dto.AppUserDTO;
import com.lien.api.appuser.domain.dto.UserEditReqDTO;

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

    /**
     * 编辑C端用户
     * @param userEditReqDTO C 端用户 DTO
     * @return 所编辑用户的 ID
     */
    Long edit(UserEditReqDTO userEditReqDTO);
}
