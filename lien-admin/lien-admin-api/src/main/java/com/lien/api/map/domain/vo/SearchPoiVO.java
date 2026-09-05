package com.lien.api.map.domain.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 查询地点结果VO
 */
@Getter
@Setter
public class SearchPoiVO {
    /**
     * 地点名称
     */
    private String title;

    /**
     * 地点地址
     */
    private String address;

    /**
     * 经度
     */
    private Double longitude;

    /**
     * 纬度
     */
    private Double latitude;
}
