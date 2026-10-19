package cl.campuslab.notifier.messaging;

import java.io.IOException;
import java.util.Map;

import com.rabbitmq.client.Channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AuditDlqListener {

    private static final Logger log =
            LoggerFactory.getLogger(AuditDlqListener.class);

    @RabbitListener(queues = RabbitNames.AUDIT_DLQ)
    public void receive(
            Message message,
            Channel channel
    ) throws IOException {

        MessageProperties properties =
                message.getMessageProperties();

        Map<String, Object> headers = properties.getHeaders();

        log.error(
                "Auditoría en DLQ: evento={}, cola={}, "
                        + "origen={}, motivo={}, bytes={}",
                properties.getMessageId(),
                properties.getConsumerQueue(),
                headers.get("x-first-death-queue"),
                headers.get("x-first-death-reason"),
                message.getBody().length
        );

        channel.basicAck(
                properties.getDeliveryTag(),
                false
        );
    }
}