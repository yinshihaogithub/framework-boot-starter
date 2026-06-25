package com.framework.mq.deadletter;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * Default dead letter queue listener.
 */
public class MqDeadLetterListener {

    private final DeadLetterHandler deadLetterHandler;

    public MqDeadLetterListener(DeadLetterHandler deadLetterHandler) {
        this.deadLetterHandler = deadLetterHandler;
    }

    @RabbitListener(queues = "${framework.mq.dead-letter.queue:framework.dead.letter.queue}")
    public void handle(Message message, Channel channel) throws Exception {
        deadLetterHandler.handleDeadLetter(message, channel);
    }
}
