package com.lien.mstemplateservice.entity.rabbitmq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQ 测试用的队列/交换机/绑定声明。
 * 服务启动时由 RabbitAdmin 自动在 Broker 上创建，均为非持久化（测试用完即弃）。
 */
@Configuration
public class MqTestConfig {

    /** 简单队列（走默认交换机） */
    public static final String SIMPLE_QUEUE = "mq.test.simple";

    /** fanout 广播 */
    public static final String FANOUT_EXCHANGE = "mq.test.fanout";
    public static final String FANOUT_QUEUE_A = "mq.test.fanout.a";
    public static final String FANOUT_QUEUE_B = "mq.test.fanout.b";

    /** direct 直连路由 */
    public static final String DIRECT_EXCHANGE = "mq.test.direct";
    public static final String DIRECT_QUEUE_INFO = "mq.test.direct.info";
    public static final String DIRECT_QUEUE_ERROR = "mq.test.direct.error";
    public static final String DIRECT_KEY_INFO = "info";
    public static final String DIRECT_KEY_ERROR = "error";

    /** topic 通配符路由 */
    public static final String TOPIC_EXCHANGE = "mq.test.topic";
    public static final String TOPIC_QUEUE_ORDER = "mq.test.topic.order";
    public static final String TOPIC_QUEUE_PAY = "mq.test.topic.pay";
    public static final String TOPIC_PATTERN_ORDER = "order.*";
    public static final String TOPIC_PATTERN_PAY = "pay.*";

    /** 对象消息队列（验证 JSON MessageConverter） */
    public static final String OBJECT_QUEUE = "mq.test.object";

    // ==================== 简单队列 ====================

    @Bean
    public Queue simpleQueue() {
        return new Queue(SIMPLE_QUEUE, false);
    }

    // ==================== fanout ====================

    @Bean
    public FanoutExchange fanoutExchange() {
        return new FanoutExchange(FANOUT_EXCHANGE, false, false);
    }

    @Bean
    public Queue fanoutQueueA() {
        return new Queue(FANOUT_QUEUE_A, false);
    }

    @Bean
    public Queue fanoutQueueB() {
        return new Queue(FANOUT_QUEUE_B, false);
    }

    @Bean
    public Binding fanoutBindingA() {
        return BindingBuilder.bind(fanoutQueueA()).to(fanoutExchange());
    }

    @Bean
    public Binding fanoutBindingB() {
        return BindingBuilder.bind(fanoutQueueB()).to(fanoutExchange());
    }

    // ==================== direct ====================

    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(DIRECT_EXCHANGE, false, false);
    }

    @Bean
    public Queue directQueueInfo() {
        return new Queue(DIRECT_QUEUE_INFO, false);
    }

    @Bean
    public Queue directQueueError() {
        return new Queue(DIRECT_QUEUE_ERROR, false);
    }

    @Bean
    public Binding directBindingInfo() {
        return BindingBuilder.bind(directQueueInfo()).to(directExchange()).with(DIRECT_KEY_INFO);
    }

    @Bean
    public Binding directBindingError() {
        return BindingBuilder.bind(directQueueError()).to(directExchange()).with(DIRECT_KEY_ERROR);
    }

    // ==================== topic ====================

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE, false, false);
    }

    @Bean
    public Queue topicQueueOrder() {
        return new Queue(TOPIC_QUEUE_ORDER, false);
    }

    @Bean
    public Queue topicQueuePay() {
        return new Queue(TOPIC_QUEUE_PAY, false);
    }

    @Bean
    public Binding topicBindingOrder() {
        return BindingBuilder.bind(topicQueueOrder()).to(topicExchange()).with(TOPIC_PATTERN_ORDER);
    }

    @Bean
    public Binding topicBindingPay() {
        return BindingBuilder.bind(topicQueuePay()).to(topicExchange()).with(TOPIC_PATTERN_PAY);
    }

    // ==================== 对象队列 ====================

    @Bean
    public Queue objectQueue() {
        return new Queue(OBJECT_QUEUE, false);
    }
}
