package com.lien.adminservice.user.service.impl;


import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lien.adminservice.dict.service.ISysDictionaryService;
import com.lien.adminservice.user.domain.dto.PasswordLoginDTO;
import com.lien.adminservice.user.domain.dto.SysUserDTO;
import com.lien.adminservice.user.domain.dto.SysUserListReqDTO;
import com.lien.adminservice.user.domain.entity.SysUser;
import com.lien.adminservice.user.mapper.SysUserMapper;
import com.lien.adminservice.user.service.ISysUserService;
import com.lien.common.core.utils.AESUtil;
import com.lien.common.core.utils.VerifyUtil;
import domain.EnumCode;
import domain.dto.LoginUserDTO;
import domain.dto.TokenDTO;
import domain.exception.ServiceException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import service.TokenService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * B端用户登录服务实现类
 */
@Service
public class SysUserServiceImpl implements ISysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysDictionaryService sysDictionaryService;

    private static final String STATUS_DISABLE = "disable";

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
        if (STATUS_DISABLE.equals(sysUser.getStatus())) {
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addOrEdit(SysUserDTO sysUserDTO) {
        SysUser sysUser = new SysUser();

        // 处理新增的逻辑
        if (sysUserDTO.getUserId() == null) {
            // 校验手机号
            if (!VerifyUtil.checkPhone(sysUserDTO.getPhoneNumber())) {
                throw new ServiceException("手机格式错误", EnumCode.INVALID_PARA.getCode());
            }
//            // 校验密码
//            if (StringUtils.isEmpty(sysUserDTO.getPassword())) {
//                throw new ServiceException("密码校验失败", EnumCode.INVALID_PARA.getCode());
//            }

            // 手机号唯一性判断
            String encryptedPhone = AESUtil.encryptHex(sysUserDTO.getPhoneNumber());
            SysUser existSysUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhoneNumber,encryptedPhone));
            if (existSysUser != null) {
                throw new ServiceException("手机号已经被占用", EnumCode.INVALID_PARA.getCode());
            }

            // 判断身份信息在字典数据里是否包含
            if (sysDictionaryService.getDicDataByKey(sysUserDTO.getIdentity()) == null) {
                throw new ServiceException("用户身份错误", EnumCode.INVALID_PARA.getCode());
            }

            // 执行新增用户逻辑
            sysUser.setPhoneNumber(AESUtil.encryptHex(sysUserDTO.getPhoneNumber()));
            sysUser.setPassword(DigestUtil.sha256Hex(sysUserDTO.getPassword()));
            sysUser.setIdentity(sysUserDTO.getIdentity());
            sysUser.setStatus(sysUserDTO.getStatus());
            sysUser.setNickName(sysUserDTO.getNickName());
            if (StringUtils.isNotBlank(sysUserDTO.getRemark())) {
                sysUser.setRemark(sysUserDTO.getRemark());
            }
            sysUserMapper.insert(sysUser);
            return sysUser.getId();
        }

        // 跳转到用户信息编辑逻辑
        sysUser.setId(sysUserDTO.getUserId());
        SysUser existUser = sysUserMapper.selectById(sysUserDTO.getUserId());
        if (existUser == null) {
           throw new ServiceException("用户不存在", EnumCode.INVALID_PARA.getCode());
        }
        // 判断用户状态，若为不合法字段则不允许编辑
        if (sysDictionaryService.getDicDataByKey(sysUserDTO.getStatus()) == null) {
            throw new ServiceException("用户状态错误，不允许编辑", EnumCode.INVALID_PARA.getCode());
        }
        // 如果用户状态 status 设置为 disable 则视为踢人
        if (STATUS_DISABLE.equals(sysUserDTO.getStatus())) {
            tokenService.delLoginUser(sysUserDTO.getUserId(), "sys");
        }

        // 除了密码和手机号之外的其他字段都可以编辑
        sysUser.setIdentity(sysUserDTO.getIdentity());
        sysUser.setNickName(sysUserDTO.getNickName());
        sysUser.setStatus(sysUserDTO.getStatus());
        sysUser.setRemark(sysUserDTO.getRemark());
        sysUserMapper.updateById(sysUser);

        return sysUser.getId();

    }

    /**
     * @param sysUserListReqDTO 用户查询 DTO
     * @return
     */
    @Override
    public List<SysUserDTO> getUserList(SysUserListReqDTO sysUserListReqDTO) {
        // 构造查询条件
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(sysUserListReqDTO.getPhoneNumber())) {
            queryWrapper.eq(SysUser::getPhoneNumber, AESUtil.encryptHex(sysUserListReqDTO.getPhoneNumber()));
        }
        if (sysUserListReqDTO.getUserId() != null) {
            queryWrapper.eq(SysUser::getId, sysUserListReqDTO.getUserId());
        }
        if (StringUtils.isNotBlank(sysUserListReqDTO.getStatus())) {
            queryWrapper.eq(SysUser::getStatus, sysUserListReqDTO.getStatus());
        }

        // 查询数据
        List<SysUser> sysUsers = sysUserMapper.selectList(queryWrapper);
        return sysUsers.stream().map(sysUser -> {
            SysUserDTO sysUserDTO = new SysUserDTO();
            sysUserDTO.setUserId(sysUser.getId());
            sysUserDTO.setIdentity(sysUser.getIdentity());
            sysUserDTO.setPhoneNumber(AESUtil.decryptHex(sysUser.getPhoneNumber()));
            sysUserDTO.setNickName(sysUser.getNickName());
            sysUserDTO.setStatus(sysUser.getStatus());
            sysUserDTO.setRemark(sysUser.getRemark());
            return sysUserDTO;
        }).collect(Collectors.toList());
    }
}
