package com.lien.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class LienGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LienGatewayApplication.class, args);
        log.info("网关启动成功");
    }
}
