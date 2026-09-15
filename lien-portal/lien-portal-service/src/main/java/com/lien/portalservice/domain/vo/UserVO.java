package com.lien.portalservice.domain.vo;

import domain.vo.LoginUserVO;
import lombok.Data;

/**
 * C 端用户登录信息 VO
 */
@Data
public class UserVO extends LoginUserVO {

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 昵称
     */
    private String nickName;
}
