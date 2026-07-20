package com.rag.rag.ingestion.messaging;

import java.util.UUID;

/**
 * Queue payload after HTTP accepts an upload (Sprint 2 async ingest).
 */
public record DocumentUploadedEvent(
        String path,
        UUID ownerId,
        String contentHash,
        String folderName,
        String fileName,
        String entityTag
) {}
