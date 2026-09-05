package com.lien.adminservice.map.domain.dto;

import com.lien.api.map.domain.dto.TencentMapBaseDTO;
import lombok.Data;

import java.util.List;

/**
 * 地图 POI
 */
@Data
public class PoiListDTO extends TencentMapBaseDTO {

     /**
     * 本次搜索的结果数
     */
    private Integer count;

    /**
     * 查出来的poi列表
     */
    private List<PoiDTO> data;

    @Data
    public static class PoiDTO {

         /**
         * POI 地点的唯一标识 (regionId)
         */
        private String id;

        /**
         * poi地点的名称
         */
        private String title;

        /**
         * 地址
         */
        private String address;

        /**
         * POI类型，值说明：0:普通POI / 1:公交车站 / 2:地铁站 / 3:公交线路 / 4:行政区划
         */
        private String type;

        /**
         * poi地点所处坐标
         */
        private LocationDTO location;
    }
}
