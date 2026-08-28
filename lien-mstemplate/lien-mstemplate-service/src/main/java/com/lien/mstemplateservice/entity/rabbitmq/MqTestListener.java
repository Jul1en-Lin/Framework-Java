package com.lien.mstemplateservice.entity.rabbitmq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import utils.JsonUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQ 测试监听器：消费各测试队列的消息并存入内存，供测试接口读取验证。
 */
@Slf4j
@Component
public class MqTestListener {

    /**
     * 内存接收存储：key=队列名，value=该队列最新收到的消息。
     * 测试接口在发送前先 clear，发送后从此处读取结果进行比对。
     */
    public static final Map<String, Object> RECEIVED = new ConcurrentHashMap<>();

    @RabbitListener(queues = MqTestConfig.SIMPLE_QUEUE)
    public void receiveSimple(String message) {
        log.info("[Listener] {} 收到消息: {}", MqTestConfig.SIMPLE_QUEUE, message);
        RECEIVED.put(MqTestConfig.SIMPLE_QUEUE, message);
    }

    @RabbitListener(queues = MqTestConfig.FANOUT_QUEUE_A)
    public void receiveFanoutA(MqMessage message) {
        log.info("[Listener] {} 收到消息: {}", MqTestConfig.FANOUT_QUEUE_A, JsonUtil.Obj2string(message));
        RECEIVED.put(MqTestConfig.FANOUT_QUEUE_A, message);
    }

    @RabbitListener(queues = MqTestConfig.FANOUT_QUEUE_B)
    public void receiveFanoutB(MqMessage message) {
        log.info("[Listener] {} 收到消息: {}", MqTestConfig.FANOUT_QUEUE_B, JsonUtil.Obj2string(message));
        RECEIVED.put(MqTestConfig.FANOUT_QUEUE_B, message);
    }

    @RabbitListener(queues = MqTestConfig.DIRECT_QUEUE_INFO)
    public void receiveDirectInfo(MqMessage message) {
        log.info("[Listener] {} 收到消息: {}", MqTestConfig.DIRECT_QUEUE_INFO, JsonUtil.Obj2string(message));
        RECEIVED.put(MqTestConfig.DIRECT_QUEUE_INFO, message);
    }

    @RabbitListener(queues = MqTestConfig.DIRECT_QUEUE_ERROR)
    public void receiveDirectError(MqMessage message) {
        log.info("[Listener] {} 收到消息: {}", MqTestConfig.DIRECT_QUEUE_ERROR, JsonUtil.Obj2string(message));
        RECEIVED.put(MqTestConfig.DIRECT_QUEUE_ERROR, message);
    }

    @RabbitListener(queues = MqTestConfig.TOPIC_QUEUE_ORDER)
    public void receiveTopicOrder(MqMessage message) {
        log.info("[Listener] {} 收到消息: {}", MqTestConfig.TOPIC_QUEUE_ORDER, JsonUtil.Obj2string(message));
        RECEIVED.put(MqTestConfig.TOPIC_QUEUE_ORDER, message);
    }

    @RabbitListener(queues = MqTestConfig.TOPIC_QUEUE_PAY)
    public void receiveTopicPay(MqMessage message) {
        log.info("[Listener] {} 收到消息: {}", MqTestConfig.TOPIC_QUEUE_PAY, JsonUtil.Obj2string(message));
        RECEIVED.put(MqTestConfig.TOPIC_QUEUE_PAY, message);
    }

    @RabbitListener(queues = MqTestConfig.OBJECT_QUEUE)
    public void receiveObject(MqMessage message) {
        log.info("[Listener] {} 收到消息: {}", MqTestConfig.OBJECT_QUEUE, JsonUtil.Obj2string(message));
        RECEIVED.put(MqTestConfig.OBJECT_QUEUE, message);
    }
}
