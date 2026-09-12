package com.lien.adminservice.user.service;


import com.lien.adminservice.user.domain.dto.PasswordLoginDTO;
import com.lien.adminservice.user.domain.dto.SysUserDTO;
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

    /**
     * 新增或编辑用户
     * @param sysUserDTO B端用户信息
     * @return 用户ID
     */
    Long addOrEdit(SysUserDTO sysUserDTO);
}
