package cl.campuslab.notifier.messaging;

import java.util.Map;

import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMessageConfig {

    @Bean
    public MessageConverter rabbitMessageConverter() {
        DefaultClassMapper classMapper = new DefaultClassMapper();

        classMapper.setTrustedPackages(
                "cl.campuslab.notifier.messaging");

        classMapper.setIdClassMapping(Map.of(
                "booking.task.v1", BookingTaskMessage.class
        ));

        classMapper.afterPropertiesSet();

        JacksonJsonMessageConverter converter =
                new JacksonJsonMessageConverter(
                        "cl.campuslab.notifier.messaging");

        converter.setClassMapper(classMapper);

        return converter;
    }
}