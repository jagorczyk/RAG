package com.rag.rag.ingestion.messaging;

public final class IngestQueueNames {

    public static final String EXCHANGE = "rag.ingest";
    public static final String DLX = "rag.ingest.dlx";
    public static final String QUEUE = "rag.ingest.document-uploaded";
    public static final String DLQ = "rag.ingest.document-uploaded.dlq";
    public static final String ROUTING_KEY = "document.uploaded";
    public static final String DLQ_ROUTING_KEY = "document.uploaded.dlq";

    private IngestQueueNames() {}
}
