package com.lien.api.map.feign;

import com.lien.api.map.domain.dto.LocationReqDTO;
import com.lien.api.map.domain.dto.SearchReqDTO;
import com.lien.api.map.domain.vo.RegionCityVO;
import com.lien.api.map.domain.vo.RegionVO;
import com.lien.api.map.domain.vo.SearchPoiVO;
import domain.Result;
import domain.vo.BasePageVO;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 地图服务相关远程调用
 */
@FeignClient(contextId = "mapFeignClient", value = "lien-admin")
public interface IMapFeignClient {

    /**
     * 城市列表查询
     * @return 城市列表信息
     */
    @GetMapping("/map/city_list")
    Result<List<RegionVO>> getCityList();

    /**
     * 城市拼音归类查询
     * @return 城市字母与城市列表的哈希
     */
    @GetMapping("/map/city_pinyin_list")
    Result<Map<String, List<RegionVO>>> getCityListPy();

    /**
     * 根据父级区域ID获取子集区域列表
     * @param parentId 父级区域ID
     * @return 子集区域列表
     */
    @GetMapping("/map/region_children_list")
    Result<List<RegionVO>> getRegionChildrenList(@RequestParam @NotNull Long parentId);

    /**
     * 获取热门城市列表
     * @return 城市列表
     */
    @GetMapping("/map/city_hot_list")
    Result<List<RegionVO>> getHotCityList();


    /**
     * 根据关键词搜索地点
     * @param searchReqDTO 搜索条件
     * @return 搜索结果
     */
    @PostMapping("/map/search")
    Result<BasePageVO<SearchPoiVO>> searchPlaceByRegion(@RequestBody SearchReqDTO searchReqDTO);

    /**
     * 根据经纬度来定位城市
     * @param locationReqDTO 经纬度信息
     * @return 城市信息
     */
    @PostMapping("/map/locate_city_by_location")
    Result<RegionCityVO> getCityByLocation(@RequestBody LocationReqDTO locationReqDTO);
}
