package com.lien.adminservice.dict.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统参数表。
 *
 * <p>参数以 key-value 的形式保存，status 为 1 表示启用，0 表示停用。</p>
 */
@Getter
@Setter
@TableName("sys_argument")
public class SysArgument {

    /**
     * 自增主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 参数键（业务主键）
     */
    @TableField("config_key")
    private String configKey;

    /**
     * 参数名称
     */
    private String name;

    /**
     * 参数值（具体数字，调整数值）
     */
    private String value;

    /**
     * 备注
     */
    private String remark;

}
