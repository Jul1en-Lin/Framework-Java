package com.lien.adminservice.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lien.adminservice.user.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理端人员表操作接口。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
