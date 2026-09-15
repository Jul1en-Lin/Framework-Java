package domain.dto;


import domain.vo.TokenVO;
import lombok.Getter;
import lombok.Setter;

/**
 * 令牌数据传输对象
 */
@Getter
@Setter
public class TokenDTO {

    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 过期时间
     */
    private Long expires;

}
