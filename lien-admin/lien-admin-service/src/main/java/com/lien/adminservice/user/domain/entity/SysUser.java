package com.lien.adminservice.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理端人员表
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser {

    /**
     * 自增主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 电话
     */
    private String phoneNumber;

    /**
     * 密码
     */
    private String password;

    /**
     * 身份
     */
    private String identity;

    /**
     * 备注
     */
    private String remark;

    /**
     * 状态
     */
    private String status;
}
