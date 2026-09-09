package utils;

import domain.constants.SecurityConstants;
import domain.constants.TokenConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Map;

/**
 * JWT 工具类
 */
public class JwtUtil {

    /**
     * JWT 秘钥
     */
    private static String secretKey = TokenConstants.SECRET_KEY;

    /**
     * 从原始数据声明生成令牌
     * @param claims 数据声明
     * @return 一个紧凑的 url 安全 JWT Token 令牌字符串。
     */
    public static String createToken(Map<String, Object> claims) {
        // 生成 JWT token
        return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, secretKey).compact();
    }


    /**
     * 根据令牌获取数据声明
     * @param token 令牌
     * @return 数据声明（最终是一个JSON映射，任何值都可以添加到其中）
     */
    public static Claims parseToken(String token) {
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
    }


    // -------------- 获取用户指定 key 的值（value） --------------


    /**
     * 根据 Token 令牌获取用户标识
     * @param token 令牌
     * @return 用户标识
     */
    public static String getUserKey(String token) {
        // 先根据令牌获取数据声明（claims），再根据数据声明获取用户标识
        Claims claims = parseToken(token);
        return getValue(claims, SecurityConstants.USER_KEY);
    }

    /**
     * 根据数据声明获取用户标识（再封装）
     * @param claims 数据声明
     * @return 用户标识
     */
    public static String getUserKey(Claims claims) {
        return getValue(claims, SecurityConstants.USER_KEY);
    }

    /**
     * 根据 Token 令牌获取用户ID
     * @param token 令牌
     * @return 用户ID
     */
    public static String getUserId(String token) {
        Claims claims = parseToken(token);
        return getValue(claims, SecurityConstants.USER_ID);
    }

    /**
     * 根据数据声明获取用户ID（再封装）
     * @param claims 数据声明
     * @return 用户ID
     */
    public static String getUserId(Claims claims) {
        return getValue(claims, SecurityConstants.USER_ID);
    }

    /**
     * 根据令牌获取用户名称
     * @param token 令牌
     * @return 用户名称
     */
    public static String getUserName(String token) {
        Claims claims = parseToken(token);
        return getValue(claims, SecurityConstants.USERNAME);
    }

    /**
     * 根据数据声明获取用户名称（再封装）
     * @param claims 数据声明
     * @return 用户名称
     */
    public static String getUserName(Claims claims) {
        return getValue(claims, SecurityConstants.USERNAME);
    }


    /**
     * 根据令牌获取用户来源
     * @param token 令牌
     * @return 用户来源
     */
    public static String getUserFrom(String token) {
        Claims claims = parseToken(token);
        return getValue(claims, SecurityConstants.USER_FROM);
    }

    /**
     * 根据数据声明获取用户来源（再封装）
     * @param claims 数据声明
     * @return 用户来源
     */
    public static String getUserFrom(Claims claims) {
        return getValue(claims, SecurityConstants.USER_FROM);
    }

    // ---------------------------------------------------------



    /**
     * 根据数据声明（claims）获取指定 key 的值（value）
     * @param claims 数据声明
     * @param key 指定的 key
     * @return 指定 key 的值
     */
    private static String getValue(Claims claims, String key) {
        Object value = claims.get(key);
        if (value == null) {
            return "";
        }
        return value.toString();
    }
}
