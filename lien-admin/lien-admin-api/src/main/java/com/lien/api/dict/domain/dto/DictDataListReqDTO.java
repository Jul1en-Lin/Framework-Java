package com.lien.api.dict.domain.dto;

import domain.dto.BasePageReqDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 获取字典数据列表的请求 DTO
 */
@Data
public class DictDataListReqDTO extends BasePageReqDTO {

    /**
     * 字典类型业务主键
     */
    @NotBlank(message = "字典类型业务主键不能为空")
    private String typeKey;

    /**
     * 字典数据值
     */
    private String value;

}
