package com.lien.adminservice.user.domain.dto;

import com.lien.adminservice.user.domain.vo.SysUserLoginVO;
import com.lien.common.core.utils.BeanUtil;
import domain.dto.LoginUserDTO;
import lombok.Data;

/**
 * B端登录用户信息 DTO
 */
@Data
public class SysUserLoginDTO extends LoginUserDTO {

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 身份
     */
    private String identity;

    /**
     * 状态
     */
    private String status;

    /**
     * B 端用户登录信息 DTO 转VO
     * @return B端用户登录信息 VO
     */
    public SysUserLoginVO convertToVO() {
        SysUserLoginVO sysUserLoginVO = new SysUserLoginVO();
        BeanUtil.copyProperties(this, sysUserLoginVO);
        return sysUserLoginVO;
    }
}
