package com.lien.adminservice.user.controller;

import com.lien.adminservice.user.service.IAppUserService;
import com.lien.api.appuser.domain.dto.AppUserDTO;
import com.lien.api.appuser.domain.vo.AppUserVO;
import com.lien.api.appuser.feign.AppUserFeignClient;
import domain.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * C 端用户相关接口
 */
@Controller
@RequestMapping("/app_user")
public class AppUserController implements AppUserFeignClient {

    @Autowired
    private IAppUserService appUserService;


    @Override
    public Result<AppUserVO> registerByOpenId(String openId) {
        AppUserDTO appUserDTO = appUserService.registerByOpenId(openId);
        return Result.success(appUserDTO.convertToVO());
    }
}
