package com.lien.adminservice.user.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 */
@Configuration
@Slf4j
public class RabbitMqConfig {

    // 交换机的名称
    public final static String EXCHANGE_NAME = "edit_user_exchange";

    /**
     * 广播交换机
     * @return FanoutExchange 广播交换机，会把消息路由到所有绑定到它的队列
     */
    @Bean
    public FanoutExchange editUserExchange() {
        return new FanoutExchange(EXCHANGE_NAME, true, true);
    }
}
