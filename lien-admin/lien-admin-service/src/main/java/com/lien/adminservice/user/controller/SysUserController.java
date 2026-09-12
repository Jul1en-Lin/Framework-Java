package com.lien.adminservice.user.controller;


import com.lien.adminservice.user.domain.dto.PasswordLoginDTO;
import com.lien.adminservice.user.domain.dto.SysUserDTO;
import com.lien.adminservice.user.service.ISysUserService;
import com.lien.common.core.utils.BeanUtil;
import domain.Result;
import domain.dto.TokenDTO;
import domain.vo.TokenVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * B端用户服务控制器类
 */
@RestController
@RequestMapping("/sys_user")
public class SysUserController {

    @Autowired
    private ISysUserService sysUserService;

    /**
     * B端用户登录
     *
     * @param passwordLoginDTO B端用户登录DTO
     * @return token信息
     */
    @PostMapping("/login/password")
    public Result<TokenVO> login(@Validated @RequestBody PasswordLoginDTO passwordLoginDTO) {
        TokenDTO tokenDTO = sysUserService.login(passwordLoginDTO);
        TokenVO result = new TokenVO();
        BeanUtil.copyProperties(tokenDTO, result);
        return Result.success(result);
    }


    /**
     * 新增或编辑用户
     * @param sysUserDTO B端用户信息
     * @return  用户ID
     */
    @PostMapping("/add_edit")
    public Result<Long> addOrEditUser(@Validated @RequestBody SysUserDTO sysUserDTO) {
        return Result.success(sysUserService.addOrEdit(sysUserDTO));
    }
    
}
