package service;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import utils.JsonUtil;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

@AutoConfiguration
public class RedisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // *********************** 操作String类型 **************************
    // ******* 存储缓存对象 *******

    public <T> void setCacheObject(final String key, final T value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public <T> void setCacheObject(final String key, final T value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }


    public <T> Boolean setCacheObjectIfAbsent(final String key, T value) {
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
        if (clazz.isInstance(o)) {
            return clazz.cast(o);
        }
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

    /**
     * 缓存List数据
     * @param key 缓存的键值
     * @param dataList 待缓存的List数据
     * @return Redis中当前操作的这个list结构的长度
     * @param <T> 对象类型
     */
    public <T> Long setCacheList(final String key, List<T> dataList) {
        // RedisTemplate<String, Object> 的 ListOperations V=Object，
        // 直接传 List<T>（T≠Object）会被 varargs rightPushAll(K, V...) 当成单个元素 push
        // （因泛型不协变，List<T> 不是 List<Object>，无法命中 Collection<V> 重载）。
        // 让 List 中每个元素被单独 push。
        Collection<Object> values = (Collection) dataList;
        return redisTemplate.opsForList().rightPushAll(key, values);
    }

    /**
     * 从List结构左侧插入数据（头插、入队）
     * @param key key
     * @param value 缓存的对象
     * @param <T> 值类型
     */
    public <T> Long leftPushForList(final String key, final T value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * 从List结构右侧插入数据（尾插、插入单个数据）
     * @param key key
     * @param value 缓存的对象
     * @param <T> 值类型
     */
    public <T> Long rightPushForList(final String key, final T value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    /**
     * 删除左侧第一个数据 （头删）
     * @param key   key
     */
    public void leftPopForList(final String key) {
        redisTemplate.opsForList().leftPop(key);
    }

    /**
     * 删除右侧第一个数据 （尾删）
     * @param key   key
     */
    public void rightPopForList(final String key) {
        redisTemplate.opsForList().rightPop(key);
    }

    /**
     * 移除List第一个匹配的元素
     * 删除的方向： > 0 从左往右   <0 从右往左
     * key : redis key
     * count : 删除的方向 & 删除个数 count > 0 从左往右删除  count < 0 从右往左删除
     * count = 0 代表全部删除
     *
     * @param key key
     * @param value 值
     * @param <T> 值类型
     */
    public <T> void removeForList(final String key, T value) {
        redisTemplate.opsForList().remove(key, 1L, value);
    }

    /**
     * 移除List中匹配的所有列表元素
     *
     * @param key key
     * @param value 值
     * @param <T> 值类型
     */
    public <T> void removeAllForList(final String key, T value) {
        redisTemplate.opsForList().remove(key, 0, value);
    }

    /**
     * 移除指定范围内的所有元素
     * [start,end] 左闭右闭
     * @start 起始索引（下标）
     * @end 结束索引（下标）
     * @param key key
     */
    public void removeForList(final String key,long start,long end) {
        redisTemplate.opsForList().trim(key, start, end);
    }

    /**
     * 清空列表中的所有元素（保留 key，仅删除其中的数据）
     * @param key
     * @trim 当 start > end 时 Redis 会清空整个 list 并保留空 key，
     *       这里用 (0, -1) 不行（会保留全部），用 (-1, 0) 在长度为 1 时
     *       -1 解析为下标 0，区间 [0,0] 反而保留唯一元素。
     *       用 (1, 0) 保证 start > end 在任意长度下都清空。
     */
    public void removeForAllList(final String key) {
        redisTemplate.opsForList().trim(key, 1, 0);
    }

    /**
     * 修改指定下标数据
     * @param key       key
     * @param index     下标
     * @param newValue  修改后新值
     * @param <T>       值类型
     */
    public <T> void setElementAtIndex(final String key, long index, T newValue) {
        redisTemplate.opsForList().set(key, index, newValue);
    }

    /**
     * 获得缓存的list对象
     * @param key key 缓存的键值
     * @param clazz 对象的类
     * @return 列表
     * @param <T> 对象类型
     * @range 保留区间
     */
    // 有序性
    public <T> List<T> getCacheList(final String key, Class<T> clazz) {
        List list = redisTemplate.opsForList().range(key, 0, -1);
        return JsonUtil.string2List(JsonUtil.Obj2string(list), clazz);
        // 0 代表第一个元素  1 ：第二个元素  -1 最后一个元素  -2 倒数第二个元素  依次类推
        // start ： 起始索引（下标）
        // end : 结束索引（下标）
    }

    /**
     * 获得缓存的list对象 （支持复杂的泛型嵌套）
     *
     * @param key key信息
     * @param typeReference 类型模板
     * @return list对象
     * @param <T> 对象类型
     */
    public <T> List<T> getCacheList(final String key, TypeReference<List<T>> typeReference) {
        List list = redisTemplate.opsForList().range(key, 0, -1);
        return JsonUtil.string2Obj(JsonUtil.Obj2string(list), typeReference);
    }

    /**
     * 根据范围获取List
     *
     * @param key key
     * @param start 开始位置
     * @param end 结束位置
     * @param clazz 类信息
     * @return List列表
     * @param <T> 类型
     */
    public <T> List<T> getCacheListByRange(final String key, long start, long end, Class<T> clazz) {
        List range = redisTemplate.opsForList().range(key, start, end);
        return JsonUtil.string2List(JsonUtil.Obj2string(range), clazz);
    }

    /**
     * 根据范围获取List（支持复杂的泛型嵌套 ）
     *
     * @param key key
     * @param start 开始
     * @param end 结果
     * @param typeReference 类型模板
     * @return list列表
     * @param <T> 类型信息
     */
    public <T> List<T> getCacheListByRange(final String key, long start, long end, TypeReference<List<T>> typeReference) {
        List range = redisTemplate.opsForList().range(key, start, end);
        return JsonUtil.string2Obj(JsonUtil.Obj2string(range), typeReference);
    }

    /**
     * 获取指定列表长度
     *
     * @param key key信息
     * @return 列表长度
     */
    public long getCacheListSize(final String key) {
        return redisTemplate.opsForList().size(key);
    }

}
