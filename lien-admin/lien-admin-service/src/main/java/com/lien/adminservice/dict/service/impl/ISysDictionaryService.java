package com.lien.adminservice.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lien.adminservice.dict.domain.dto.DictTypeWriteReqDTO;
import com.lien.adminservice.dict.domain.entity.SysDictionaryType;
import com.lien.adminservice.dict.mapper.SysDictionaryDataMapper;
import com.lien.adminservice.dict.mapper.SysDictionaryTypeMapper;
import domain.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ISysDictionaryService implements com.lien.adminservice.dict.service.ISysDictionaryService {

    @Autowired
    private SysDictionaryTypeMapper sysDictTypeMapper;
    @Override
    public Long addType(DictTypeWriteReqDTO dictTypeWriteReqDTO) {
        // 查询数据
        LambdaQueryWrapper<SysDictionaryType> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(SysDictionaryType::getId)
                .eq(SysDictionaryType::getValue, dictTypeWriteReqDTO.getValue())
                .or()
                .eq(SysDictionaryType::getTypeKey, dictTypeWriteReqDTO.getTypeKey());
        SysDictionaryType sysDictionaryType = sysDictTypeMapper.selectOne(queryWrapper);
        if (sysDictionaryType != null) {
            log.warn("字典类型键或者值已存在: {}", dictTypeWriteReqDTO);
            return null;
        }

        // 插入值
        sysDictionaryType = new SysDictionaryType();
        sysDictionaryType.setValue(dictTypeWriteReqDTO.getValue());
        sysDictionaryType.setTypeKey(dictTypeWriteReqDTO.getTypeKey());
        if (StringUtils.isNotBlank(dictTypeWriteReqDTO.getRemark())) {
            sysDictionaryType.setRemark(dictTypeWriteReqDTO.getRemark());
        }
        sysDictTypeMapper.insert(sysDictionaryType);
        return sysDictionaryType.getId();
    }
}
