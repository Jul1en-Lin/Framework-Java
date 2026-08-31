package com.lien.mstemplateservice.test.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lien.mstemplateservice.test.entity.RedisUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.RedisService;
import com.lien.common.core.utils.JsonUtil;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    @GetMapping("/set")
    public void testSet() {
        String key = "redis:user:set";
        TypeReference<Set<RedisUser>> setTypeRef = new TypeReference<Set<RedisUser>>() {};

        // 每次测试前先清空，避免历史数据干扰：
        // 取出全部历史成员，再批量删除（集合需先 toArray() 展开后再传入可变参数，
        // 直接传集合会被当成"单个元素"处理）
        Set<RedisUser> history = redisService.getCacheSet(key, setTypeRef);
        if (history != null && !history.isEmpty()) {
            redisService.deleteMember(key, history.toArray());
        }
        log.info("[set-清空] 清空后集合(应为空): {}",
                JsonUtil.Obj2string(redisService.getCacheSet(key, setTypeRef)));

        // 1. addMember: 添加单个元素
        RedisUser user1 = new RedisUser(1L, "张三", 20, LocalDateTime.now());
        Long addedOne = redisService.addMember(key, user1);
        log.info("[addMember] 添加单个元素, 实际新增个数(应为1): {}, 集合: {}",
                addedOne, JsonUtil.Obj2string(redisService.getCacheSet(key, setTypeRef)));

        // 2. addMember: 可变参数批量添加多个元素
        RedisUser user2 = new RedisUser(2L, "李四", 30, LocalDateTime.now());
        RedisUser user3 = new RedisUser(3L, "王五", 40, LocalDateTime.now());
        Long batchAdded = redisService.addMember(key, user2, user3);
        Set<RedisUser> afterBatchAdd = redisService.getCacheSet(key, setTypeRef);
        log.info("[addMember] 批量添加, 实际新增个数(应为2): {}, 集合大小(应为3): {}, 全量: {}",
                batchAdded, afterBatchAdd.size(), JsonUtil.Obj2string(afterBatchAdd));

        // 3. addMember: 添加重复元素（与user1序列化结果相同），Set 自动去重
        // 返回值直接反映去重结果，无需额外读取集合
        Long duplicateAdded = redisService.addMember(key, user1);
        Set<RedisUser> afterDuplicateAdd = redisService.getCacheSet(key, setTypeRef);
        log.info("[addMember] 重复添加'张三', 实际新增个数(应为0，去重直接可见): {}, 集合大小(应仍为3): {}, 全量: {}",
                duplicateAdded, afterDuplicateAdd.size(), JsonUtil.Obj2string(afterDuplicateAdd));

//        // 4. deleteMember: 删除单个元素
//        Long removedOne = redisService.deleteMember(key, user2);
//        Set<RedisUser> afterDeleteOne = redisService.getCacheSet(key, setTypeRef);
//        log.info("[deleteMember] 删除'李四', 实际删除个数(应为1): {}, 集合大小(应为2): {}, 全量: {}",
//                removedOne, afterDeleteOne.size(), JsonUtil.Obj2string(afterDeleteOne));
//
//        // 5. deleteMember: 可变参数批量删除剩余元素
//        Long batchRemoved = redisService.deleteMember(key, user1, user
//        3);
//        Set<RedisUser> afterBatchDelete = redisService.getCacheSet(key, setTypeRef);
//        log.info("[deleteMember] 批量删除, 实际删除个数(应为2): {}, 集合大小(应为0): {}, 全量: {}",
//                batchRemoved, afterBatchDelete.size(), JsonUtil.Obj2string(afterBatchDelete));
    }

    @GetMapping("/zset")
    public void testZSet() {
        String key = "redis:user:zset";
        TypeReference<LinkedHashSet<RedisUser>> zsetTypeRef = new TypeReference<LinkedHashSet<RedisUser>>() {};

        // 每次测试前先清空，避免历史数据干扰：
        // 取出全部历史成员，逐个删除（delMemberZSet 仅支持单元素删除）
        Set<RedisUser> history = redisService.getCacheZSet(key, zsetTypeRef);
        if (history != null && !history.isEmpty()) {
            history.forEach(member -> redisService.delMemberZSet(key, member));
        }
        log.info("[zset-清空] 清空后集合(应为空): {}",
                JsonUtil.Obj2string(redisService.getCacheZSet(key, zsetTypeRef)));

        // 1. addMemberZSet: 添加3个元素，score分别为 10、30、20
        RedisUser user1 = new RedisUser(1L, "张三", 20, LocalDateTime.now());
        RedisUser user2 = new RedisUser(2L, "李四", 30, LocalDateTime.now());
        RedisUser user3 = new RedisUser(3L, "王五", 40, LocalDateTime.now());
        Boolean add1 = redisService.addMemberZSet(key, user1, 10);
        Boolean add2 = redisService.addMemberZSet(key, user2, 30);
        Boolean add3 = redisService.addMemberZSet(key, user3, 20);
        log.info("[addMemberZSet] 添加3个元素, 返回(应均为true): {}/{}/{}, 升序全量(应按score: 张三10<王五20<李四30): {}",
                add1, add2, add3, JsonUtil.Obj2string(redisService.getCacheZSet(key, zsetTypeRef)));

        // 2. addMemberZSet: 重复添加已有元素'张三'，score更新为40，返回false，排序位置移到最后
        Boolean addDuplicate = redisService.addMemberZSet(key, user1, 40);
        log.info("[addMemberZSet] 重复添加'张三'并更新score为40, 返回(应为false): {}, 升序全量('张三'应排最后): {}",
                addDuplicate, JsonUtil.Obj2string(redisService.getCacheZSet(key, zsetTypeRef)));

        // 3. getCacheZSetDesc: 降序获取全量
        Set<RedisUser> descSet = redisService.getCacheZSetDesc(key, zsetTypeRef);
        log.info("[getCacheZSetDesc] 降序全量('张三'40应排最前): {}", JsonUtil.Obj2string(descSet));

        // 4. getCacheZSet(key, typeRef, start, end): 升序取下标[0,1]，即score最低的2个
        Set<RedisUser> rangeSet = redisService.getCacheZSet(key, zsetTypeRef, 0, 1);
        log.info("[getCacheZSet-range] 升序下标[0,1](应为'王五'20、'李四'30): {}", JsonUtil.Obj2string(rangeSet));

        // 5. removeZSetByScore: 删除score在[20, 30]闭区间的元素（'王五'、'李四'）
        Long removedByScore = redisService.removeZSetByScore(key, 20, 30);
        log.info("[removeZSetByScore] 删除score[20,30]区间, 实际删除个数(应为2): {}, 剩余(应只剩'张三'): {}",
                removedByScore, JsonUtil.Obj2string(redisService.getCacheZSet(key, zsetTypeRef)));

        // 6. delMemberZSet: 删除指定元素
        Long removed = redisService.delMemberZSet(key, user1);
        log.info("[delMemberZSet] 删除'张三', 实际删除个数(应为1): {}, 剩余(应为空): {}",
                removed, JsonUtil.Obj2string(redisService.getCacheZSet(key, zsetTypeRef)));
    }

    @GetMapping("/common")
    public void testCommon() {
        String key1 = "redis:common:1";
        String key2 = "redis:common:2";
        String renamedKey = "redis:common:rename";

        // 每次测试前先清空，避免历史数据干扰
        redisService.deleteObject(Arrays.asList(key1, key2, renamedKey));

        // 0. 准备测试数据
        RedisUser user = new RedisUser(1L, "张三", 20, LocalDateTime.now());
        redisService.setCacheObject(key1, user);
        redisService.setCacheObject(key2, user);
        log.info("[common-准备] 写入2个测试key完成: {}, {}", key1, key2);

        // 1. hasKey: 判断 key 是否存在
        Boolean exists = redisService.hasKey(key1);
        Boolean notExists = redisService.hasKey("redis:common:not-exists");
        log.info("[hasKey] 存在的key(应为true): {}, 不存在的key(应为false): {}", exists, notExists);

        // 2. expire(key, timeout): 设置过期时间（默认单位秒）
        Boolean expireResult = redisService.expire(key1, 60);
        log.info("[expire] 设置60s过期(应为true): {}", expireResult);

        // 3. getExpire: 获取剩余有效时间（秒）
        Long ttl = redisService.getExpire(key1);
        log.info("[getExpire] 剩余有效时间(应<=60且>0): {}s", ttl);

        // 4. expire(key, timeout, timeUnit): 指定时间单位设置过期时间
        Boolean expireWithUnit = redisService.expire(key2, 2, TimeUnit.MINUTES);
        Long ttl2 = redisService.getExpire(key2);
        log.info("[expire-timeUnit] 设置2分钟过期(应为true): {}, 剩余有效时间(应<=120且>60): {}s", expireWithUnit, ttl2);

        // 5. keys: 按模式查找匹配的键
        Collection<String> matchedKeys = redisService.keys("redis:common:*");
        log.info("[keys] 模式'redis:common:*'匹配到的键(应为2个): {}", JsonUtil.Obj2string(matchedKeys));

        // 6. renameKey: 重命名 key（重命名后原 key 的过期时间会保留）
        redisService.renameKey(key2, renamedKey);
        log.info("[renameKey] {} 重命名为 {}, 旧key是否存在(应为false): {}, 新key是否存在(应为true): {}, 新key剩余过期时间(应保留): {}s",
                key2, renamedKey, redisService.hasKey(key2), redisService.hasKey(renamedKey), redisService.getExpire(renamedKey));

        // 7. deleteObject(key): 删除单个数据
        Boolean deletedOne = redisService.deleteObject(key1);
        log.info("[deleteObject] 删除单个key(应为true): {}, 删除后是否存在(应为false): {}", deletedOne, redisService.hasKey(key1));

        // 8. deleteObject(collection): 批量删除（先补回 key1、key2 再一次性删除3个）
        redisService.setCacheObject(key1, user);
        redisService.setCacheObject(key2, user);
        Long deletedCount = redisService.deleteObject(Arrays.asList(key1, key2, renamedKey));
        log.info("[deleteObject-batch] 批量删除3个key, 实际删除数量(应为3): {}, 剩余匹配'redis:common:*'的键(应为空): {}",
                deletedCount, JsonUtil.Obj2string(redisService.keys("redis:common:*")));
    }
}
