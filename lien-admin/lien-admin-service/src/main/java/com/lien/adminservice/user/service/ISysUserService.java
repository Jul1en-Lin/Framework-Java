package com.lien.adminservice.user.service;


import com.lien.adminservice.user.domain.dto.PasswordLoginDTO;
import domain.dto.TokenDTO;

/**
 * B端用户服务接口
 */
public interface ISysUserService {

    /**
     * B端用户登录
     * @param passwordLoginDTO B端用户登录DTO
     * @return Token 令牌信息
     */
    TokenDTO login(PasswordLoginDTO passwordLoginDTO);

}
