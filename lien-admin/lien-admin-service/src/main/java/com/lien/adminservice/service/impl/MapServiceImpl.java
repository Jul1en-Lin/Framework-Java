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
import java.util.TreeMap;
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
        // 提前缓存城市列表与缓存城市首字母归类列表数据
        loadCityInfo(regionList);
        loadCityListPy(regionList);
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
     * 缓存城市首字母 A-Z 归类列表数据
     * @param regionList 提前加载的全局城市列表数据
     */
    private void loadCityListPy(List<SysRegion> regionList) {
        Map<String, List<SysRegionDTO>> dtoMap = new TreeMap<>();
        // 构建城市拼音首字母列表信息
        for (SysRegion region : regionList) {
            if (MapConstants.CITY_LEVEL.equals(region.getLevel())) {
                SysRegionDTO regionDTO = new SysRegionDTO();
                BeanUtil.copyProperties(region, regionDTO);
                // 截取首位拼音字母
                String aCase = regionDTO.getPinyin().substring(0, 1).toUpperCase();
                // 若 key 包含该字母
                if (dtoMap.containsKey(aCase)) {
                    List<SysRegionDTO> dtoList = dtoMap.get(aCase);
                    dtoList.add(regionDTO);
                } else {
                    dtoMap.put(aCase, new ArrayList<SysRegionDTO>());
                    dtoMap.get(aCase).add(regionDTO);
                }
            }
        }
        cacheService.setAllCache(MapConstants.CACHE_MAP_CITY_PINYIN_KEY, dtoMap,120L, TimeUnit.MINUTES);
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
        return cacheService.getCache(MapConstants.CACHE_MAP_CITY_PINYIN_KEY, new TypeReference<Map<String, List<SysRegionDTO>>>() {});
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
