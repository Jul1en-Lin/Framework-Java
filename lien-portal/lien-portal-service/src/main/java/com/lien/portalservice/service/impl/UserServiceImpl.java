package com.lien.portalservice.service.impl;

import com.lien.api.appuser.domain.dto.UserEditReqDTO;
import com.lien.api.appuser.domain.vo.AppUserVO;
import com.lien.api.appuser.feign.AppUserFeignClient;
import com.lien.common.core.utils.BeanUtil;
import com.lien.portalservice.domain.dto.LoginDTO;
import com.lien.portalservice.domain.dto.UserDTO;
import com.lien.portalservice.domain.dto.WechatLoginDTO;
import com.lien.portalservice.service.UserService;
import domain.EnumCode;
import domain.Result;
import domain.dto.LoginUserDTO;
import domain.dto.TokenDTO;
import domain.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.TokenService;
import utils.JwtUtil;
import utils.SecurityUtil;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    // 登录来源
    private final String LOGIN_FROM_APP = "app";

    @Autowired
    private AppUserFeignClient appUserFeignClient;

    @Autowired
    private TokenService tokenService;


    /**
     * @param loginDTO 登录基类DTO
     * @return TokenDTO 令牌信息
     */
    @Override
    public TokenDTO login(LoginDTO loginDTO) {
        LoginUserDTO loginUserDTO = new LoginUserDTO();
        // 逻辑分发，判断是微信登录还是短信登录

        // 微信登录
        if (loginDTO instanceof WechatLoginDTO wechatloginDTO) {
            loginWithWechat(wechatloginDTO, loginUserDTO);
        }
        // TODO：短信登录

        // 生成令牌并返回
        return tokenService.createToken(loginUserDTO);
    }

    /**
     * @param userEditReqDTO C端用户编辑 DTO
     * @return 所编辑用户的 ID
     */
    @Override
    public Long edit(UserEditReqDTO userEditReqDTO) {
       return appUserFeignClient.edit(userEditReqDTO).getData();
    }

    /**
     * @return C 端用户登录信息 DTO
     */
    @Override
    public UserDTO getLoginUser() {
        // 获取当前登录的用户信息
        LoginUserDTO loginUserDTO = tokenService.getUserInfo();
        if (loginUserDTO == null) {
            throw new ServiceException(EnumCode.TOKEN_INVALID);
        }
        // 远程调用获取用户信息
        Result<AppUserVO> result = appUserFeignClient.findById(loginUserDTO.getUserId());
        if (result == null || result.getCode() != EnumCode.SUCCESS.getCode() || result.getData() == null) {
            throw new ServiceException("查询用户失败", EnumCode.FAILED.getCode());
        }
        // 对象拼装，补全 UserDTO 所需的全部字段
        UserDTO userDTO = new UserDTO();
        BeanUtil.copyProperties(loginUserDTO, userDTO);
        BeanUtil.copyProperties(result.getData(), userDTO);
        return userDTO;
    }

    @Override
    public void logout() {
        // 1 解析令牌
        String token = SecurityUtil.getToken();
        if (StringUtils.isEmpty(token)) {
            return;
        }
        String userName = JwtUtil.getUserName(token);
        String userId = JwtUtil.getUserId(token);
        log.info("{}退出系统, 用户ID{}", userName, userId);
        // 2 删除用户缓存记录
        String userFrom = JwtUtil.getUserFrom(token);
        tokenService.delLoginUser(Long.valueOf(userId), userFrom);
    }

    /**
     * 微信登录逻辑
     * @param wechatLoginDTO 微信登录 DTO
     * @param loginUserDTO 登录基类
     */
    private void loginWithWechat(WechatLoginDTO wechatLoginDTO, LoginUserDTO loginUserDTO) {
        AppUserVO appUserVO;
        // 根据 openId 进行查询数据库
        Result<AppUserVO> result = appUserFeignClient.findByOpenId(wechatLoginDTO.getOpenId());
        if (result.getCode() != EnumCode.SUCCESS.getCode() || result.getData() == null) {
            // 没查到，需要进行注册
            appUserVO = register(wechatLoginDTO);
        } else {
            // 提取数据体
            appUserVO = result.getData();
        }
        // 设置登录信息，用于生成令牌
        loginUserDTO.setUserFrom(LOGIN_FROM_APP);
        loginUserDTO.setUserId(appUserVO.getUserId());
        loginUserDTO.setUserName(appUserVO.getNickName());
    }

    /**
     * 用户通用注册方法
     * @param loginDTO
     * @return
     */
    private AppUserVO register(LoginDTO loginDTO) {
        Result<AppUserVO> result = new Result<>();
        // 逻辑分发

        // 微信注册
        if (loginDTO instanceof WechatLoginDTO wechatLoginDTO) {
            result = appUserFeignClient.registerByOpenId(wechatLoginDTO.getOpenId());
            if (result.getCode() != EnumCode.SUCCESS.getCode() || result.getData() == null) {
                log.warn("微信用户注册失败,openId:{}",wechatLoginDTO.getOpenId());
                throw new ServiceException("微信用户注册失败", EnumCode.ERROR.getCode());
            }
        }

        // TODO：短信注册
        return result.getData();
    }
}
