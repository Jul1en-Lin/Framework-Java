package com.lien.adminservice.user.controller;

import com.lien.adminservice.user.domain.dto.AppUserListReqDTO;
import com.lien.adminservice.user.service.IAppUserService;
import com.lien.api.appuser.domain.dto.AppUserDTO;
import com.lien.api.appuser.domain.dto.UserEditReqDTO;
import com.lien.api.appuser.domain.vo.AppUserVO;
import com.lien.api.appuser.feign.AppUserFeignClient;
import com.lien.common.core.domain.dto.BasePageDTO;
import com.lien.common.core.utils.BeanUtil;
import domain.Result;
import domain.vo.BasePageVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端用户相关接口
 */
@RestController
@RequestMapping("/app_user")
public class AppUserController implements AppUserFeignClient {

    @Autowired
    private IAppUserService appUserService;


    /**
     * @param openId 用户微信ID
     * @return
     */
    @Override
    public Result<AppUserVO> findByOpenId(String openId) {
        if (!StringUtils.isNotBlank(openId)) {
            return Result.fail("微信 openId 不能为空");
        }
        AppUserDTO appUserDTO = appUserService.findByOpenId(openId);
        if (appUserDTO == null) {
            return Result.success(null);
        }
        return Result.success(appUserDTO.convertToVO());
    }

    @Override
    public Result<AppUserVO> registerByOpenId(String openId) {
        if (!StringUtils.isNotBlank(openId)) {
            return Result.fail("微信 openId 不能为空");
        }
        AppUserDTO appUserDTO = appUserService.registerByOpenId(openId);
        return Result.success(appUserDTO.convertToVO());
    }


    @Override
    public Result<Long> edit(UserEditReqDTO userEditReqDTO) {
        Long userId = appUserService.edit(userEditReqDTO);
        return Result.success(userId);
    }

    /**
     * 查询 C 端用户
     * @param appUserListReqDTO 查询 C 端用户参数 DTO
     * @return C 端用户列表分页结果
     */
    @PostMapping("/list/search")
    public Result<BasePageVO<AppUserVO>> list(@RequestBody AppUserListReqDTO appUserListReqDTO) {
        BasePageDTO<AppUserDTO> appUserDTOList = appUserService.getUserList(appUserListReqDTO);
        BasePageVO<AppUserVO> result = new BasePageVO<>();
        BeanUtil.copyProperties(appUserDTOList, result);
        return Result.success(result);
    }
}
