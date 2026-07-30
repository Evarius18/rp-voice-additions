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
        assertEquals("whisper", service.cycle(player).id);
        assertEquals("quiet", service.cycle(player).id);
    }

    @Test
    void followsNativeWhisperAndRestoresPreviousMode() {
        SpeechService service = new SpeechService(new SpeechConfig());
        UUID player = UUID.randomUUID();

        service.setMode(player, "shout");
        service.observeNativeWhisper(player, true);
        assertEquals("whisper", service.mode(player).id);

        service.observeNativeWhisper(player, false);
        assertEquals("shout", service.mode(player).id);
    }

    @Test
    void directWhisperSelectionRequiresNoSyntheticMode() {
        SpeechService service = new SpeechService(new SpeechConfig());
        UUID player = UUID.randomUUID();

        assertEquals("whisper", service.setMode(player, "WHISPER").id);
        assertEquals(0.0F, service.mode(player).distance);
        assertEquals("normal", service.setMode(player, "normal").id);
    }
}
