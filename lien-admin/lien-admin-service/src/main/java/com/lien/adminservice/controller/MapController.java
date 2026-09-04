package com.lien.adminservice.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lien.adminservice.domain.dto.*;
import com.lien.adminservice.service.IMapService;
import com.lien.adminservice.service.ITencentMapService;
import com.lien.api.constants.MapConstants;
import com.lien.api.domain.dto.LocationReqDTO;
import com.lien.api.domain.dto.SearchReqDTO;
import com.lien.api.domain.vo.RegionCityVO;
import com.lien.api.domain.vo.RegionVO;
import com.lien.api.domain.vo.SearchPoiVO;
import com.lien.api.feign.IMapFeignClient;
import com.lien.common.cache.service.CacheService;
import com.lien.common.core.domain.dto.BasePageDTO;
import com.lien.common.core.utils.BeanUtil;
import com.lien.common.core.utils.PageUtil;
import domain.Result;
import domain.vo.BasePageVO;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * 地图控制器类
 */
@RestController
@Validated
@Slf4j
public class MapController implements IMapFeignClient {

    /**
     * 地图服务
     */
    @Autowired
    private IMapService mapService;

    @Override
    public Result<List<RegionVO>> getCityList() {
        List<SysRegionDTO> dtoList = mapService.getCityList();
        List<RegionVO> result = new ArrayList<>();
        BeanUtil.copyListProperties(dtoList, RegionVO::new).forEach(result::add);
        return Result.success(result);
    }

    @Override
    public Result<Map<String, List<RegionVO>>> getCityListPy() {
        Map<String, List<RegionVO>> result = new TreeMap<>();
        Map<String, List<SysRegionDTO>> cityListPy = mapService.getCityListPy();
        // 遍历转换
        for (Map.Entry<String, List<SysRegionDTO>> entry : cityListPy.entrySet()) {
            List<RegionVO> regionVOList = new ArrayList<>();
            BeanUtil.copyListProperties(entry.getValue(), RegionVO::new).forEach(regionVOList::add);
            result.put(entry.getKey(), regionVOList);
        }
        return Result.success(result);
    }

    @Override
    public Result<List<RegionVO>> getRegionChildrenList(@NotNull Long parentId) {
        List<SysRegionDTO> regionChildrenList = mapService.getRegionChildrenList(parentId);
        List<RegionVO> result = new ArrayList<>();
        BeanUtil.copyListProperties(regionChildrenList, RegionVO::new).forEach(result::add);
        return Result.success(result);
    }

    @Override
    public Result<List<RegionVO>> getHotCityList() {
        List<SysRegionDTO> hotCityList = mapService.getHotCityList();
        List<RegionVO> result = new ArrayList<>();
        BeanUtil.copyListProperties(hotCityList, RegionVO::new).forEach(result::add);
        return Result.success(result);
    }

    @Override
    public Result<BasePageVO<SearchPoiVO>> searchPlaceByRegion(SearchReqDTO searchReqDTO) {
        BasePageDTO<SearchPoiDTO> basePageReqDTO = mapService.searchPlaceByRegion(searchReqDTO);
        BasePageVO<SearchPoiVO> result = new BasePageVO<>();
        BeanUtils.copyProperties(basePageReqDTO, result);
        return Result.success(result);
    }

    @Override
    public Result<RegionCityVO> getCityByLocation(LocationReqDTO locationReqDTO) {
        RegionCityDTO cityByLocation = mapService.getCityByLocation(locationReqDTO);
        RegionCityVO result = new RegionCityVO();
        BeanUtils.copyProperties(cityByLocation, result);
        return Result.success(result);
    }

}
