package com.evarius.rpvca.api;

import java.util.UUID;

/** Server-authoritative result for call state changes. */
public record CallMutationResult(Status status, UUID callId) {
    public enum Status {
        RINGING, CONNECTED, DECLINED, CANCELLED, ENDED,
        NOT_FOUND, STALE_CALL, BUSY, UNREACHABLE, NOT_ALLOWED, INVALID_NUMBER
    }

    public boolean successful() {
        return status == Status.RINGING || status == Status.CONNECTED
                || status == Status.DECLINED || status == Status.CANCELLED || status == Status.ENDED;
    }

    public static CallMutationResult of(Status status, UUID callId) {
        return new CallMutationResult(status, callId);
    }
}
