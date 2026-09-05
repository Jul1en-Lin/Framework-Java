package com.lien.adminservice.dict.service;

import com.lien.adminservice.dict.domain.dto.DictTypeWriteReqDTO;
import com.lien.api.dict.domain.dto.DictTypeListReqDTO;
import com.lien.api.dict.domain.vo.DictTypeVO;
import domain.vo.BasePageVO;

/**
 * 字典服务接口
 */
public interface ISysDictionaryService {

    /**
     * 新增字典类型
     * @param dictTypeWriteReqDTO 新增字典类型DTO
     * @return Long
     */
    Long addType(DictTypeWriteReqDTO dictTypeWriteReqDTO);

    /**
     * 字典类型列表
     * @param dictTypeListReqDTO 字典类型列表DTO
     * @return BasePageVO
     */
    BasePageVO<DictTypeVO> listType(DictTypeListReqDTO dictTypeListReqDTO);
}
