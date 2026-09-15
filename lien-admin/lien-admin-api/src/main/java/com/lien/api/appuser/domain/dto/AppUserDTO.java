package com.lien.api.appuser.domain.dto;


import com.lien.api.appuser.domain.vo.AppUserVO;
import com.lien.common.core.utils.BeanUtil;
import lombok.Data;


/**
 * C 端用户数据 DTO
 */
@Data
public class AppUserDTO {
    
    /**
     * C 端用户ID
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String nickName;

    /**
     * 手机号
     */
    private String phoneNumber;

    /**
     * 微信ID
     */
    private String openId;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 对象转换 VO 对象
     * @return VO 对象
     */
    public AppUserVO convertToVO() {
        AppUserVO appUserVO = new AppUserVO();
        BeanUtil.copyProperties(this, appUserVO);
        appUserVO.setUserId(this.id);
        return appUserVO;
    }
}
