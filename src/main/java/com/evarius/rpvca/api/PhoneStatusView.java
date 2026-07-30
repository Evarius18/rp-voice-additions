package com.evarius.rpvca.api;

import java.util.UUID;

/** Immutable server-side status snapshot for external phone UIs. */
public record PhoneStatusView(
        String state,
        UUID callId,
        String peer,
        String peerNumber,
        boolean savedContact,
        String number,
        boolean speaker,
        boolean coverage,
        boolean phoneHeld,
        String notice
) {
}
