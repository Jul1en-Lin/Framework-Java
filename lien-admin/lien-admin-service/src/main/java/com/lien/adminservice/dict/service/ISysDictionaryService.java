package com.lien.adminservice.dict.service;

import com.lien.adminservice.dict.domain.dto.DictTypeWriteReqDTO;
import com.lien.api.dict.domain.dto.DictDataAddReqDTO;
import com.lien.api.dict.domain.dto.DictDataEditReqDTO;
import com.lien.api.dict.domain.dto.DictDataListReqDTO;
import com.lien.api.dict.domain.dto.DictTypeListReqDTO;
import com.lien.api.dict.domain.vo.DictDataVO;
import com.lien.api.dict.domain.vo.DictTypeVO;
import domain.vo.BasePageVO;

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

}
