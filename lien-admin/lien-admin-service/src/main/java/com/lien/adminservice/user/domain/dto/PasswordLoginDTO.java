package com.lien.adminservice.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class PasswordLoginDTO implements Serializable {

    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /**
     * 密码（前端传来的加密过后的数据）
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
