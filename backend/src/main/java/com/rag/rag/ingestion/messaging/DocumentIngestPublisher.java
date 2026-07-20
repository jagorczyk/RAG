package com.rag.rag.ingestion.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.ingest.async-enabled", havingValue = "true", matchIfMissing = true)
public class DocumentIngestPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(DocumentUploadedEvent event) {
        rabbitTemplate.convertAndSend(
                IngestQueueNames.EXCHANGE,
                IngestQueueNames.ROUTING_KEY,
                event
        );
        log.info("Published DocumentUploaded for path={} owner={}", event.path(), event.ownerId());
    }
}
