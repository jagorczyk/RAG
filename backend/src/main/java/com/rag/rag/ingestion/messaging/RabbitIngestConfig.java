package com.rag.rag.ingestion.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "rag.ingest.async-enabled", havingValue = "true", matchIfMissing = true)
public class RabbitIngestConfig {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        return template;
    }

    @Bean
    public DirectExchange ingestExchange() {
        return new DirectExchange(IngestQueueNames.EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange ingestDlx() {
        return new DirectExchange(IngestQueueNames.DLX, true, false);
    }

    @Bean
    public Queue documentUploadedQueue() {
        return QueueBuilder.durable(IngestQueueNames.QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", IngestQueueNames.DLX,
                        "x-dead-letter-routing-key", IngestQueueNames.DLQ_ROUTING_KEY
                ))
                .build();
    }

    @Bean
    public Queue documentUploadedDlq() {
        return QueueBuilder.durable(IngestQueueNames.DLQ).build();
    }

    @Bean
    public Binding documentUploadedBinding(Queue documentUploadedQueue, DirectExchange ingestExchange) {
        return BindingBuilder.bind(documentUploadedQueue)
                .to(ingestExchange)
                .with(IngestQueueNames.ROUTING_KEY);
    }

    @Bean
    public Binding documentUploadedDlqBinding(Queue documentUploadedDlq, DirectExchange ingestDlx) {
        return BindingBuilder.bind(documentUploadedDlq)
                .to(ingestDlx)
                .with(IngestQueueNames.DLQ_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonMessageConverter);
        // Failed messages go to DLQ (default-requeue-rejected=false in properties)
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
