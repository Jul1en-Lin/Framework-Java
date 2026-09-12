package domain.vo;

import lombok.Data;

/**
 * 用户登录信息 VO 基类
 */
@Data
public class LoginUserVO {

    /**
     * 用户标识
     */
    private String userToken;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;
}
