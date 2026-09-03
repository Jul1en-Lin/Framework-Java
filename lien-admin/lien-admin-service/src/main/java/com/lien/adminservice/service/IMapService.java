package com.lien.adminservice.service;

import com.lien.adminservice.domain.dto.SysRegionDTO;

import java.util.List;
import java.util.Map;

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
    List<SysRegionDTO> getRegionChildrenList(int parentId);

    /**
     * 获取热门城市列表
     * @return 城市列表
     */
    List<SysRegionDTO> getHotCityList();
}
