package com.lien.portalservice.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 通过微信登录 DTO
 */
@Data
public class WechatLoginDTO extends LoginDTO{

    /**
     * 微信openId
     */
    @NotBlank(message = "微信 openId 不能为空")
    private String openId;
}
