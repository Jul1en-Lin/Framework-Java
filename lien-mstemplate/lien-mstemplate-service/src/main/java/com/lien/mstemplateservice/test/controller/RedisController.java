package com.lien.mstemplateservice.test.controller;

import com.lien.mstemplateservice.test.entity.RedisUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.RedisService;
import utils.JsonUtil;

import java.time.LocalDateTime;
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
}
