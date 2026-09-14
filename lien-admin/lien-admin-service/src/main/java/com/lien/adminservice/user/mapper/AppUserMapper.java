package com.lien.adminservice.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lien.adminservice.user.domain.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * C 端用户表操作接口。
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
}
