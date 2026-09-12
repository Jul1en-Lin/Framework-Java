package com.lien.adminservice.user.domain.dto;

 

import jakarta.validation.constraints.*;
import org.checkerframework.common.value.qual.MatchesRegex;

import com.lien.adminservice.user.domain.vo.SysUserVO;

import lombok.Data;

/**
 * B 端用户信息
 */
@Data 
public class SysUserDTO {
    
    /**
     * B端人员用户ID
     */
    private Long userId;

    /**
     * 身份
     */
    @NotBlank(message = "身份不能为空")
    private String identity;

    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    private String phoneNumber;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(max = 20,message = "密码长度不能超过20位")
    @Pattern(regexp = "^[a-zA-Z0-9]+$")
    private String password;

    /**
     * 昵称
     */
    @NotBlank(message = "昵称不能为空")
    private String nickName;

    /**
     * 状态
     */
    @NotBlank(message = "状态不能为空")
    private String status;

    /**
     * 备注
     */
    private String remark;

    // /**
    //  * 校验密码是否合理
    //  * @return 布尔类型
    //  */
    // public boolean checkPassword() {
    //     return this.password.matches("^[a-zA-Z0-9]+$");
    // }

    /**
     * DTO 转换 VO
     * @return B端用户查询 VO
     */
    public SysUserVO convertToVO() {
        SysUserVO sysUserVo = new SysUserVO();
        sysUserVo.setUserId(this.userId);
        sysUserVo.setIdentity(this.identity);
        sysUserVo.setPhoneNumber(this.phoneNumber);
        sysUserVo.setNickName(this.nickName);
        sysUserVo.setStatus(this.status);
        sysUserVo.setRemark(this.remark);
        return sysUserVo;
    }
}

