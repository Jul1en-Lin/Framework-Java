package com.lien.mstemplateservice.test.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lien.mstemplateservice.test.entity.RedisUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.RedisService;
import utils.JsonUtil;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/test/redis")
public class RedisController {

    @Autowired
    private RedisService redisService;

    @GetMapping("/setCacheObject")
    public void setCacheObject() {
        RedisUser user = new RedisUser(1L, "张三", 20, LocalDateTime.now());

        redisService.setCacheObject("redis:user:1", user);
        log.info("[setCacheObject] 无过期时间写入完成, key=redis:user:1, value={}", JsonUtil.Obj2string(user));

        RedisUser cached = redisService.getCacheObject("redis:user:1", RedisUser.class);
        log.info("[setCacheObject] 无过期时间读取结果: {}", JsonUtil.Obj2string(cached));

        redisService.setCacheObject("redis:user:2", user, 60, TimeUnit.SECONDS);
        log.info("[setCacheObject] 带过期时间(60s)写入完成, key=redis:user:2, value={}", JsonUtil.Obj2string(user));

        RedisUser cached2 = redisService.getCacheObject("redis:user:2", RedisUser.class);
        log.info("[setCacheObject] 带过期时间读取结果: {}", JsonUtil.Obj2string(cached2));
    }

    @GetMapping("/setCacheObjectIfAbsent")
    public void setCacheObjectIfAbsent() {
        RedisUser user = new RedisUser(2L, "李四", 30, LocalDateTime.now());

        Boolean first = redisService.setCacheObjectIfAbsent("redis:user:3", user);
        log.info("[setCacheObjectIfAbsent] 首次写入结果(应为true): {}", first);

        Boolean second = redisService.setCacheObjectIfAbsent("redis:user:3", user);
        log.info("[setCacheObjectIfAbsent] 重复写入结果(应为false): {}", second);

        Boolean third = redisService.setCacheObjectIfAbsent("redis:user:4", user, 60, TimeUnit.SECONDS);
        log.info("[setCacheObjectIfAbsent] 带过期时间首次写入结果(应为true): {}", third);

        Boolean fourth = redisService.setCacheObjectIfAbsent("redis:user:4", user, 60, TimeUnit.SECONDS);
        log.info("[setCacheObjectIfAbsent] 带过期时间重复写入结果(应为false): {}", fourth);

        RedisUser cached = redisService.getCacheObject("redis:user:3", RedisUser.class);
        log.info("[setCacheObjectIfAbsent] 读取结果: {}", JsonUtil.Obj2string(cached));
    }

    @GetMapping("/list")
    public void testList() {
        String key = "redis:user:list";
        // 每次测试前先清空，避免历史数据干扰
        redisService.removeForAllList(key);

        // 1. setCacheList: 批量缓存List
        List<RedisUser> initList = Arrays.asList(
                new RedisUser(1L, "张三", 20, LocalDateTime.now()),
                new RedisUser(2L, "李四", 30, LocalDateTime.now()),
                new RedisUser(3L, "王五", 40, LocalDateTime.now())
        );
        Long pushAllSize = redisService.setCacheList(key, initList);
        log.info("[setCacheList] 批量写入完成, 返回列表长度(应为3): {}, 当前全量: {}",
                pushAllSize, JsonUtil.Obj2string(redisService.getCacheList(key, RedisUser.class)));

        // 2. leftPushForList: 头插
        Long afterLeftPush = redisService.leftPushForList(key, new RedisUser(0L, "头插用户", 18, LocalDateTime.now()));
        log.info("[leftPushForList] 头插后列表长度(应为4): {}, 当前全量: {}",
                afterLeftPush, JsonUtil.Obj2string(redisService.getCacheList(key, RedisUser.class)));

        // 3. rightPushForList: 尾插
        Long afterRightPush = redisService.rightPushForList(key, new RedisUser(4L, "尾插用户", 50, LocalDateTime.now()));
        log.info("[rightPushForList] 尾插后列表长度(应为5): {}, 当前全量: {}",
                afterRightPush, JsonUtil.Obj2string(redisService.getCacheList(key, RedisUser.class)));

        // 4. getCacheListSize: 获取长度
        long size = redisService.getCacheListSize(key);
        log.info("[getCacheListSize] 当前列表长度(应为5): {}", size);

        // 5. getCacheListByRange: 范围获取 [1, 3]
        List<RedisUser> rangeList = redisService.getCacheListByRange(key, 1, 3, RedisUser.class);
        log.info("[getCacheListByRange] 下标[1,3]元素: {}", JsonUtil.Obj2string(rangeList));

        // 6. getCacheList(TypeReference): 泛型嵌套获取
        List<RedisUser> listByTypeRef = redisService.getCacheList(key, new TypeReference<List<RedisUser>>() {});
        log.info("[getCacheList-TypeRef] 全量读取: {}", JsonUtil.Obj2string(listByTypeRef));

        // 7. setElementAtIndex: 修改下标为0的元素
        redisService.setElementAtIndex(key, 0, new RedisUser(99L, "被修改的头元素", 99, LocalDateTime.now()));
        log.info("[setElementAtIndex] 修改下标0后全量: {}", JsonUtil.Obj2string(redisService.getCacheList(key, RedisUser.class)));

        // 8. removeForList(key, value): 移除第一个匹配"李四"
        redisService.removeForList(key, new RedisUser(2L, "李四", 30, initList.get(1).getCreateTime()));
        log.info("[removeForList] 移除首个匹配'李四'后全量: {}", JsonUtil.Obj2string(redisService.getCacheList(key, RedisUser.class)));

        // 9. leftPopForList: 头删
        redisService.leftPopForList(key);
        log.info("[leftPopForList] 头删后全量: {}", JsonUtil.Obj2string(redisService.getCacheList(key, RedisUser.class)));

        // 10. rightPopForList: 尾删
        redisService.rightPopForList(key);
        log.info("[rightPopForList] 尾删后全量: {}", JsonUtil.Obj2string(redisService.getCacheList(key, RedisUser.class)));

        // 11. removeAllForList: 先追加一个重复元素，再全部移除
        redisService.rightPushForList(key, new RedisUser(3L, "王五", 40, initList.get(2).getCreateTime()));
        redisService.rightPushForList(key, new RedisUser(3L, "王五", 40, initList.get(2).getCreateTime()));
        log.info("[removeAllForList] 移除前全量(含重复'王五'): {}", JsonUtil.Obj2string(redisService.getCacheList(key, RedisUser.class)));
        redisService.removeAllForList(key, new RedisUser(3L, "王五", 40, initList.get(2).getCreateTime()));
        log.info("[removeAllForList] 移除所有'王五'后全量: {}", JsonUtil.Obj2string(redisService.getCacheList(key, RedisUser.class)));

        // 12. removeForList(key, start, end): trim 保留区间 [0, 0]，只留下第一个
        long sizeBeforeTrim = redisService.getCacheListSize(key);
        redisService.removeForList(key, 0, 0);
        log.info("[removeForList-trim] trim前长度={}, trim[0,0]后全量(应只剩1个): {}",
                sizeBeforeTrim, JsonUtil.Obj2string(redisService.getCacheList(key, RedisUser.class)));

        // 13. removeForAllList: 清空
        redisService.removeForAllList(key);
        log.info("[removeForAllList] 清空后长度(应为0): {}, 全量: {}",
                redisService.getCacheListSize(key), JsonUtil.Obj2string(redisService.getCacheList(key, RedisUser.class)));
    }
}
