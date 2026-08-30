package com.evarius.rpvca.api;

import java.util.UUID;

/** Immutable, non-sensitive controller information exposed to optional integrations. */
public record SirenControllerView(
        UUID controllerId,
        String name,
        String dimension,
        int x,
        int y,
        int z,
        int linkedSirens,
        int scheduledAlarms,
        boolean active
) {
}
