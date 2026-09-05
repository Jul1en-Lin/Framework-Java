package com.lien.adminservice.map.service;

import com.lien.adminservice.map.domain.dto.RegionCityDTO;
import com.lien.adminservice.map.domain.dto.SearchPoiDTO;
import com.lien.adminservice.map.domain.dto.SysRegionDTO;
import com.lien.api.map.domain.dto.LocationReqDTO;
import com.lien.api.map.domain.dto.SearchReqDTO;
import com.lien.common.core.domain.dto.BasePageDTO;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 城市服务接口
 */
public interface IMapService {
    /**
     * 城市列表查询
     * @return 城市列表信息
     */
    List<SysRegionDTO> getCityList();

    /**
     * 城市拼音归类查询
     * @return 城市字母与城市列表的哈希
     */
    Map<String, List<SysRegionDTO>> getCityListPy();

    /**
     * 根据父级区域 ID 获取子集区域列表
     * @param parentId 父级区域ID
     * @return 子集区域列表
     */
    List<SysRegionDTO> getRegionChildrenList(@NotNull Long parentId);

    /**
     * 获取热门城市列表
     * @return 城市列表
     */
    List<SysRegionDTO> getHotCityList();

    /**
     * 根据地点搜索
     * @param placeSearchReqDTO 搜索条件
     * @return 搜索结果
     */
    BasePageDTO<SearchPoiDTO> searchPlaceByRegion(SearchReqDTO placeSearchReqDTO);

    /**
     * 根据经纬度来定位城市
     * @param locationReqDTO 经纬度信息
     * @return 城市信息
     */
    RegionCityDTO getCityByLocation(LocationReqDTO locationReqDTO);
}
