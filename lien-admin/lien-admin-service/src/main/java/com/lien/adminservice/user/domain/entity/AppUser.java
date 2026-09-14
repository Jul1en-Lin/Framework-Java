package com.lien.adminservice.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * C 端用户表
 */
@Getter
@Setter
@TableName("app_user")
public class AppUser {

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
     * 微信用户唯一标识
     */
    private String openId;

    /**
     * 头像
     */
    private String avatar;
}
