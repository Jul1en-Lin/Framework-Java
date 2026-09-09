package com.lien.adminservice.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lien.adminservice.dict.domain.entity.SysArgument;
import com.lien.adminservice.dict.mapper.SysArgumentMapper;
import com.lien.adminservice.dict.service.ISysArgumentService;
import com.lien.adminservice.map.domain.dto.SysRegionDTO;
import com.lien.api.dict.domain.dto.ArgumentAddReqDTO;
import com.lien.api.dict.domain.dto.ArgumentDTO;
import com.lien.api.dict.domain.dto.ArgumentEditReqDTO;
import com.lien.api.dict.domain.dto.ArgumentListReqDTO;
import com.lien.api.dict.domain.vo.ArgumentVO;
import com.lien.common.core.utils.BeanUtil;
import domain.exception.ServiceException;
import domain.vo.BasePageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数服务实现类
 */
@Service
public class SysArgumentServiceImpl implements ISysArgumentService {

    @Autowired
    private SysArgumentMapper sysArgumentMapper;

    @Override
    public Long add(ArgumentAddReqDTO argumentAddReqDTO) {
        // 查询参数是否存在
        LambdaQueryWrapper<SysArgument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysArgument::getConfigKey, argumentAddReqDTO.getConfigKey());
        if (sysArgumentMapper.selectOne(queryWrapper) != null) {
            throw new ServiceException("已存在参数主键");
        }

        // 插入数据
        SysArgument data = new SysArgument();
        data.setConfigKey(argumentAddReqDTO.getConfigKey());
        data.setName(argumentAddReqDTO.getName());
        data.setValue(argumentAddReqDTO.getValue());
        if (argumentAddReqDTO.getRemark() != null) {
            data.setRemark(argumentAddReqDTO.getRemark());
        }
        sysArgumentMapper.insert(data);
        return data.getId();
    }

    @Override
    public BasePageVO<ArgumentVO> list(ArgumentListReqDTO argumentListReqDTO) {
        // 构造查询条件
        LambdaQueryWrapper<SysArgument> queryWrapper = new LambdaQueryWrapper<>();
        if (argumentListReqDTO.getConfigKey() != null) {
            queryWrapper.eq(SysArgument::getConfigKey, argumentListReqDTO.getConfigKey());
        }
        if (argumentListReqDTO.getName() != null) {
            queryWrapper.like(SysArgument::getName, argumentListReqDTO.getName());
        }

        // 查询数据库
        long pageNo = (long) argumentListReqDTO.getPageNo();
        long pageSize = (long) argumentListReqDTO.getPageSize();
        Page<SysArgument> sysArguments = sysArgumentMapper.selectPage(new Page<>(pageNo, pageSize), queryWrapper);

        List<ArgumentVO> voList = new ArrayList<>();
        for (SysArgument sysArgument : sysArguments.getRecords()) {
            ArgumentVO argumentVO = new ArgumentVO();
            BeanUtil.copyProperties(sysArgument, argumentVO);
            argumentVO.setConfigKey(sysArgument.getConfigKey());
            voList.add(argumentVO);
        }

        BasePageVO<ArgumentVO> result = new BasePageVO<>();
        result.setTotals((int) sysArguments.getTotal());
        result.setTotalPages((int) sysArguments.getPages());
        result.setList(voList);
        return result;
    }

    @Override
    public Long edit(ArgumentEditReqDTO argumentEditReqDTO) {
        // 查询修改的参数是否已经存在，不存在则不允许修改
        SysArgument sysArgument = sysArgumentMapper.selectOne(new LambdaQueryWrapper<SysArgument>()
                .eq(SysArgument::getConfigKey, argumentEditReqDTO.getConfigKey()));
        if (sysArgument == null) {
            throw new ServiceException("不存在要修改的参数主键");
        }

        // 检查要修改的数据是否与已存在的参数存在冲突，存在则不允许修改
        if (sysArgumentMapper.selectOne(new LambdaQueryWrapper<SysArgument>()
                .eq(SysArgument::getName, argumentEditReqDTO.getName())
                .ne(SysArgument::getConfigKey, argumentEditReqDTO.getConfigKey())) != null) {
            throw new ServiceException("已存在参数名称，不允许修改");
        }

        // 修改
        sysArgument.setName(argumentEditReqDTO.getName());
        sysArgument.setValue(argumentEditReqDTO.getValue());
        if (argumentEditReqDTO.getRemark() != null) {
            sysArgument.setRemark(argumentEditReqDTO.getRemark());
        }
        sysArgumentMapper.updateById(sysArgument);
        return sysArgument.getId();
    }

    @Override
    public ArgumentDTO getByConfigKey(String configKey) {
        LambdaQueryWrapper<SysArgument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysArgument::getConfigKey,configKey);

        SysArgument data = sysArgumentMapper.selectOne(queryWrapper);
        if (data != null) {
            ArgumentDTO result = new ArgumentDTO();
            BeanUtil.copyProperties(result,data);
            return result;
        }
        return null;
    }

    @Override
    public List<ArgumentDTO> getByConfigKeys(List<String> configKeys) {
        if (configKeys.isEmpty()) return null;

        List<SysArgument> datas = sysArgumentMapper.selectList(new LambdaQueryWrapper<SysArgument>()
                .in(SysArgument::getConfigKey, configKeys));

        if (!datas.isEmpty()) {
            List<ArgumentDTO> result = new ArrayList<>();
            for (SysArgument data : datas) {
                ArgumentDTO dto = new ArgumentDTO();
                BeanUtil.copyProperties(dto,data);
                result.add(dto);
            }
            return result;
        }
        return null;
    }
}
