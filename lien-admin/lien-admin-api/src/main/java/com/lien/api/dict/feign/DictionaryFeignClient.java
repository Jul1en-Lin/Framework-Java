package com.lien.api.dict.feign;

import com.lien.api.dict.domain.dto.DictDataDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;


@FeignClient(contextId = "dictionaryFeignClient", value = "lien-admin")
public interface DictionaryFeignClient {

    /**
     * 获取单个字典类型下的所有字典数据
     * @param typeKey 字典类型键
     * @return 字典数据列表
     */
    @GetMapping("/dictionary_data/type")
    List<DictDataDTO> selectDictDataByType(@RequestParam String typeKey);


    /**
     * 获取多个字典类型下的所有字典数据
     * @param typeKeys 字典类型键列表
     * @return 哈希表 字典类型键->字典数据列表
     */
    @PostMapping("/dictionary_data/types")
    Map<String, List<DictDataDTO>> selectDictDataByTypes(@RequestBody List<String> typeKeys);

    /**
     * 根据字典数据业务主键（dataKey）获取字典数据对象
     * @param dataKey 字典数据业务主键
     * @return 字典数据 DTO
     */
    @GetMapping("/dictionary_data/key")
    DictDataDTO getDicDataByKey(@RequestParam String dataKey);

    /**
     * 根据多个字典数据业务主键（dataKey）获取多个字典数据对象
     * @param dataKeys 多个字典数据业务主键
     * @return 字典数据 DTO 列表
     */
    @PostMapping("/dictionary_data/keys")
    List<DictDataDTO> getDicDataByKeys(@RequestBody List<String> dataKeys);


}
