package com.lien.adminservice.dict.controller;

import com.lien.adminservice.dict.domain.dto.DictTypeWriteReqDTO;
import com.lien.adminservice.dict.service.ISysDictionaryService;
import com.lien.api.dict.domain.dto.DictTypeListReqDTO;
import com.lien.api.dict.domain.vo.DictTypeVO;
import com.lien.api.dict.feign.DictionaryFeignClient;
import domain.Result;
import domain.vo.BasePageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DictionaryController implements DictionaryFeignClient {

    @Autowired
    private ISysDictionaryService sysDictionaryService;
    /**
     * 新增字典类型
     * @param dictTypeWriteReqDTO 新增字典类型DTO
     * @return Long
     */
    @PostMapping("/dictionary_type/add")
    public Result<Long> addType(@RequestBody @Validated DictTypeWriteReqDTO dictTypeWriteReqDTO) {
        return Result.success(sysDictionaryService.addType(dictTypeWriteReqDTO));
    }

    /**
     * 字典类型列表
     * @param dictTypeListReqDTO 字典类型列表DTO
     * @return BasePageVO
     */
    @GetMapping("/dictionary_type/list")
    public Result<BasePageVO<DictTypeVO>> listType(@Validated DictTypeListReqDTO dictTypeListReqDTO) {
        return Result.success(sysDictionaryService.listType(dictTypeListReqDTO));
    }
}
