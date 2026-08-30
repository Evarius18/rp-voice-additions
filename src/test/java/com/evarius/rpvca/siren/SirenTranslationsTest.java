package com.evarius.rpvca.siren;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SirenTranslationsTest {
    @Test
    void germanAndEnglishContainEverySirenSignalAndAction() {
        assertLanguage("de_de.json");
        assertLanguage("en_us.json");
    }

    private static void assertLanguage(String language) {
        var stream = SirenTranslationsTest.class.getResourceAsStream("/assets/rp-vca/lang/" + language);
        assertNotNull(stream, language);
        JsonObject json = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        for (SirenSignal signal : SirenSignal.values()) {
            assertTrue(json.has(signal.translationKey()), language + ": " + signal.translationKey());
        }
        assertTrue(json.has("block.rp-vca.siren_controller"));
        assertTrue(json.has("gui.rp-vca.siren.live_start"));
        assertTrue(json.has("gui.rp-vca.siren.record_start"));
        assertTrue(json.has("notice.rp-vca.siren.voice_chat_unavailable"));
        assertTrue(json.has("notice.rp-vca.siren.programmer_required"));
        assertTrue(json.has("item.rp-vca.siren_programmer"));
        assertNotNull(SirenTranslationsTest.class.getResourceAsStream(
                "/assets/rp-vca/items/siren_programmer.json"));
        assertNotNull(SirenTranslationsTest.class.getResourceAsStream(
                "/assets/rp-vca/models/item/siren_programmer.json"));
    }
}
