package com.lien.api.map.domain.dto;

import domain.dto.BasePageReqDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 请求查询 DTO
 */
@Data
public class SearchReqDTO extends BasePageReqDTO {

    /**
     * 请求的关键字
     */
    @NotNull(message = "请求关键字不允许为空")
    private String keyword;

    /**
     * 请求区域的城市ID
     */
    @NotNull(message = "请求区域ID不能为空")
    private Long regionId;
}
