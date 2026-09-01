package com.lien.file.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 *  AliYun OSS V4 签名必传参数
 */
@Getter
@Setter
public class SignDTO {

    /**
     * 签名
     */
    private String signature;

    private String host;

    private String pathPrefix;

    private String xOSSCredential;

    private String xOSSDate;

    private String policy;
}
