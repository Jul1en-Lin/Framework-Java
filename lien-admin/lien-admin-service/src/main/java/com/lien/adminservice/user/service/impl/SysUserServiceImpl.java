package com.lien.adminservice.user.service.impl;


import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lien.adminservice.user.domain.dto.PasswordLoginDTO;
import com.lien.adminservice.user.domain.entity.SysUser;
import com.lien.adminservice.user.mapper.SysUserMapper;
import com.lien.adminservice.user.service.ISysUserService;
import com.lien.common.core.utils.AESUtil;
import com.lien.common.core.utils.VerifyUtil;
import domain.EnumCode;
import domain.dto.LoginUserDTO;
import domain.dto.TokenDTO;
import domain.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.TokenService;


/**
 * B端用户登录服务实现类
 */
@Service
public class SysUserServiceImpl implements ISysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private TokenService tokenService;

    @Override
    public TokenDTO login(PasswordLoginDTO passwordLoginDTO) {
        // 校验手机号是否符合规范
        if (!VerifyUtil.checkPhone(passwordLoginDTO.getPhone())) {
            throw new ServiceException(EnumCode.ERROR_PHONE_FORMAT);
        }

        // 校验手机号是否在库中
        // 入库的手机号需加密，故加密后校验
        String encryptedPhone = AESUtil.encryptHex(passwordLoginDTO.getPhone());
        SysUser sysUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhoneNumber, encryptedPhone));
        if (sysUser == null) {
            throw new ServiceException("手机号不存在", EnumCode.INVALID_PARA.getCode());
        }

        // 进行密码校验
        // 先解密前端传来的密码，再经过加密。与数据库中存储的密码进行比对
        String decryptedPassword = AESUtil.decryptHex(passwordLoginDTO.getPassword());
        if (decryptedPassword == null || decryptedPassword.isBlank()) {
           throw new ServiceException("密码不能为空", EnumCode.INVALID_PARA.getCode());
        }
        // 再加密
        String encryptedPassword = DigestUtil.sha256Hex(decryptedPassword);
        if (!sysUser.getPassword().equals(encryptedPassword)) {
            throw new ServiceException("密码错误", EnumCode.INVALID_PARA.getCode());
        }

        // 校验用户状态（若为 disable 则无法登录）
        if (sysUser.getStatus().equals("diable")) {
            throw new ServiceException(EnumCode.USER_DISABLE);
        }

        // 生成 Token 令牌
        LoginUserDTO loginUserDTO = new LoginUserDTO();
        loginUserDTO.setUserId(sysUser.getId());
        loginUserDTO.setUserName(sysUser.getNickName());
        loginUserDTO.setUserFrom("sys");
        TokenDTO token = tokenService.createToken(loginUserDTO);
        return token;
    }
}
