package com.lien.adminservice.user.domain.dto;


import lombok.Data;

import java.io.Serializable;

/**
 * B 端用户查询请求DTO
 * <p>
 *      三个参数不做必填项，不填则查询符合条件的所有用户
 * </p>
 */
@Data
public class SysUserListReqDTO implements Serializable {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 手机号
     */
    private String phoneNumber;

    /**
     * 状态
     */
    private String status;
}
