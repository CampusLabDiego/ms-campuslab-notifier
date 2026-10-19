package cl.campuslab.notifier.messaging;

import java.io.IOException;
import java.util.Map;

import com.rabbitmq.client.Channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TaskDlqListener {

    private static final Logger log =
            LoggerFactory.getLogger(TaskDlqListener.class);

    @RabbitListener(queues = {
            RabbitNames.EMAIL_DLQ,
            RabbitNames.PREP_DLQ,
            RabbitNames.VOUCHER_DLQ
    })
    public void receive(
            Message message,
            Channel channel) throws IOException {

        Map<String, Object> headers =
                message.getMessageProperties().getHeaders();

        log.error(
                "Tarea fallida en DLQ: mensaje={}, colaDLQ={}, colaOrigen={}, motivo={}, bytes={}",
                message.getMessageProperties().getMessageId(),
                message.getMessageProperties().getConsumerQueue(),
                headers.getOrDefault(
                        "x-first-death-queue",
                        "desconocida"),
                headers.getOrDefault(
                        "x-first-death-reason",
                        "desconocido"),
                message.getBody().length);

        channel.basicAck(
                message.getMessageProperties().getDeliveryTag(),
                false);
    }
}