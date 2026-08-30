package com.evarius.rpvca.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MastModelCompatibilityTest {
    private static final Map<String, Integer> MODEL_ELEMENT_COUNTS = Map.of(
            "mast", 9,
            "mast_basis", 10,
            "mast_sirene_zwei", 81,
            "mast_sirene_drei", 113,
            "mast_mobilfunk", 47,
            "mast_digitalfunk", 23);

    @Test
    void generatedMastModelsUseOnlyMinecraft1218ElementRotations() {
        MODEL_ELEMENT_COUNTS.forEach(MastModelCompatibilityTest::assertCompatibleModel);
    }

    private static void assertCompatibleModel(String modelName, int expectedElements) {
        String resource = "/assets/rp-vca/models/block/" + modelName + ".json";
        var stream = MastModelCompatibilityTest.class.getResourceAsStream(resource);
        assertNotNull(stream, resource);
        JsonObject model = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();

        assertFalse(model.has("format_version"), modelName + " contains source format metadata");
        assertFalse(model.has("groups"), modelName + " contains Blockbench groups");
        assertFalse(model.has("fabric:type"), modelName + " requires a custom model loader");
        assertTrue(model.getAsJsonObject("textures").has("particle"), modelName + " has no particle texture");

        JsonArray elements = model.getAsJsonArray("elements");
        assertTrue(elements.size() == expectedElements, modelName + " lost model elements");
        for (int index = 0; index < elements.size(); index++) {
            JsonObject element = elements.get(index).getAsJsonObject();
            assertBounds(modelName, index, element.getAsJsonArray("from"));
            assertBounds(modelName, index, element.getAsJsonArray("to"));
            if (!element.has("rotation")) {
                continue;
            }
            JsonObject rotation = element.getAsJsonObject("rotation");
            assertFalse(rotation.has("x") || rotation.has("y") || rotation.has("z"),
                    modelName + " element " + index + " still uses a 1.21.11 Euler rotation");
            assertTrue(rotation.has("angle") && rotation.has("axis") && rotation.has("origin"),
                    modelName + " element " + index + " has an incomplete classic rotation");
            assertTrue(Set.of("x", "y", "z").contains(rotation.get("axis").getAsString()),
                    modelName + " element " + index + " has an invalid rotation axis");
            assertTrue(Math.abs(rotation.get("angle").getAsDouble()) <= 45.000001,
                    modelName + " element " + index + " exceeds the 1.21.8 rotation range");
        }
    }

    private static void assertBounds(String modelName, int index, JsonArray coordinates) {
        assertTrue(coordinates.size() == 3, modelName + " element " + index + " has invalid bounds");
        coordinates.forEach(value -> assertTrue(
                value.getAsDouble() >= -16.000001 && value.getAsDouble() <= 32.000001,
                modelName + " element " + index + " exceeds the 1.21.8 coordinate range"));
    }
}
