package com.evarius.rpvca.speech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeechModeTest {
    @Test
    void mapsStableIdsToClientTranslationKeys() {
        assertEquals("speech_mode.rpvca.whisper", SpeechMode.translationKey("WHISPER"));
        assertEquals("speech_mode.rpvca.quiet", SpeechMode.translationKey("quiet"));
        assertEquals("speech_mode.rpvca.normal", SpeechMode.translationKey("normal"));
        assertEquals("speech_mode.rpvca.shout", SpeechMode.translationKey("shout"));
        assertEquals("speech_mode.rpvca.scream", SpeechMode.translationKey("SCREAM"));
    }

    @Test
    void invalidNetworkValueFallsBackSafely() {
        assertEquals("speech_mode.rpvca.normal", SpeechMode.translationKey("invalid"));
    }
}
