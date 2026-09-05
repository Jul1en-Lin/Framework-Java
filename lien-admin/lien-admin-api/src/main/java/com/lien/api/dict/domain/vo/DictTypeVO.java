package com.lien.api.dict.domain.vo;

import lombok.Data;


/**
 * 字典类型VO
 */
@Data
public class DictTypeVO {

    /**
     * 字典类型id
     */
    private Long id;

    /**
     * 字典类型键
     */
    private String typeKey;

    /**
     * 字典类型值
     */
    private String value;

    /**
     * 备注
     */
    private String remark;

    /**
     * 状态
     */
    private Integer status;
}
