package service;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import utils.JsonUtil;

import java.util.concurrent.TimeUnit;

public class RedisService {

    private RedisTemplate<String, Object> redisTemplate;

    // *********************** 操作String类型 **************************
    // ******* 存储缓存对象 *******

    public <T> void setCacheObject(String key, T value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public <T> void setCacheObject(String key, T value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }


    public <T> Boolean setCacheObjectIfAbsent(String key, T value) {
        return redisTemplate.opsForValue().setIfAbsent(key, value);
    }


    public <T> Boolean setCacheObjectIfAbsent(final String key, final T value, final long timeout, final TimeUnit timeUnit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, timeUnit);
    }

    // ******* 获取缓存对象 *******

    /**
     * 获得缓存的数据（将缓存的数据反序列化为指定类型返回）
     * @param clazz 反序列化的类型
     */
    public <T> T getCacheObject(final String key, Class<T> clazz) {
        ValueOperations<String, Object> ValueOperations = redisTemplate.opsForValue();
        Object o = ValueOperations.get(key);
        if (o == null) return null;
        String str = JsonUtil.Obj2string(o);
        return JsonUtil.string2Obj(str, clazz);
    }

    /**
     * 获得缓存的数据 （将缓存的数据反序列化为指定类型返回，支持复杂的类型嵌套）
     * @param typeRef 嵌套类型
     */
    public <T> T getCacheObject(final String key, TypeReference<T> typeRef) {
        ValueOperations<String, Object> ValueOperations = redisTemplate.opsForValue();
        Object o = ValueOperations.get(key);
        if (o == null) return null;
        String str = JsonUtil.Obj2string(o);
        return JsonUtil.string2Obj(str, typeRef);
    }
    // *********************** 操作List类型 **************************

}
