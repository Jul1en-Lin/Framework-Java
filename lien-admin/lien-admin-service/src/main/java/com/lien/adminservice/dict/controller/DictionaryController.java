package com.lien.adminservice.dict.controller;

import com.lien.adminservice.dict.domain.dto.DictTypeWriteReqDTO;
import com.lien.adminservice.dict.service.ISysDictionaryService;
import com.lien.api.dict.feign.DictionaryFeignClient;
import domain.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
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
}
