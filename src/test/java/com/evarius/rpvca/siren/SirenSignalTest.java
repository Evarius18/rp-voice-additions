package com.evarius.rpvca.siren;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SirenSignalTest {
    @Test
    void allConfiguredSignalsResolveAndShipAudio() {
        assertEquals(4, Arrays.stream(SirenSignal.values()).map(SirenSignal::id).distinct().count());
        for (SirenSignal signal : SirenSignal.values()) {
            assertEquals(signal, SirenSignal.fromId(signal.id().toUpperCase()));
            assertNotNull(SirenSignalTest.class.getResourceAsStream(signal.resourcePath()), signal.id());
        }
    }
}
