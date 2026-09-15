package com.lien.adminservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class LienAdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LienAdminServiceApplication.class, args);
        log.info("admin 服务启动成功");
    }
}
