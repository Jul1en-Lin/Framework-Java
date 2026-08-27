package service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import java.util.concurrent.TimeUnit;

/**
 * 二级缓存服务（L1: Caffeine 本地缓存, L2: Redis）
 */
@AutoConfiguration
public class CacheService {

    @Autowired
    private RedisService redisService;

    /**
     * 读取本地缓存
     *
     * @param key 缓存key
     * @param valueTypeRef 嵌套模板类型
     * @param caffeineCache 本地缓存服务
     * @return 缓存信息
     * @param <T> 缓存类型
     */
    public <T> T getCache(String key, TypeReference<T> valueTypeRef, Cache<String, Object> caffeineCache) {
        // 从 L1 缓存中查询
        T result = (T) caffeineCache.getIfPresent(key);
        if (result != null) {
            return result;
        }
        // 从 L2（redis）查询数据
        result = redisService.getCacheObject(key, valueTypeRef);
        if (result != null) {
            caffeineCache.put(key, result);
            return result;
        }
        // 从 db 当中数据查询  代码逻辑
        return null;
    }

    /**
     * 存储到一级缓存
     *
     * @param key 缓存key
     * @param value 缓存值
     * @param caffeineCache 本地缓存
     * @param <T> 缓存类型
     */
    public <T> void setL1Cache(String key, T value, Cache<String, Object> caffeineCache) {
        caffeineCache.put(key, value);    //todo: 本地缓存中存储的数据需设置有效时间
    }


    /**
     * 存储到二级缓存
     *
     * @param key 缓存key
     * @param value 缓存值
     * @param <T> 缓存类型
     */
    public <T> void setL2Cache(String key, T value, Long timeout, TimeUnit timeUnit) {
        redisService.setCacheObject(key, value, timeout, timeUnit);  //todo: 本地缓存中存储的数据需设置有效时间
    }


    /**
     * 存储二级缓存和一级缓存
     *
     * @param key 缓存key
     * @param value  缓存对象值
     * @param caffeineCache 本地缓存信息
     * @param timeout 超时时间
     * @param timeUnit 超时单位
     * @param <T> 对象类型
     */
    public <T> void setAllCache(String key, T value,
                             Cache<String, Object> caffeineCache, Long timeout, TimeUnit timeUnit) {
        setL2Cache(key, value, timeout, timeUnit);
        setL1Cache(key, value, caffeineCache);   //todo: 本地 L1 缓存中存储的数据也需要设置有效时间
    }
}
