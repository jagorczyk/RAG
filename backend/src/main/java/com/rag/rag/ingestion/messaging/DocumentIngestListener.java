package com.rag.rag.ingestion.messaging;

import com.rag.rag.ingestion.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.ingest.async-enabled", havingValue = "true", matchIfMissing = true)
public class DocumentIngestListener {

    private final IngestionService ingestionService;

    @RabbitListener(queues = IngestQueueNames.QUEUE)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        log.info("Consuming DocumentUploaded path={} owner={}", event.path(), event.ownerId());
        ingestionService.processQueuedDocument(event);
    }
}
