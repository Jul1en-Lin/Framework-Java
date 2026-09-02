package com.lien.file.entity.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 返回前端的必要直传签名字段
 */
@Getter
@Setter
public class SignVO {

    private String signature;

    private String host;

    private String pathPrefix;

    private String xOSSCredential;

    private String xOSSDate;

    private String policy;
}
