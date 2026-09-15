package com.lien.adminservice.user.service;

import com.lien.adminservice.user.domain.dto.AppUserListReqDTO;
import com.lien.api.appuser.domain.dto.AppUserDTO;
import com.lien.api.appuser.domain.dto.UserEditReqDTO;
import com.lien.common.core.domain.dto.BasePageDTO;

import java.util.List;

public interface IAppUserService {

    /**
     * 根据微信用户唯一标识注册 C 端用户
     * @param openId 用户唯一标识微信 ID
     * @return C 端用户 DTO
     */
    AppUserDTO registerByOpenId(String openId);

    /**
     * 根据 openId 查询用户信息
     * @param openId 用户微信ID
     * @return C 端用户DTO
     */
    AppUserDTO findByOpenId(String openId);

    /**
     * 编辑C端用户
     * @param userEditReqDTO C 端用户 DTO
     * @return 所编辑用户的 ID
     */
    Long edit(UserEditReqDTO userEditReqDTO);

    /**
     * 查询 C 端用户
     * @param appUserListReqDTO 查询 C 端用户参数 DTO
     * @return C 端用户列表分页结果
     */
    BasePageDTO<AppUserDTO> getUserList(AppUserListReqDTO appUserListReqDTO);

    /**
     * 根据用户 ID 获取用户登录信息
     * @param userId 用户ID
     * @return C 端用户信息 DTO
     */
    AppUserDTO findById(Long userId);

    /**
     * 根据多个用户 ID 获取用户登录信息列表
     * @param userIds 多个用户ID
     * @return C 端用户信息列表 VO
     */
    List<AppUserDTO> listByIds(List<Long> userIds);
}
