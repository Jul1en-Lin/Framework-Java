package com.lien.api.dict.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典数据新增请求DTO
 */
@Data
public class DictDataAddReqDTO {

    /**
     * 字典类型业务主键
     */
    @NotBlank(message = "字典类型业务主键不能为空")
    private String typeKey;

    /**
     * 字典数据业务主键
     */
    @NotBlank(message = "字典数据业务主键不能为空")
    private String dataKey;

    /**
     * 字典数据名称
     */
    @NotBlank(message = "字典数据名称不能为空")
    private String value;

    /**
     * 备注
     */
    private String remark;

    /**
     * 排序
     */
    private Integer sort;
}
