package com.lien.mstemplateservice.test.controller;

import domain.Result;
import domain.exception.ServiceException;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
@Validated
@Slf4j
public class TestController {

    @GetMapping("/success")
    public Result<String> success() {
        log.info("网关路由测试接口正常");
        return Result.success("网关路由测试接口正常");
    }

    @GetMapping("/error")
    public Result<Void> error() {
        throw new ServiceException("异常捕获测试hi~");
    }

    @GetMapping("/validation")
    public Result<String> validation(@RequestParam @NotBlank(message = "name不能为空") String name) {
        return Result.success(name);
    }
}
