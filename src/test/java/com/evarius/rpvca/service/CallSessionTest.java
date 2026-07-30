package com.evarius.rpvca.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CallSessionTest {
    @Test
    void resolvesTheOtherParticipant() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PhoneService.CallSession session = new PhoneService.CallSession(first, second, "test");

        assertEquals(second, session.other(first));
        assertEquals(first, session.other(second));
        assertEquals("", session.otherNumber(first));
        assertEquals("", session.otherNumber(second));
        assertThrows(IllegalArgumentException.class, () -> session.other(UUID.randomUUID()));
    }
}
