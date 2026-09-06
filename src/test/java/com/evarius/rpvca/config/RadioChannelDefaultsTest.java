package com.evarius.rpvca.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadioChannelDefaultsTest {
    @Test
    void tmoChannelsHaveExpectedNamesAndFrequencies() {
        RadioConfig config = new RadioConfig();
        assertEquals(List.of("2000", "2010", "2011", "2012", "2013", "2014", "2020", "2021"),
                config.channels.stream().map(channel -> channel.id).toList());
        assertEquals(List.of("EFS-BOS", "EFS-Anruf", "EFS-RD", "EFS-FW", "EFS-POL", "EFS-KatS", "EFS-SW", "EFS-AC"),
                config.channels.stream().map(channel -> channel.displayName).toList());
        assertTrue(config.channels.stream().allMatch(channel -> !channel.requiredTeam.isBlank()));
    }

    @Test
    void everyTmoChannelIsGrantedByAtLeastOneInstitutionMapping() {
        CompatibilityConfig compatibility = new CompatibilityConfig();
        Set<String> granted = compatibility.institutionRadioChannels.values().stream()
                .flatMap(List::stream).collect(java.util.stream.Collectors.toSet());
        for (RadioConfig.Channel channel : new RadioConfig().channels) {
            assertTrue(granted.contains(channel.id), channel.id + " has no institution mapping");
        }
        assertEquals(List.of("2000", "2010", "2012"),
                compatibility.institutionRadioChannels.get("FW"));
        assertEquals(List.of("2000", "2010", "2011"),
                compatibility.institutionRadioChannels.get("RD"));
        assertEquals(List.of("2000", "2010", "2013"),
                compatibility.institutionRadioChannels.get("POL"));
        assertFalse(compatibility.institutionRadioChannels.get("SW").contains("2000"));
        assertFalse(compatibility.institutionRadioChannels.get("AC").contains("2000"));
    }
}
