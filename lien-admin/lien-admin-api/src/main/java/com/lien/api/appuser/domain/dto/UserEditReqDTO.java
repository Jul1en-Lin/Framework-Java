package com.lien.api.appuser.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户编辑请求 DTO
 */
@Data
public class UserEditReqDTO {

    /**
     * 用户ID
     *<P>
     *     指定要编辑的用户ID
     *</P>
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    // ------- 支持的编辑字段 -------
    /**
     * 用户昵称
     */
    @NotNull(message = "用户昵称不能为空")
    private String nickName;

    /**
     * 用户头像
     */
    @NotNull(message = "用户头像不能为空")
    private String avatar;
}
