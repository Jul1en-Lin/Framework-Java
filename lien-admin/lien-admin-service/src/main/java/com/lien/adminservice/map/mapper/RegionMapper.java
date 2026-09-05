package com.lien.adminservice.map.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lien.adminservice.map.domain.entity.SysRegion;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RegionMapper extends BaseMapper<SysRegion> {

    /**
     * 查询全部区划信息（按拼音排序）
     * @return 区划信息列表
     */
    List<SysRegion> selectAllRegion();
}
