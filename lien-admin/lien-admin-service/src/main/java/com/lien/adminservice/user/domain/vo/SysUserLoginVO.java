package com.lien.adminservice.user.domain.vo;

import domain.vo.LoginUserVO;
import lombok.Data;

/**
 * B 端用户登录信息 VO
 */
@Data
public class SysUserLoginVO extends LoginUserVO {

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
}
