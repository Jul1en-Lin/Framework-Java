package com.lien.api.map.domain.vo;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RegionCityVO {
    /**
     * 城市ID
     */
    private Long id;

    /**
     * 城市名称
     */
    private String name;

    /**
     * 城市全称
     */
    private String fullName;
}
