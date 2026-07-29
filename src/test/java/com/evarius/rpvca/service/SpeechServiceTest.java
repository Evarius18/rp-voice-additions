package com.evarius.rpvca.service;

import com.evarius.rpvca.config.SpeechConfig;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpeechServiceTest {
    @Test
    void usesDefaultAndChangesToConfiguredMode() {
        SpeechService service = new SpeechService(new SpeechConfig());
        UUID player = UUID.randomUUID();

        assertEquals("normal", service.mode(player).id);
        assertEquals("shout", service.setMode(player, "SHOUT").id);
        assertEquals(64.0F, service.mode(player).distance);
        assertNull(service.setMode(player, "missing"));
    }

    @Test
    void cyclesThroughModes() {
        SpeechService service = new SpeechService(new SpeechConfig());
        UUID player = UUID.randomUUID();

        assertEquals("shout", service.cycle(player).id);
        assertEquals("scream", service.cycle(player).id);
        assertEquals("quiet", service.cycle(player).id);
    }
}
