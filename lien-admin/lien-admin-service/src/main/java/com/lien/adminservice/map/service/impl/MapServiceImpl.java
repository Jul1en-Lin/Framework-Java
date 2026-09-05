package com.lien.adminservice.map.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lien.adminservice.map.domain.dto.*;
import com.lien.adminservice.map.domain.entity.SysRegion;
import com.lien.adminservice.map.mapper.RegionMapper;
import com.lien.adminservice.map.service.IMapService;
import com.lien.adminservice.map.service.ITencentMapService;
import com.lien.api.map.constants.MapConstants;
import com.lien.api.map.domain.dto.LocationReqDTO;
import com.lien.api.map.domain.dto.SearchReqDTO;
import com.lien.common.cache.service.CacheService;
import com.lien.common.core.domain.dto.BasePageDTO;
import com.lien.common.core.utils.BeanUtil;
import com.lien.common.core.utils.PageUtil;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

@Service
public class MapServiceImpl implements IMapService {

    @Autowired
    private CacheService cacheService;
    @Autowired
    private RegionMapper regionMapper;
    @Autowired
    private ITencentMapService tencentMapService;

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
     * 提前缓存城市列表数据
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
     * 提前缓存城市首字母 A-Z 归类列表数据
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


    @Override
    public List<SysRegionDTO> getCityList() {
        return cacheService.getCache(MapConstants.CACHE_MAP_CITY_KEY, new TypeReference<List<SysRegionDTO>>() {});
    }

    @Override
    public Map<String, List<SysRegionDTO>> getCityListPy() {
        return cacheService.getCache(MapConstants.CACHE_MAP_CITY_PINYIN_KEY, new TypeReference<Map<String, List<SysRegionDTO>>>() {});
    }


    @Override
    public List<SysRegionDTO> getRegionChildrenList(@NotNull Long parentId) {
        // 尝试获取缓存
        String cacheKey = MapConstants.CACHE_MAP_CITY_CHILDREN_KEY + parentId;
        List<SysRegionDTO> cache = cacheService.getCache(cacheKey, new TypeReference<>() {
        });
        if (cache != null && !cache.isEmpty()) {
            return cache;
        }

        List<SysRegion> cityList = regionMapper.selectAllRegion();
        List<SysRegionDTO> childrenList = new ArrayList<>();
        // 判断符合父级 id 进入 childrenList
        for (SysRegion region : cityList) {
            SysRegionDTO regionDTO = new SysRegionDTO();
            BeanUtil.copyProperties(region, regionDTO);
            if (Objects.equals(regionDTO.getParentId(), parentId)) {
                childrenList.add(regionDTO);
            }
        }
        cacheService.setAllCache(cacheKey, childrenList, 120L, TimeUnit.MINUTES);
        return childrenList;
    }

    @Override
    public List<SysRegionDTO> getHotCityList() {
        // 先查缓存
        List<SysRegionDTO> hotCityList = cacheService.getCache(MapConstants.CACHE_MAP_HOT_CITY, new TypeReference<List<SysRegionDTO>>() {
        });
        if (hotCityList != null) {
            return hotCityList;
        }

        // 设置6个热门城市
        String ids = "1,2,3,4,5,6";
        List<Long> idList = new ArrayList<>();
        for (String num : ids.split(",")) {
            idList.add(Long.parseLong(num));
        }
        // 查询热门城市结果
        List<SysRegionDTO> result = new ArrayList<>();
        for (SysRegion sysRegion : regionMapper.selectBatchIds(idList)) {
            SysRegionDTO sysRegionDTO = new SysRegionDTO();
            BeanUtil.copyProperties(sysRegion, sysRegionDTO);
            result.add(sysRegionDTO);
        }
        // 4 设置缓存
        cacheService.setAllCache(MapConstants.CACHE_MAP_HOT_CITY, result, 120L, TimeUnit.MINUTES);
        return result;
    }

    @Override
    public BasePageDTO<SearchPoiDTO> searchPlaceByRegion(SearchReqDTO placeSearchReqDTO) {
        // 构建入参
        SearchParamDTO SearchParamDTO = constructReqParam(placeSearchReqDTO);
        PoiListDTO poiListDTO = tencentMapService.searchPlaceByRegion(SearchParamDTO);

        // 结果对象赋值转换成 BasePageDTO 的 List<SearchPoiDTO> list;
        List<PoiListDTO.PoiDTO> dataList = poiListDTO.getData();
        List<SearchPoiDTO> PoiVOList = new ArrayList<>();
        for (PoiListDTO.PoiDTO poiDTO : dataList) {
            SearchPoiDTO searchPoiVO = new SearchPoiDTO();
            BeanUtil.copyProperties(poiDTO, searchPoiVO);
            // 手动赋值 有位置封装嵌套
            searchPoiVO.setLongitude(poiDTO.getLocation().getLng());
            searchPoiVO.setLatitude(poiDTO.getLocation().getLat());
            PoiVOList.add(searchPoiVO);
        }
        // 剩余元素传参
        BasePageDTO<SearchPoiDTO> result = new BasePageDTO<>();
        result.setTotals(poiListDTO.getCount());
        result.setTotalPages(PageUtil.getTotalPages(result.getTotals(), placeSearchReqDTO.getPageSize()));
        result.setList(PoiVOList);
        return result;
    }

    @Override
    public RegionCityDTO getCityByLocation(LocationReqDTO locationReqDTO) {
        // 构建入参
        LocationDTO locationDTO = constructReqParam(locationReqDTO);
        // 获取区域信息
        GeoResultDTO geoResultDTO = tencentMapService.getDistrictByLonLat(locationDTO);

        RegionCityDTO result = new RegionCityDTO();
        if (geoResultDTO == null || geoResultDTO.getResult() == null
                || geoResultDTO.getResult().getAd_info() == null) {
            return result;
        }

        // 查城市列表缓存（CITY_LEVEL = 2）
        // TODO: 缓存时由于都是固定为等级为 2 的城市，应做全量城市列表的查询
        List<SysRegionDTO> cityCache = cacheService.getCache(MapConstants.CACHE_MAP_CITY_KEY,
                new TypeReference<List<SysRegionDTO>>() {});
        String cityName = geoResultDTO.getResult().getAd_info().getCity();
        for (SysRegionDTO sysRegionDTO: cityCache) {
            if (sysRegionDTO.getFullName().equals(cityName)) {
                BeanUtils.copyProperties(sysRegionDTO, result);
                return result;
            }
        }
        return result;
    }

    /**
     * 构造查询 SearchReqDTO 对象入参
     */
    private SearchParamDTO constructReqParam(SearchReqDTO searchReqDTO) {
        SearchParamDTO suggestSearchDTO = new SearchParamDTO();
        BeanUtil.copyProperties(searchReqDTO, suggestSearchDTO);
        suggestSearchDTO.setPageIndex(searchReqDTO.getPageNo());
        suggestSearchDTO.setRegionId(String.valueOf(searchReqDTO.getRegionId()));
        return suggestSearchDTO;
    }


    /**
     * 构造查询 LocationReqDTO 对象入参
     */
    private LocationDTO constructReqParam(LocationReqDTO locationReqDTO) {
        LocationDTO suggestSearchDTO = new LocationDTO();
        BeanUtil.copyProperties(locationReqDTO, suggestSearchDTO);
        return suggestSearchDTO;
    }
}
