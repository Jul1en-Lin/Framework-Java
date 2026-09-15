package com.lien.portalservice.service;

import com.lien.api.appuser.domain.dto.AppUserDTO;
import com.lien.api.appuser.domain.dto.UserEditReqDTO;
import com.lien.portalservice.domain.dto.LoginDTO;
import com.lien.portalservice.domain.dto.UserDTO;
import com.lien.portalservice.domain.dto.WechatLoginDTO;
import com.lien.portalservice.domain.vo.UserVO;
import domain.dto.TokenDTO;

public interface UserService {

    /**
     * 登录通用方法
     * @param loginDTO 登录基类DTO
     * @return token 令牌 DTO
     */
    TokenDTO login(LoginDTO loginDTO);

    /**
     * 修改用户信息
     * @param userEditReqDTO C端用户编辑 DTO
     * @return 编辑用户的 Id
     */
    Long edit(UserEditReqDTO userEditReqDTO);

    /**
     * 获取 C 端用户登录信息
     * @return C 端用户信息 DTO
     */
    UserDTO getLoginUser();
}
