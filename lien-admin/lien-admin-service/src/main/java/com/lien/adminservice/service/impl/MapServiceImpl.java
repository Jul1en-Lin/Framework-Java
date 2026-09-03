package com.lien.adminservice.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lien.adminservice.domain.dto.SysRegionDTO;
import com.lien.adminservice.domain.entity.SysRegion;
import com.lien.adminservice.mapper.RegionMapper;
import com.lien.adminservice.service.IMapService;
import com.lien.api.constants.MapConstants;
import com.lien.common.cache.service.CacheService;
import com.lien.common.core.utils.BeanUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.RedisService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class MapServiceImpl implements IMapService {

    @Autowired
    private CacheService cacheService;
    @Autowired
    private RegionMapper regionMapper;

    /**
     * 请求前提前构造
     * 提前缓存数据
     */
    @PostConstruct
    private void init() {
        List<SysRegion> regionList = regionMapper.selectAllRegion();
        // 提前缓存城市列表与缓存城市归类列表数据
        loadCityInfo(regionList);
    }

    /**
     * 缓存城市列表数据
     * @param regionList
     */
    private void loadCityInfo(List<SysRegion> regionList) {
        List<SysRegionDTO> result = new ArrayList<>();
        for (SysRegion region : regionList) {
            if (MapConstants.CITY_LEVEL.equals(region.getLevel())) {
                SysRegionDTO regionDTO = new SysRegionDTO();
                BeanUtil.copyProperties(region, regionDTO);
                result.add(regionDTO);
            }
        }
        cacheService.setAllCache(MapConstants.CACHE_MAP_CITY_KEY, result,120L, TimeUnit.MINUTES);
    }

    /**
     * 城市列表查询
     * @return 城市列表信息
     */
    @Override
    public List<SysRegionDTO> getCityList() {
        return cacheService.getCache(MapConstants.CACHE_MAP_CITY_KEY, new TypeReference<List<SysRegionDTO>>() {});
    }

    @Override
    public Map<String, List<SysRegionDTO>> getCityListPy() {
        return Map.of();
    }

    @Override
    public List<SysRegionDTO> getRegionChildrenList(int parentId) {
        return List.of();
    }

    @Override
    public List<SysRegionDTO> getHotCityList() {
        return List.of();
    }
}
