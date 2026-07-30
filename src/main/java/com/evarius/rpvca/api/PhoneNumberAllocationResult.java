package com.evarius.rpvca.api;

public record PhoneNumberAllocationResult(Status status, String normalizedNumber, String formattedNumber) {
    public enum Status {
        CREATED,
        ALREADY_ASSIGNED,
        NUMBER_SPACE_EXHAUSTED,
        INVALID_CONFIGURATION,
        STORAGE_ERROR
    }

    public boolean successful() {
        return status == Status.CREATED || status == Status.ALREADY_ASSIGNED;
    }
}
