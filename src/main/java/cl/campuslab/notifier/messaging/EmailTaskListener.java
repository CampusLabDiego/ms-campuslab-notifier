package cl.campuslab.notifier.messaging;

import java.io.IOException;

import com.rabbitmq.client.Channel;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EmailTaskListener {

    private final TaskMessageHandler handler;

    public EmailTaskListener(TaskMessageHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitNames.EMAIL_QUEUE)
    public void receive(
            BookingTaskMessage task,
            Message message,
            Channel channel) throws IOException {

        handler.handle(
                task,
                message,
                channel,
                BookingTaskMessage.TaskType.EMAIL
        );
    }
}