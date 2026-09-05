package com.lien.adminservice.map.domain.dto;

import lombok.Data;
/**
 * 逆地址解析结果
 */
@Data
public class AddrResultDTO {

    /**
    * 具体的详细地址
    */
    private String address;

    /**
     * 城市地址详细信息
     */
    private AddrInfoDTO ad_info;

    /**
     * 详细地址
     */
    @Data
    public static class AddrInfoDTO {

        /**
         * 国家代码
         */
        private String nation_code;

        /**
         * 行政区划代码
         */
        private String adcode;

        /**
         * 城市代码
         */
        private String city_code;

        /**
         * 行政区划名称
         */
        private String name;

        /**
         * 国家
         */
        private String nation;

        /**
         * 省/直辖市
         */
        private String province;

        /**
         * 地级市
         */
        private String city;

        /**
         * 县区一级
         */
        private String district;
    }
}
