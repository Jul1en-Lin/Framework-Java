package com.lien.mstemplateservice.entity.rabbitmq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lien.common.core.utils.JsonUtil;

import java.time.LocalDateTime;

/**
 * RabbitMQ 测试接口（冒烟测试）。
 * <p>
 * 消息消费是异步的，每个接口的流程为：清空接收存储 → 发送消息 → 轮询等待消费结果 → 日志比对。
 * 覆盖场景：简单队列、fanout 广播、direct 路由、topic 通配符、对象 JSON 序列化。
 */
@Slf4j
@RestController
@RequestMapping("/test/mq")
public class RabbitMqTestController {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 测试场景1：简单队列（默认交换机，routingKey=队列名）
     * <p>
     * 验证点：
     * 1. 消息能发送到 mq.test.simple 队列
     * 2. 监听器能原样收到内容
     */
    @GetMapping("/simple")
    public void simple() throws InterruptedException {
        MqTestListener.RECEIVED.clear();

        String content = "hello-simple-" + System.currentTimeMillis();
        rabbitTemplate.convertAndSend(MqTestConfig.SIMPLE_QUEUE, content);
        log.info("[simple] 发送完成, queue={}, content={}", MqTestConfig.SIMPLE_QUEUE, content);

        Object received = waitForMessage(MqTestConfig.SIMPLE_QUEUE, 3000);
        log.info("[simple] 接收结果(应等于发送内容): {}", received);
        log.info("[simple] 内容是否一致(应为true): {}", content.equals(received));
    }

    /**
     * 测试场景2：fanout 广播
     * <p>
     * 验证点：
     * 1. 发到 fanout 交换机的一条消息，绑定的两个队列都能收到（忽略 routingKey）
     */
    @GetMapping("/fanout")
    public void fanout() throws InterruptedException {
        MqTestListener.RECEIVED.clear();

        MqMessage message = new MqMessage(1L, "fanout-broadcast-" + System.currentTimeMillis(), LocalDateTime.now());
        rabbitTemplate.convertAndSend(MqTestConfig.FANOUT_EXCHANGE, "", message);
        log.info("[fanout] 发送完成, exchange={}, message={}", MqTestConfig.FANOUT_EXCHANGE, JsonUtil.Obj2string(message));

        Object receivedA = waitForMessage(MqTestConfig.FANOUT_QUEUE_A, 3000);
        Object receivedB = waitForMessage(MqTestConfig.FANOUT_QUEUE_B, 3000);
        log.info("[fanout] 队列A是否收到(应为true): {}, 内容: {}", receivedA != null, JsonUtil.Obj2string(receivedA));
        log.info("[fanout] 队列B是否收到(应为true): {}, 内容: {}", receivedB != null, JsonUtil.Obj2string(receivedB));
    }

    /**
     * 测试场景3：direct 直连路由
     * <p>
     * 验证点：
     * 1. routingKey=info 的消息只进 info 队列
     * 2. routingKey=error 的消息只进 error 队列
     */
    @GetMapping("/direct")
    public void direct() throws InterruptedException {
        MqTestListener.RECEIVED.clear();

        MqMessage infoMsg = new MqMessage(2L, "direct-info-" + System.currentTimeMillis(), LocalDateTime.now());
        MqMessage errorMsg = new MqMessage(3L, "direct-error-" + System.currentTimeMillis(), LocalDateTime.now());
        rabbitTemplate.convertAndSend(MqTestConfig.DIRECT_EXCHANGE, MqTestConfig.DIRECT_KEY_INFO, infoMsg);
        rabbitTemplate.convertAndSend(MqTestConfig.DIRECT_EXCHANGE, MqTestConfig.DIRECT_KEY_ERROR, errorMsg);
        log.info("[direct] 发送完成, key=info -> {}, key=error -> {}",
                JsonUtil.Obj2string(infoMsg), JsonUtil.Obj2string(errorMsg));

        Object receivedInfo = waitForMessage(MqTestConfig.DIRECT_QUEUE_INFO, 3000);
        Object receivedError = waitForMessage(MqTestConfig.DIRECT_QUEUE_ERROR, 3000);
        log.info("[direct] info队列收到(应为direct-info内容): {}", JsonUtil.Obj2string(receivedInfo));
        log.info("[direct] error队列收到(应为direct-error内容): {}", JsonUtil.Obj2string(receivedError));
    }

    /**
     * 测试场景4：topic 通配符路由
     * <p>
     * 验证点：
     * 1. routingKey=order.create 能匹配绑定 order.* 的队列
     * 2. 不能匹配绑定 pay.* 的队列
     */
    @GetMapping("/topic")
    public void topic() throws InterruptedException {
        MqTestListener.RECEIVED.clear();

        MqMessage message = new MqMessage(4L, "topic-order-create-" + System.currentTimeMillis(), LocalDateTime.now());
        rabbitTemplate.convertAndSend(MqTestConfig.TOPIC_EXCHANGE, "order.create", message);
        log.info("[topic] 发送完成, exchange={}, routingKey=order.create, message={}",
                MqTestConfig.TOPIC_EXCHANGE, JsonUtil.Obj2string(message));

        Object receivedOrder = waitForMessage(MqTestConfig.TOPIC_QUEUE_ORDER, 3000);
        log.info("[topic] order队列(绑定order.*)是否收到(应为true): {}, 内容: {}",
                receivedOrder != null, JsonUtil.Obj2string(receivedOrder));

        // 负向验证：pay队列绑定 pay.*，不应收到 order.create，等一小段时间确认
        Thread.sleep(1000);
        Object receivedPay = MqTestListener.RECEIVED.get(MqTestConfig.TOPIC_QUEUE_PAY);
        log.info("[topic] pay队列(绑定pay.*)是否收到(应为false): {}", receivedPay != null);
    }

    /**
     * 测试场景5：对象消息 JSON 序列化
     * <p>
     * 验证点：
     * 1. common 模块自动装配的 Jackson2JsonMessageConverter 生效
     * 2. MqMessage 对象发送后能完整反序列化回来，字段（含 LocalDateTime）不丢失
     */
    @GetMapping("/object")
    public void object() throws InterruptedException {
        MqTestListener.RECEIVED.clear();

        MqMessage message = new MqMessage(5L, "object-message", LocalDateTime.now());
        rabbitTemplate.convertAndSend(MqTestConfig.OBJECT_QUEUE, message);
        log.info("[object] 发送完成, queue={}, message={}", MqTestConfig.OBJECT_QUEUE, JsonUtil.Obj2string(message));

        Object received = waitForMessage(MqTestConfig.OBJECT_QUEUE, 3000);
        log.info("[object] 接收结果(字段应与发送一致): {}", JsonUtil.Obj2string(received));
        if (received instanceof MqMessage receivedMsg) {
            log.info("[object] 反序列化类型正确(应为true): true, id={}, content={}, sendTime={}",
                    receivedMsg.getId(), receivedMsg.getContent(), receivedMsg.getSendTime());
        } else {
            log.info("[object] 反序列化类型正确(应为true): false, 实际类型: {}",
                    received == null ? "null" : received.getClass().getName());
        }
    }

    /**
     * 轮询等待指定队列收到消息，最多等待 timeoutMs 毫秒
     *
     * @param queueName 队列名
     * @param timeoutMs 超时时间（毫秒）
     * @return 收到的消息，超时未收到返回 null
     */
    private Object waitForMessage(String queueName, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Object message;
        while ((message = MqTestListener.RECEIVED.get(queueName)) == null
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        return message;
    }
}
