package com.lien.api.domain.dto;

import lombok.Data;

/**
 * 腾讯地图响应基类
 */
@Data
public class TencentMapBaseDTO {

    /**
     * 响应码  0表示成功
     */
    private Integer status;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 请求ID
     */
    private String request_id;
}
