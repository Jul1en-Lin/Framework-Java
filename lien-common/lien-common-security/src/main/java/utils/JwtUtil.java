package utils;

import domain.constants.SecurityConstants;
import domain.constants.TokenConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * JWT 工具类
 */
public class JwtUtil {

    /**
     * JWT 秘钥
     * <p>
     * jjwt 0.12+ 强制校验 HMAC 密钥长度（HS512 要求 >= 512 bit），
     * 原秘钥只有 30 字节，因此补零至 64 字节后交由 jjwt 构建 HMAC-SHA512 SecretKey。
     */
    private static final SecretKey secretKey = Keys.hmacShaKeyFor(
            Arrays.copyOf(TokenConstants.SECRET_KEY.getBytes(StandardCharsets.UTF_8), 64));

    /**
     * 从原始数据声明生成令牌
     * @param claims 数据声明
     * @return 一个紧凑的 url 安全 JWT Token 令牌字符串。
     */
    public static String createToken(Map<String, Object> claims) {
        // 生成 JWT token
        return Jwts.builder().setClaims(claims).signWith(secretKey, Jwts.SIG.HS512).compact();
    }


    /**
     * 根据令牌获取数据声明
     * @param token 令牌
     * @return 数据声明（最终是一个JSON映射，任何值都可以添加到其中）
     */
    public static Claims parseToken(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
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
