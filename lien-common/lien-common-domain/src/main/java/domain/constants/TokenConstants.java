package domain.constants;

/**
 * 令牌常量信息
 */
public class TokenConstants {

    /**
     * 令牌秘钥
     */
    public final static String SECRET_KEY = "lienabcdefghijklmnopqrstuvwxyz";

    /**
     * 令牌前缀
     */
    public final static String PREFIX = "Bearer ";

    /**
     * 已授权的令牌 Token 在 Redis 缓存的前缀 key
     */
    public final static String LOGIN_TOKEN_KEY = "logintoken:";
}
