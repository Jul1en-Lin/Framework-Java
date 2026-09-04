package com.lien.adminservice.service;

import com.lien.adminservice.domain.dto.SysRegionDTO;
import com.lien.adminservice.domain.entity.SysRegion;
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
}
