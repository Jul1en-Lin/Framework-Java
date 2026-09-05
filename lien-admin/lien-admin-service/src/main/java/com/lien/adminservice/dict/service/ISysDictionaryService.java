package com.lien.adminservice.dict.service;

import com.lien.adminservice.dict.domain.dto.DictTypeWriteReqDTO;

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
}
