package com.lien.adminservice.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lien.adminservice.dict.domain.dto.DictTypeWriteReqDTO;
import com.lien.adminservice.dict.domain.entity.SysDictionaryType;
import com.lien.adminservice.dict.mapper.SysDictionaryDataMapper;
import com.lien.adminservice.dict.mapper.SysDictionaryTypeMapper;
import com.lien.api.dict.domain.dto.DictTypeListReqDTO;
import com.lien.api.dict.domain.vo.DictTypeVO;
import com.lien.common.core.utils.BeanUtil;
import domain.exception.ServiceException;
import domain.vo.BasePageVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    public BasePageVO<DictTypeVO> listType(DictTypeListReqDTO dictTypeListReqDTO) {
        BasePageVO<DictTypeVO> result = new BasePageVO<>();
        // 构造查询字典类型条件
        LambdaQueryWrapper<SysDictionaryType> queryWrapper = new LambdaQueryWrapper<>();
        // 模糊右查询（xxx%）
        if (StringUtils.isNotBlank(dictTypeListReqDTO.getValue())) {
            queryWrapper.likeRight(SysDictionaryType::getValue, dictTypeListReqDTO.getValue());
        }
        if (StringUtils.isNotBlank(dictTypeListReqDTO.getTypeKey())) {
            queryWrapper.eq(SysDictionaryType::getTypeKey, dictTypeListReqDTO.getTypeKey());
        }
        // 查询数据库
        Page<SysDictionaryType> page = sysDictTypeMapper.selectPage(
            new Page<>(dictTypeListReqDTO.getPageNo().longValue(), dictTypeListReqDTO.getPageSize().longValue()),
                queryWrapper);
        // 对象转换
        List<SysDictionaryType> records = page.getRecords();
        List<DictTypeVO> dictTypeVOList = records.stream().map(record -> {
            DictTypeVO dictTypeVO = new DictTypeVO();
            BeanUtil.copyProperties(record, dictTypeVO);
            return dictTypeVO;
        }).toList();
        result.setList(dictTypeVOList);
        result.setTotals(Integer.parseInt(String.valueOf(page.getTotal())));
        result.setTotalPages(Integer.parseInt(String.valueOf(page.getPages())));
        return result;
    }
}
