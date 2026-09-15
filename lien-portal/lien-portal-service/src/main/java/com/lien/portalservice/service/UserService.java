package com.lien.portalservice.service;

import com.lien.api.appuser.domain.dto.AppUserDTO;
import com.lien.portalservice.domain.dto.LoginDTO;
import com.lien.portalservice.domain.dto.WechatLoginDTO;
import domain.dto.TokenDTO;

public interface UserService {

    /**
     * 登录通用方法
     * @param loginDTO 登录基类DTO
     * @return token 令牌 DTO
     */
    TokenDTO login(LoginDTO loginDTO);
}
