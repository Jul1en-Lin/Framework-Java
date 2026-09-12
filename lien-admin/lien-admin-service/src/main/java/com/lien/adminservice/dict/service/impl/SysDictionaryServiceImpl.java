package com.lien.adminservice.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lien.adminservice.dict.domain.dto.DictTypeWriteReqDTO;
import com.lien.adminservice.dict.domain.entity.SysDictionaryData;
import com.lien.adminservice.dict.domain.entity.SysDictionaryType;
import com.lien.adminservice.dict.mapper.SysDictionaryDataMapper;
import com.lien.adminservice.dict.mapper.SysDictionaryTypeMapper;
import com.lien.api.dict.domain.dto.*;
import com.lien.api.dict.domain.vo.DictDataVO;
import com.lien.api.dict.domain.vo.DictTypeVO;
import com.lien.common.core.utils.BeanUtil;
import domain.exception.ServiceException;
import domain.vo.BasePageVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.swing.text.html.parser.Entity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SysDictionaryServiceImpl implements com.lien.adminservice.dict.service.ISysDictionaryService {

    @Autowired
    private SysDictionaryTypeMapper sysDictTypeMapper;

    @Autowired
    private SysDictionaryDataMapper sysDictionaryDataMapper;


    @Override
    public Long addType(DictTypeWriteReqDTO dictTypeWriteReqDTO) {
        // 查询数据
        LambdaQueryWrapper<SysDictionaryType> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(SysDictionaryType::getId)
                .eq(SysDictionaryType::getValue, dictTypeWriteReqDTO.getValue())
                .or()
                .eq(SysDictionaryType::getTypeKey, dictTypeWriteReqDTO.getTypeKey());
        // 为了避免查询到两条数据以上的数据后，selectOne 抛出异常，使用 selectCount 方法更合适
        if (sysDictTypeMapper.selectCount(queryWrapper) > 0) {
            throw new ServiceException("字典类型的键或者值已存在");
        }
        // 插入值
        SysDictionaryType sysDictionaryType = new SysDictionaryType();
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

    @Override
    public Long editType(DictTypeWriteReqDTO dictTypeWriteReqDTO) {
        // 查询字典类型 TypeKey
        SysDictionaryType sysDictionaryType = sysDictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictionaryType>()
                .eq(SysDictionaryType::getTypeKey, dictTypeWriteReqDTO.getTypeKey()));
        if (sysDictionaryType == null) {
            throw new ServiceException("字典类型（TypeKey）不存在");
        }
        // 排除编辑后的 value 已存在
        if (sysDictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictionaryType>()
                .eq(SysDictionaryType::getValue, dictTypeWriteReqDTO.getValue())
                .ne(SysDictionaryType::getTypeKey, dictTypeWriteReqDTO.getTypeKey())) != null) {
            throw new ServiceException("已有字典类型 TypeKey 相同的值（value）存在，不允许编辑");
        }
        // 更新数据
        sysDictionaryType.setValue(dictTypeWriteReqDTO.getValue());
        sysDictionaryType.setRemark(dictTypeWriteReqDTO.getRemark());
        sysDictTypeMapper.updateById(sysDictionaryType);
        return sysDictionaryType.getId();
    }

    @Override
    public Long addData(DictDataAddReqDTO dictionaryDataAddReqDTO) {
        // 确认一级的字典类型需存在 不存在则不允许添加字典数据
        if (sysDictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictionaryType>()
                .eq(SysDictionaryType::getTypeKey, dictionaryDataAddReqDTO.getTypeKey())) == null) {
            throw new ServiceException("上级字典类型不存在，不允许添加新数据");
        }

        // 检查要修改的字典数据键或值是否存在，若存在则不允许添加
        SysDictionaryData sysDictionaryData = sysDictionaryDataMapper.selectOne(new LambdaQueryWrapper<SysDictionaryData>()
                .eq(SysDictionaryData::getValue, dictionaryDataAddReqDTO.getValue())
                .or()
                .eq(SysDictionaryData::getDataKey, dictionaryDataAddReqDTO.getDataKey())
        );
        if (sysDictionaryData != null) {
            throw new ServiceException("字典数据的键或值已存在，不允许添加");
        }

        // 添加字典数据键值
        sysDictionaryData = new SysDictionaryData();
        sysDictionaryData.setDataKey(dictionaryDataAddReqDTO.getDataKey());
        sysDictionaryData.setTypeKey(dictionaryDataAddReqDTO.getTypeKey());
        sysDictionaryData.setValue(dictionaryDataAddReqDTO.getValue());
        // 选填判断
        if (dictionaryDataAddReqDTO.getSort() != null) {
            sysDictionaryData.setSort(dictionaryDataAddReqDTO.getSort());
        }
        if (StringUtils.isNotBlank(dictionaryDataAddReqDTO.getRemark())) {
            sysDictionaryData.setRemark(dictionaryDataAddReqDTO.getRemark());
        }
        sysDictionaryDataMapper.insert(sysDictionaryData);
        return sysDictionaryData.getId();
    }

    @Override
    public BasePageVO<DictDataVO> listData(DictDataListReqDTO dictionaryDataListReqDTO) {
        // 构造查询条件
        LambdaQueryWrapper<SysDictionaryData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDictionaryData::getTypeKey, dictionaryDataListReqDTO.getTypeKey());
        // 向右模糊匹配（支持 value）
        if (StringUtils.isNotBlank(dictionaryDataListReqDTO.getValue())) {
            queryWrapper.likeRight(SysDictionaryData::getValue, dictionaryDataListReqDTO.getValue());
        }
        // 考虑到 sort 字段的排序问题
        queryWrapper.orderByAsc(SysDictionaryData::getSort);
        queryWrapper.orderByAsc(SysDictionaryData::getId);

        // 查询数据库
        Page<SysDictionaryData> page = sysDictionaryDataMapper.selectPage(
                new Page<>(dictionaryDataListReqDTO.getPageNo().longValue(),
                    dictionaryDataListReqDTO.getPageSize().longValue()), queryWrapper);

        // 构造返回对象
        BasePageVO<DictDataVO> result = new BasePageVO<>();
        result.setTotals(((Long)page.getTotal()).intValue());
        result.setTotalPages(((Long)page.getPages()).intValue());
        List<DictDataVO> list = new ArrayList<>();
        for (SysDictionaryData sysDictionaryData : page.getRecords()) {
            DictDataVO dictionaryDataVo = new DictDataVO();
            BeanUtils.copyProperties(sysDictionaryData, dictionaryDataVo);
            list.add(dictionaryDataVo);
        }
        result.setList(list);
        return result;
    }

    @Override
    public Long editData(DictDataEditReqDTO dictionaryDataEditReqDTO) {
        // 判断字典数据是否存在，若不存在则不允许编辑
        SysDictionaryData sysDictionaryData = sysDictionaryDataMapper.selectOne(new LambdaQueryWrapper<SysDictionaryData>()
                .eq(SysDictionaryData::getDataKey, dictionaryDataEditReqDTO.getDataKey()));
        if (sysDictionaryData == null) {
            throw new ServiceException("字典数据（dataKey）不存在");
        }
        // 判断要写入的 value 是否存在，若存在则不允许编辑
        SysDictionaryData data = sysDictionaryDataMapper.selectOne(new LambdaQueryWrapper<SysDictionaryData>()
                .eq(SysDictionaryData::getValue, dictionaryDataEditReqDTO.getValue())
                .ne(SysDictionaryData::getDataKey, dictionaryDataEditReqDTO.getDataKey()));
        if (data != null) {
            throw new ServiceException("已有字典数据 dataKey 相同的值（value）存在，不允许编辑");
        }

        // 更新数据
        sysDictionaryData.setValue(dictionaryDataEditReqDTO.getValue());
        if (dictionaryDataEditReqDTO.getSort() != null) {
            sysDictionaryData.setSort(dictionaryDataEditReqDTO.getSort());
        }
        if (StringUtils.isNotBlank(dictionaryDataEditReqDTO.getRemark())) {
            sysDictionaryData.setRemark(dictionaryDataEditReqDTO.getRemark());
        }
        sysDictionaryDataMapper.updateById(sysDictionaryData);
        return sysDictionaryData.getId();
    }

    @Override
    public List<DictDataDTO> selectDictDataByType(String typeKey) {
        LambdaQueryWrapper<SysDictionaryData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDictionaryData::getTypeKey, typeKey);
        List<SysDictionaryData> dataList = sysDictionaryDataMapper.selectList(queryWrapper);
        if (dataList == null || dataList.isEmpty()) {
            return null;
        }
        // 转换对象
        List<DictDataDTO> result = new ArrayList<>();
        for (SysDictionaryData data : dataList) {
            DictDataDTO dictDataDTO = new DictDataDTO();
            BeanUtils.copyProperties(data, dictDataDTO);
            result.add(dictDataDTO);
        }
        return result;
    }

    @Override
    public Map<String, List<DictDataDTO>> selectDictDataByTypes(List<String> typeKeys) {
        // 查询字典数据
        List<SysDictionaryData> list = sysDictionaryDataMapper.selectList(new LambdaQueryWrapper<SysDictionaryData>()
                .in(SysDictionaryData::getTypeKey, typeKeys));
        if (list == null || list.isEmpty()) {
            return null;
        }
        // 构造结果对象 List
        List<DictDataDTO> result = new ArrayList<>();
        for (SysDictionaryData sysDictionaryData : list) {
            DictDataDTO dictionaryDataDTO = new DictDataDTO();
            BeanUtils.copyProperties(sysDictionaryData, dictionaryDataDTO);
            result.add(dictionaryDataDTO);
        }
        // 把结果封装成哈希映射的形式
        Map<String, List<DictDataDTO>> map = new HashMap<>();
        for (DictDataDTO dictionaryDataDTO : result) {
            List<DictDataDTO> value;
            // 先判断当前字典类型业务主键是否在哈希表中
            if (map.get(dictionaryDataDTO.getTypeKey()) == null) {
                value = new ArrayList<>();
                value.add(dictionaryDataDTO);
                map.put(dictionaryDataDTO.getTypeKey(), value);
            } else {
                // 当前字典类型业务主键已经在哈希表的情况
                value = map.get(dictionaryDataDTO.getTypeKey());
                value.add(dictionaryDataDTO);
            }
        }
        return map;
    }


    @Override
    public DictDataDTO getDicDataByKey(String dataKey) {
        LambdaQueryWrapper<SysDictionaryData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDictionaryData::getDataKey, dataKey);
        // 查询单个字典数据
        SysDictionaryData sysDictionaryData = sysDictionaryDataMapper.selectOne(queryWrapper);
        if (sysDictionaryData == null) {
            return null;
        }
        // 转换对象
        DictDataDTO dictDataDTO = new DictDataDTO();
        BeanUtils.copyProperties(sysDictionaryData, dictDataDTO);
        return dictDataDTO;
    }

    @Override
    public List<DictDataDTO> getDicDataByKeys(List<String> dataKeys) {
        // 查询多个字典数据
        List<SysDictionaryData> list = sysDictionaryDataMapper.selectList(new LambdaQueryWrapper<SysDictionaryData>()
                .in(SysDictionaryData::getDataKey, dataKeys));
        if (list == null || list.isEmpty()) {
            return null;
        }
        // 转换对象
        List<DictDataDTO> result = new ArrayList<>();
        for (SysDictionaryData sysDictionaryData : list) {
            DictDataDTO dictDataDTO = new DictDataDTO();
            BeanUtils.copyProperties(sysDictionaryData, dictDataDTO);
            result.add(dictDataDTO);
        }
        return result;
    }
}
