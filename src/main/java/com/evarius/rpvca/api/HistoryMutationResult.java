package com.evarius.rpvca.api;

/** Stable result contract for call-history deletion operations. */
public enum HistoryMutationResult {
    REMOVED,
    CLEARED,
    NOT_FOUND,
    NOT_ALLOWED,
    INVALID_REQUEST,
    STORAGE_ERROR;

    public boolean successful() {
        return this == REMOVED || this == CLEARED;
    }
}
