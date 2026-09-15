package com.lien.portalservice.controller;

import com.lien.api.appuser.domain.dto.UserEditReqDTO;
import com.lien.common.core.utils.BeanUtil;
import com.lien.portalservice.domain.dto.WechatLoginDTO;
import com.lien.portalservice.service.UserService;
import domain.Result;
import domain.dto.TokenDTO;
import domain.vo.TokenVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 微信登录
     * @param wechatLoginDTO 微信登录DTO
     * @return token令牌
     */
    @PostMapping("/login/wechat")
    public Result<TokenVO> login(@RequestBody @Validated WechatLoginDTO wechatLoginDTO) {
        TokenDTO tokenDTO = userService.login(wechatLoginDTO);
        TokenVO tokenVO = new TokenVO();
        BeanUtil.copyProperties(tokenDTO, tokenVO);
        return Result.success(tokenVO);
    }

    /**
     * 修改用户信息
     * @param userEditReqDTO C端用户编辑DTO
     * @return 编辑用户的 Id
     */
    @PostMapping("/edit")
    public Result<Long> edit(@RequestBody @Validated UserEditReqDTO userEditReqDTO) {
        Long userId = userService.edit(userEditReqDTO);
        return Result.success(userId);
    }
}
