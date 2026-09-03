package com.lien.api.domain.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 区域信息VO
 */
@Setter
@Getter
public class RegionVO {
    /**
     * 区域ID
     */
    private Long id;

    /**
     * 区域名称
     */
    private String name;

    /**
     * 区域全称
     */
    private String fullName;

    /**
     * 父级区域ID
     */
    private Long parentId;

    /**
     * 拼音
     */
    private String pinyin;

    /**
     * 级别
     */
    private Integer level;

    /**
     * 经度
     */
    private Double longitude;

    /**
     * 纬度
     */
    private Double latitude;
}
