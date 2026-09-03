package com.lien.adminservice.controller;

import com.lien.adminservice.domain.dto.SysRegionDTO;
import com.lien.adminservice.service.IMapService;
import com.lien.api.domain.vo.RegionVO;
import com.lien.api.feign.IMapFeignClient;
import com.lien.common.core.utils.BeanUtil;
import domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * 地图控制器类
 */
@RestController
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
        return null;
    }

    @Override
    public Result<List<RegionVO>> getRegionChildrenList(Long parentId) {
        return null;
    }

    @Override
    public Result<List<RegionVO>> getHotCityList() {
        return null;
    }


}
