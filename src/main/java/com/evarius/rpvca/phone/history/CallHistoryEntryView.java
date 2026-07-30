package com.evarius.rpvca.phone.history;

import java.util.UUID;

/** Immutable public snapshot; persistent objects are never exposed. */
public record CallHistoryEntryView(
        UUID entryId,
        CallDirection direction,
        CallHistoryStatus status,
        String localNumber,
        String remoteNumber,
        String remoteDisplayName,
        long startedAt,
        long answeredAt,
        long endedAt,
        long durationSeconds
) {
}
