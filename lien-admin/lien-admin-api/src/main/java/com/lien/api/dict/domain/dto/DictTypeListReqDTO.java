package com.lien.api.dict.domain.dto;

import domain.dto.BasePageReqDTO;
import lombok.Data;

/**
 * 字典类型列表请求DTO
 */
@Data
public class DictTypeListReqDTO extends BasePageReqDTO {

    /**
     * 字典类型值
     */
    private String value;

    /**
     * 字典类型键
     */
    private String typeKey;
}
