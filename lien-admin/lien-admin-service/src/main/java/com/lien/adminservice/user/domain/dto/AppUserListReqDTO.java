package com.lien.adminservice.user.domain.dto;

import com.lien.common.core.domain.dto.BasePageDTO;
import domain.dto.BasePageReqDTO;
import lombok.Data;

/**
 * 用户列表查询请求报文
 * <p>
 *     参数非必填，可组合查询
 * </p>
 */
@Data
public class AppUserListReqDTO extends BasePageReqDTO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 手机号
     */
    private String phoneNumber;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 微信 openId
     */
    private String openId;
}
