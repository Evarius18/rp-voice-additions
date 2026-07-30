package com.evarius.rpvca.speech;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SpeechTranslationsTest {
    @Test
    void germanAndEnglishResourcesContainLocalizedModeNames() {
        assertModes("de_de.json", Map.of(
                "whisper", "Flüstern", "quiet", "Leise", "normal", "Normal",
                "shout", "Rufen", "scream", "Schreien"));
        assertModes("en_us.json", Map.of(
                "whisper", "Whisper", "quiet", "Quiet", "normal", "Normal",
                "shout", "Shout", "scream", "Scream"));
    }

    private static void assertModes(String file, Map<String, String> expected) {
        var stream = SpeechTranslationsTest.class.getResourceAsStream("/assets/rp-vca/lang/" + file);
        assertNotNull(stream, file);
        JsonObject json = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        expected.forEach((id, value) -> assertEquals(
                value, json.get("speech_mode.rpvca." + id).getAsString()));
    }
}
