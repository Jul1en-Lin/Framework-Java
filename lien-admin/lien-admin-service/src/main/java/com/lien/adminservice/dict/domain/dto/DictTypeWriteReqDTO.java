package com.lien.adminservice.dict.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 字典类型写操作DTO
 */
@Data
public class DictTypeWriteReqDTO {

    /**
     * 字典类型值
     */
    @NotNull(message = "字典类型值不能为空")
    private String value;

    /**
     * 字典类型键
     */
    @NotNull(message = "字典类型键不能为空")
    private String typeKey;

    /**
     * 备注
     */
    private String remark;
}
