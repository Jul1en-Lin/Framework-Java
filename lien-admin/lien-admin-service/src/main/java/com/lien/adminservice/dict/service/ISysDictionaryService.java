package com.lien.adminservice.dict.service;

import com.lien.adminservice.dict.domain.dto.DictTypeWriteReqDTO;
import com.lien.api.dict.domain.dto.*;
import com.lien.api.dict.domain.vo.DictDataVO;
import com.lien.api.dict.domain.vo.DictTypeVO;
import domain.vo.BasePageVO;

import java.util.List;
import java.util.Map;

/**
 * 字典服务接口
 */
public interface ISysDictionaryService {

    /**
     * 新增字典类型
     * @param dictTypeWriteReqDTO 新增字典类型 DTO
     * @return 字典类型数据 Id
     */
    Long addType(DictTypeWriteReqDTO dictTypeWriteReqDTO);

    /**
     * 查看字典类型列表
     * @param dictTypeListReqDTO 字典类型列表 DTO
     * @return 支持分页展示的字典类型列表
     */
    BasePageVO<DictTypeVO> listType(DictTypeListReqDTO dictTypeListReqDTO);

    /**
     * 编辑字典类型
     * @param dictTypeWriteReqDTO 编辑字典类型 DTO
     * @return 字典类型返回数据 Id
     */
    Long editType(DictTypeWriteReqDTO dictTypeWriteReqDTO);

    /**
     * 新增字典数据
     * @param dictionaryDataAddReqDTO 新增字典数据 DTO
     * @return 字典数据返回数据 Id
     */
    Long addData(DictDataAddReqDTO dictionaryDataAddReqDTO);

    /**
     * 查看字典数据列表（支持字典数据的值 value 查找）
     * @param dictionaryDataListReqDTO 字典数据列表查询 DTO
     * @return 支持分页展示的字典数据列表
     */
    BasePageVO<DictDataVO> listData(DictDataListReqDTO dictionaryDataListReqDTO);

    /**
     * 编辑字典数据
     * @param dictionaryDataEditReqDTO 编辑字典数据 DTO
     * @return 字典数据返回数据 Id
     */
    Long editData(DictDataEditReqDTO dictionaryDataEditReqDTO);


    /**
     * 获取单个字典类型下的所有字典数据
     * @param typeKey 字典类型键
     * @return 字典数据列表
     */
    List<DictDataDTO> selectDictDataByType(String typeKey);


    /**
     * 获取多个字典类型下的所有字典数据
     * @param typeKeys 字典类型键列表
     * @return 哈希表 字典类型键->字典数据列表
     */
    Map<String, List<DictDataDTO>> selectDictDataByTypes(List<String> typeKeys);

    /**
     * 根据字典数据业务主键（dataKey）获取字典数据对象
     * @param dataKey 字典数据业务主键
     * @return 字典数据 DTO
     */
    DictDataDTO getDicDataByKey(String dataKey);

    /**
     * 根据多个字典数据业务主键（dataKey）获取多个字典数据对象
     * @param dataKeys 多个字典数据业务主键
     * @return 字典数据 DTO 列表
     */
    List<DictDataDTO> getDicDataByKeys(List<String> dataKeys);
}
