package com.lien.mstemplateservice.test.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/test")
public class GatewayController {

    @GetMapping("/info")
    public void info() {
        log.info("GatewayController test method called");
    }
}
