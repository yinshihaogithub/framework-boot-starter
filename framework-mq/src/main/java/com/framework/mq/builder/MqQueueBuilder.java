package com.framework.mq.builder;

import org.springframework.amqp.core.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 队列/交换机声明工具
 * 快速构建普通队列、死信队列、延迟队列的声明
 */
public class MqQueueBuilder {

    /**
     * 构建普通队列 + 直连交换机 + 绑定
     */
    public static QueueTriple buildDirect(String exchangeName, String queueName, String routingKey) {
        DirectExchange exchange = ExchangeBuilder.directExchange(exchangeName).durable(true).build();
        Queue queue = QueueBuilder.durable(queueName).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
        return new QueueTriple(exchange, queue, binding);
    }

    /**
     * 构建主题队列（通配符路由）
     */
    public static QueueTriple buildTopic(String exchangeName, String queueName, String routingPattern) {
        TopicExchange exchange = ExchangeBuilder.topicExchange(exchangeName).durable(true).build();
        Queue queue = QueueBuilder.durable(queueName).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingPattern);
        return new QueueTriple(exchange, queue, binding);
    }

    /**
     * 构建带死信队列的队列
     * 消息被拒绝(nack/reject)或TTL过期后，转发到死信交换机
     *
     * @param queueName     业务队列名
     * @param dlxExchange   死信交换机名
     * @param dlxRoutingKey 死信路由Key
     */
    public static QueueTriple buildQueueWithDLX(String queueName, String dlxExchange, String dlxRoutingKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", dlxExchange);
        args.put("x-dead-letter-routing-key", dlxRoutingKey);
        Queue queue = QueueBuilder.durable(queueName).withArguments(args).build();
        // 死信交换机用直连方式
        DirectExchange dlxEx = ExchangeBuilder.directExchange(dlxExchange).durable(true).build();
        // 业务队列本身不需要绑定到死信交换机，返回死信交换机方便声明
        return new QueueTriple(dlxEx, queue, null);
    }

    /**
     * 构建重试队列（TTL + 死信转发）
     * 消息进入重试队列后，等待 ttl 毫秒超时，通过死信机制转发到目标交换机
     *
     * @param retryQueueName 重试队列名
     * @param ttlMs          消息存活时间（毫秒），超时后转发
     * @param targetExchange 目标交换机（通常为原业务交换机）
     * @param targetRoutingKey 目标路由Key
     */
    public static QueueTriple buildRetryQueue(String retryQueueName, long ttlMs,
                                               String targetExchange, String targetRoutingKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", targetExchange);
        args.put("x-dead-letter-routing-key", targetRoutingKey);
        args.put("x-message-ttl", ttlMs);
        Queue queue = QueueBuilder.durable(retryQueueName).withArguments(args).build();
        // 重试队列不需要绑定交换机，消息直接通过 TTL 触发死信转发
        return new QueueTriple(null, queue, null);
    }

    /**
     * 构建延迟队列（基于 rabbitmq_delayed_message_exchange 插件）
     * 需安装插件：rabbitmq_delayed_message_exchange
     *
     * @param exchangeName 延迟交换机名
     * @param queueName    延迟队列名
     * @param routingKey   路由Key
     */
    public static QueueTriple buildDelayed(String exchangeName, String queueName, String routingKey) {
        // 自定义延迟交换机类型
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        CustomExchange exchange = new CustomExchange(exchangeName, "x-delayed-message", true, false, args);
        Queue queue = QueueBuilder.durable(queueName).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey).noargs();
        return new QueueTriple(exchange, queue, binding);
    }
}
