package com.rag.rag.knowledge.entity;

/**
 * Shared decision for removing knowledge entities after mentions disappear.
 * Pure predicate so unit tests cover keep/delete without a database.
 */
public final class OrphanEntityCleanupPolicy {

    private OrphanEntityCleanupPolicy() {
    }

    /**
     * Delete only true orphans: no remaining mentions and no user-assigned alias.
     * Entities kept solely via {@link AliasSource#USER} preserve manual identity in the album/graph.
     */
    public static boolean shouldDeleteOrphan(boolean hasRemainingMentions, boolean hasUserAlias) {
        return !hasRemainingMentions && !hasUserAlias;
    }
}
