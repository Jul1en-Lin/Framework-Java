package com.lien.adminservice.user.controller;


import com.lien.adminservice.user.domain.dto.PasswordLoginDTO;
import com.lien.adminservice.user.domain.dto.SysUserDTO;
import com.lien.adminservice.user.domain.dto.SysUserListReqDTO;
import com.lien.adminservice.user.domain.dto.SysUserLoginDTO;
import com.lien.adminservice.user.domain.vo.SysUserLoginVO;
import com.lien.adminservice.user.domain.vo.SysUserVO;
import com.lien.adminservice.user.service.ISysUserService;
import com.lien.common.core.utils.BeanUtil;
import domain.Result;
import domain.dto.TokenDTO;
import domain.vo.TokenVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


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


    /**
     * 查询 B 端用户
     * @param sysUserListReqDTO 用户查询 DTO
     * @return B 端用户列表
     */
    @PostMapping("/list")
    public Result<List<SysUserVO>> getUserList(@RequestBody SysUserListReqDTO sysUserListReqDTO) {
        List<SysUserDTO> sysUserDTOS = sysUserService.getUserList(sysUserListReqDTO);
        return Result.success(sysUserDTOS.stream()
                .map(SysUserDTO::convertToVO)
                .collect(Collectors.toList())
        );
    }

    /**
     * 从请求中拿到令牌 Header 获取 B 端登录用户信息
     * @return B 端用户信息VO
     */
    @GetMapping("/login/get_info")
    public Result<SysUserLoginVO> getLoginUser() {
        SysUserLoginDTO userLoginDTO = sysUserService.getLoginUser();
        return Result.success(userLoginDTO.convertToVO());
    }
}
