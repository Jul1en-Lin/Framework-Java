package com.lien.api.dict.domain.vo;

import lombok.Data;


/**
 * 系统参数数据 VO
 * 与系统参数类一致
 */
@Data
public class ArgumentVO {

    /**
     * 自增主键
     */
    private Long id;

    /**
     * 参数名称
     */
    private String name;

    /**
     * 参数业务主键
     */
    private String configKey;

    /**
     * 参数值
     */
    private String value;

    /**
     * 备注
     */
    private String remark;
}
