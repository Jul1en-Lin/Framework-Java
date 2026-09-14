package com.lien.api.appuser.domain.vo;

import lombok.Data;


/**
 * C 端用户 VO
 */
@Data
public class AppUserVO {

    /**
     * C端用户ID
     */
    private Long userId;

    /**
     * 用户昵称
     */
    private String nickName;

    /**
     * 手机号
     */
    private String phoneNumber;

    /**
     * 微信ID
     */
    private String openId;

    /**
     * 用户头像
     */
    private String avatar;
}
