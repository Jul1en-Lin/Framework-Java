package utils;

import com.lien.common.core.utils.ServletUtil;
import domain.constants.SecurityConstants;
import domain.constants.TokenConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;


/**
 * 安全工具类
 */
public class SecurityUtil {

    /**
     * 获取请求中的 token 令牌
     * @param request http 请求
     * @return token 令牌
     */
    public static String getToken(HttpServletRequest request) {
        String token = request.getHeader(SecurityConstants.AUTHENTICATION);
        return replaceTokenIfExistPrefix(token);
    }

    /**
     * 获取请求中的 token 令牌
     * @return token 令牌
     */
    public static String getToken() {
        HttpServletRequest request = ServletUtil.getRequest();
        return getToken(request);
    }


    /**
     * 裁剪令牌 Token 的前缀
     * @param token 前端可能传入了有前缀的令牌
     * @return token 令牌
     */
    private static String replaceTokenIfExistPrefix(String token) {
        // 假如前端设置了令牌的前缀，需要裁剪掉前缀
        if (StringUtils.isNotEmpty(token) && token.startsWith(TokenConstants.PREFIX)) {
            token = token.replaceFirst(TokenConstants.PREFIX, "");
        }
        return token;
    }


}
