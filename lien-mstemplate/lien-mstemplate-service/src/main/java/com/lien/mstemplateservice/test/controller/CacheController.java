package com.lien.mstemplateservice.test.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.lien.mstemplateservice.test.entity.RedisUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lien.common.cache.service.CacheService;
import service.RedisService;
import com.lien.common.core.utils.JsonUtil;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/test/cache")
public class CacheController {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RedisService redisService;

    /**
     * 直接注入 Caffeine Cache Bean，用于在测试中观察 L1 的真实状态
     * （验证 L1 是否命中、是否被回填）
     */
    @Autowired
    private Cache<String, Object> caffeineCache;

    /**
     * 测试场景1：仅写 L1（本地缓存）
     * <p>
     * 验证点：
     * 1. setL1Cache 只写本地缓存，不写 Redis
     * 2. getCache 能从 L1 命中并返回
     * 3. L2 中不存在该 key
     */
    @GetMapping("/setL1Cache")
    public void setL1Cache() {
        String key = "cache:l1:user:1";
        RedisUser user = new RedisUser(1L, "张三", 20, LocalDateTime.now());

        // 每次测试前清空 L1 和 L2，避免历史数据干扰
        caffeineCache.invalidate(key);
        redisService.deleteObject(key);

        // 1. 仅写入 L1
        cacheService.setL1Cache(key, user);
        log.info("[setL1Cache] 写入L1完成, key={}, value={}", key, JsonUtil.Obj2string(user));

        // 2. 验证 L1 已写入、L2 未写入
        Object l1Value = caffeineCache.getIfPresent(key);
        Boolean l2Exists = redisService.hasKey(key);
        log.info("[setL1Cache] L1是否存在(应为true): {}, L2是否存在(应为false): {}",
                l1Value != null, l2Exists);

        // 3. getCache 应直接从 L1 命中返回
        RedisUser result = cacheService.getCache(key, new TypeReference<RedisUser>() {});
        log.info("[setL1Cache] getCache读取结果(应命中L1): {}", JsonUtil.Obj2string(result));

        // 4. 读取后 L2 仍应为空（L1 命中不会回写 L2）
        log.info("[setL1Cache] 读取后L2是否存在(应为false): {}", redisService.hasKey(key));

        // 清理
        caffeineCache.invalidate(key);
    }

    /**
     * 测试场景2：仅写 L2（Redis）
     * <p>
     * 验证点（这是二级缓存最核心的路径）：
     * 1. setL2Cache 只写 Redis，不写本地缓存
     * 2. 第一次 getCache：L1 未命中 → 回源 L2 命中 → 回填 L1
     * 3. 删除 L2 后再次 getCache：L1 已回填，依然能命中返回（验证 L1 真正被回填）
     */
    @GetMapping("/setL2Cache")
    public void setL2Cache() {
        String key = "cache:l2:user:2";
        RedisUser user = new RedisUser(2L, "李四", 30, LocalDateTime.now());

        // 每次测试前清空 L1 和 L2
        caffeineCache.invalidate(key);
        redisService.deleteObject(key);

        // 1. 仅写入 L2，60s 过期
        cacheService.setL2Cache(key, user, 60L, TimeUnit.SECONDS);
        log.info("[setL2Cache] 写入L2完成(60s过期), key={}, value={}", key, JsonUtil.Obj2string(user));

        // 2. 写入后验证：L1 应为空、L2 应有值
        log.info("[setL2Cache] 写入后L1是否存在(应为false): {}, L2是否存在(应为true): {}",
                caffeineCache.getIfPresent(key) != null, redisService.hasKey(key));

        // 3. 第一次 getCache：预期 L1 未命中 → 回源 L2 → 回填 L1
        RedisUser firstRead = cacheService.getCache(key, new TypeReference<RedisUser>() {});
        log.info("[setL2Cache] 第一次读取(应回源L2命中): {}", JsonUtil.Obj2string(firstRead));
        log.info("[setL2Cache] 第一次读取后L1是否被回填(应为true): {}",
                caffeineCache.getIfPresent(key) != null);

        // 4. 删除 L2 中的 key，模拟 Redis 数据过期/被清
        redisService.deleteObject(key);
        log.info("[setL2Cache] 已删除L2, L2是否存在(应为false): {}", redisService.hasKey(key));

        // 5. 第二次 getCache：L2 已删除，但 L1 已回填，应直接从 L1 命中返回
        RedisUser secondRead = cacheService.getCache(key, new TypeReference<RedisUser>() {});
        log.info("[setL2Cache] L2删除后第二次读取(应命中L1回填值): {}", JsonUtil.Obj2string(secondRead));

        // 清理
        caffeineCache.invalidate(key);
    }

    /**
     * 测试场景3：同时写 L1 + L2
     * <p>
     * 验证点：
     * 1. setAllCache 同时写入两级缓存
     * 2. L1、L2 中均存在该 key
     * 3. getCache 直接命中 L1（不访问 Redis）
     */
    @GetMapping("/setAllCache")
    public void setAllCache() {
        String key = "cache:all:user:3";
        RedisUser user = new RedisUser(3L, "王五", 40, LocalDateTime.now());

        // 每次测试前清空 L1 和 L2
        caffeineCache.invalidate(key);
        redisService.deleteObject(key);

        // 1. 同时写入 L1 + L2，L2 60s 过期
        cacheService.setAllCache(key, user, 60L, TimeUnit.SECONDS);
        log.info("[setAllCache] 同时写入L1+L2完成, key={}, value={}", key, JsonUtil.Obj2string(user));

        // 2. 验证 L1、L2 均已写入
        log.info("[setAllCache] L1是否存在(应为true): {}, L2是否存在(应为true): {}, L2剩余过期时间(应<=60且>0): {}s",
                caffeineCache.getIfPresent(key) != null,
                redisService.hasKey(key),
                redisService.getExpire(key));

        // 3. getCache 应直接从 L1 命中返回
        RedisUser result = cacheService.getCache(key, new TypeReference<RedisUser>() {});
        log.info("[setAllCache] getCache读取结果(应命中L1): {}", JsonUtil.Obj2string(result));

        // 清理
        caffeineCache.invalidate(key);
        redisService.deleteObject(key);
    }

    /**
     * 测试场景4：L1、L2 均未命中（缓存穿透路径）
     * <p>
     * 验证点：
     * 1. 两级缓存均无数据时，getCache 返回 null
     * 2. 未命中不会污染 L1（不会写入空值占位）
     */
    @GetMapping("/getCacheMiss")
    public void getCacheMiss() {
        String key = "cache:miss:not-exists";

        // 每次测试前清空 L1 和 L2
        caffeineCache.invalidate(key);
        redisService.deleteObject(key);

        // 1. L1、L2 均无数据
        log.info("[getCacheMiss] L1是否存在(应为false): {}, L2是否存在(应为false): {}",
                caffeineCache.getIfPresent(key) != null, redisService.hasKey(key));

        // 2. getCache 应返回 null
        RedisUser result = cacheService.getCache(key, new TypeReference<RedisUser>() {});
        log.info("[getCacheMiss] 未命中读取结果(应为null): {}", JsonUtil.Obj2string(result));

        // 3. 未命中后 L1 不应被污染（不应写入空值占位）
        log.info("[getCacheMiss] 未命中后L1是否被污染(应为false): {}",
                caffeineCache.getIfPresent(key) != null);
    }

    /**
     * 测试场景5：L1 命中优先级验证
     * <p>
     * 验证点：
     * 1. 当 L1 与 L2 中的值不同时，getCache 优先返回 L1 的值
     * 2. 验证二级缓存"就近优先"的读取语义
     */
    @GetMapping("/getCacheL1Priority")
    public void getCacheL1Priority() {
        String key = "cache:priority:user:5";
        RedisUser l1User = new RedisUser(5L, "L1-本地值", 20, LocalDateTime.now());
        RedisUser l2User = new RedisUser(5L, "L2-Redis值", 99, LocalDateTime.now());

        // 每次测试前清空 L1 和 L2
        caffeineCache.invalidate(key);
        redisService.deleteObject(key);

        // 1. 分别向 L1、L2 写入不同的值
        cacheService.setL1Cache(key, l1User);
        cacheService.setL2Cache(key, l2User, 60L, TimeUnit.SECONDS);
        log.info("[getCacheL1Priority] L1写入: {}, L2写入: {}",
                JsonUtil.Obj2string(l1User), JsonUtil.Obj2string(l2User));

        // 2. getCache 应优先返回 L1 的值
        RedisUser result = cacheService.getCache(key, new TypeReference<RedisUser>() {});
        log.info("[getCacheL1Priority] 读取结果(应为'L1-本地值'): {}", JsonUtil.Obj2string(result));

        // 清理
        caffeineCache.invalidate(key);
        redisService.deleteObject(key);
    }
}
