package com.evarius.rpvca.api;

/** Stable result contract for server-side contact mutations. */
public enum ContactMutationResult {
    CREATED,
    UPDATED,
    REMOVED,
    NOT_FOUND,
    INVALID_NAME,
    INVALID_NUMBER,
    DUPLICATE_NUMBER,
    LIMIT_REACHED,
    NOT_ALLOWED,
    STORAGE_ERROR;

    public boolean successful() {
        return this == CREATED || this == UPDATED || this == REMOVED;
    }
}
