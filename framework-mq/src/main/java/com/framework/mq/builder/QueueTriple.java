package com.framework.mq.builder;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;

/**
 * 队列三元组占位类（Java 17 record 的替代，兼容性更好）
 */
public class QueueTriple {
    private final Exchange exchange;
    private final Queue queue;
    private final Binding binding;

    public QueueTriple(Exchange exchange, Queue queue, Binding binding) {
        this.exchange = exchange;
        this.queue = queue;
        this.binding = binding;
    }

    public Exchange getExchange() { return exchange; }
    public Queue getQueue() { return queue; }
    public Binding getBinding() { return binding; }
}
