package com.lien.adminservice.service;

import com.lien.adminservice.domain.dto.GeoResultDTO;
import com.lien.adminservice.domain.dto.LocationDTO;
import com.lien.adminservice.domain.dto.PoiListDTO;
import com.lien.adminservice.domain.dto.SearchParamDTO;

/**
 * 腾讯地图服务接口
 * 封装了搜索的必要 key 与路由地址
 */
public interface ITencentMapService {

    /**
     * 根据关键词搜索地点
     * @param searchParamDTO 搜索条件
     * @return 搜索结果
     */
    PoiListDTO searchPlaceByRegion(SearchParamDTO searchParamDTO);

    /**
     * 根据经纬度来获取区域信息
     * @param locationDTO 经纬度
     * @return 区域信息
     */
    GeoResultDTO getDistrictByLonLat(LocationDTO locationDTO);

}
