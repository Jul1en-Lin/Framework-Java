package com.lien.gateway.handler;

import com.lien.gateway.LienGatewayApplication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;

@Slf4j
class GatewayExceptionHandlerTest {
public static void main(String[] args) {
        SpringApplication.run(LienGatewayApplication.class, args);
        log.info("网关启动成功");
    }
}
