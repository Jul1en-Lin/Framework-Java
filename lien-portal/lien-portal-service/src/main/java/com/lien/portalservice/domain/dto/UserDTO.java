package com.lien.portalservice.domain.dto;

import com.lien.common.core.utils.BeanUtil;
import com.lien.portalservice.domain.vo.UserVO;
import domain.dto.LoginUserDTO;
import lombok.Data;

/**
 * C 端用户登录信息 DTO
 */
@Data
public class UserDTO extends LoginUserDTO {

    /**
     * 用户头像
     */
    private String avatar;

    public UserVO convertToVO() {
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(this, userVO);
        userVO.setNickName(this.getUserName());
        return userVO;
    }
}
