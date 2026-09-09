package domain.dto;

import lombok.Data;
import lombok.Getter;

/**
 * 要登录的用户信息 DTO，与 {@link TokenDTO} 一并上传进行鉴权
 */
@Data
public class LoginUserDTO {

    /**
     * 用户 token（唯一标识）
     */
    private String userToken;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户来源
     */
    private String userFrom;

    /**
     * 用户名
     */
    private String userName;

    // 因为是登录用户信息，所以额外记录登录时间和过期时间，方便后续做登录状态的判断

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;
}
