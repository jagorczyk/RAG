package com.rag.rag.knowledge.entity;

/** Describes how a mention was linked to a canonical identity. */
public enum IdentityEvidenceSource {
    USER,
    USER_TAG,
    FACE_MATCH,
    DESCRIPTION_MATCH,
    FACE_CLUSTER
}
