package domain.constants;

/**
 * 缓存常量信息
 */
public class CacheConstants {

    /**
     * 缓存分割符
     */
    public final static String CACHE_SPLIT_COLON = ":";

    /**
     * 缓存有效期，默认 720（分钟）
     */
    public final static long EXPIRATION = 720;

    /**
     * 缓存刷新时间，默认 120（分钟）
     */
    public final static long REFRESH_TIME = 120;
}
