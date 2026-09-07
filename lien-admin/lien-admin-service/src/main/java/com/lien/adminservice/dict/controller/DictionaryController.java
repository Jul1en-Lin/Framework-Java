package com.lien.adminservice.dict.controller;

import com.lien.adminservice.dict.domain.dto.DictTypeWriteReqDTO;
import com.lien.adminservice.dict.service.ISysDictionaryService;
import com.lien.api.dict.domain.dto.*;
import com.lien.api.dict.domain.vo.DictDataVO;
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

import java.util.List;
import java.util.Map;

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
     * 查看字典类型列表
     * @param dictTypeListReqDTO 字典类型列表DTO
     * @return BasePageVO
     */
    @GetMapping("/dictionary_type/list")
    public Result<BasePageVO<DictTypeVO>> listType(@Validated DictTypeListReqDTO dictTypeListReqDTO) {
        return Result.success(sysDictionaryService.listType(dictTypeListReqDTO));
    }

    /**
     * 编辑字典类型列表
     * @param dictTypeWriteReqDTO 字典类型列表DTO
     * @return BasePageVO
     */
    @PostMapping("/dictionary_type/edit")
    public Result<Long> editType(@RequestBody @Validated DictTypeWriteReqDTO dictTypeWriteReqDTO) {
        return Result.success(sysDictionaryService.editType(dictTypeWriteReqDTO));
    }

    /**
     * 新增字典数据
     * @param dictionaryDataAddReqDTO 新增字典数据DTO
     * @return Long
     */
    @PostMapping("/dictionary_data/add")
    public Result<Long> addData(@RequestBody @Validated DictDataAddReqDTO dictionaryDataAddReqDTO) {
        return Result.success(sysDictionaryService.addData(dictionaryDataAddReqDTO));
    }

    /**
     * 查询字典数据列表
     * @param dictionaryDataListReqDTO 字典数据列表DTO
     * @return BasePageVO
     */
    @GetMapping("/dictionary_data/list")
    public Result<BasePageVO<DictDataVO>> listData(@Validated DictDataListReqDTO dictionaryDataListReqDTO) {
        return Result.success(sysDictionaryService.listData(dictionaryDataListReqDTO));
    }

    /**
     * 编辑字典数据
     * @param dictionaryDataEditReqDTO 编辑字典数据DTO
     * @return Long
     */
    @PostMapping("/dictionary_data/edit")
    public Result<Long> editData(@RequestBody @Validated DictDataEditReqDTO dictionaryDataEditReqDTO) {
        return Result.success(sysDictionaryService.editData(dictionaryDataEditReqDTO));
    }

    @Override
    public List<DictDataDTO> selectDictDataByType(String typeKey) {
        return sysDictionaryService.selectDictDataByType(typeKey);
    }

    @Override
    public Map<String, List<DictDataDTO>> selectDictDataByTypes(List<String> typeKeys) {
        return sysDictionaryService.selectDictDataByTypes(typeKeys);
    }

    @Override
    public DictDataDTO getDicDataByKey(String dataKey) {
        return sysDictionaryService.getDicDataByKey(dataKey);
    }

    @Override
    public List<DictDataDTO> getDicDataByKeys(List<String> dataKeys) {
        return sysDictionaryService.getDicDataByKeys(dataKeys);
    }
}
