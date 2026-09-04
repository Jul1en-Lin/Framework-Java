package com.lien.adminservice.controller;

import com.lien.adminservice.domain.dto.SysRegionDTO;
import com.lien.adminservice.domain.entity.SysRegion;
import com.lien.adminservice.service.IMapService;
import com.lien.api.domain.vo.RegionVO;
import com.lien.api.feign.IMapFeignClient;
import com.lien.common.core.utils.BeanUtil;
import domain.Result;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
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

    @Autowired
    private IMapService mapService;

    /**
     * 城市列表查询
     * @return 城市列表信息
     */
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


}
