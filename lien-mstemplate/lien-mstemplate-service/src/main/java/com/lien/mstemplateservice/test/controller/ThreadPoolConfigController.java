package com.lien.mstemplateservice.test.controller;

import com.lien.mstemplateservice.service.ThreadPoolServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/test")
public class ThreadPoolConfigController {

    @Autowired
    private ThreadPoolServiceImpl threadPoolService;

    @GetMapping("/threadPoolInfo")
    public void info() {
        log.info("TestThreadPoolController thread name: {}", Thread.currentThread().getName());
        threadPoolService.info();
    }
}
