package com.evarius.rpvca.permissions;

import com.evarius.rpvca.config.CompatibilityConfig;
import com.evarius.rpvca.config.RadioConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadioPermissionResolverTest {
    private final RadioPermissionResolver resolver = new RadioPermissionResolver(null, new CompatibilityConfig());

    @Test
    void terraNexusMembershipKeysGrantMappedChannelsOnly() {
        assertTrue(resolver.hasAccess(Set.of("FW"), channel("2000")));
        assertTrue(resolver.hasAccess(Set.of("Feuerwehr"), channel("2012")));
        assertFalse(resolver.hasAccess(Set.of("FW"), channel("2013")));

        assertTrue(resolver.hasAccess(Set.of("RD"), channel("2011")));
        assertFalse(resolver.hasAccess(Set.of("RD"), channel("2012")));

        assertTrue(resolver.hasAccess(Set.of("POL"), channel("2013")));
        assertFalse(resolver.hasAccess(Set.of("POL"), channel("2020")));
    }

    @Test
    void scoreboardAndInstitutionKeysUseTheSameCaseInsensitiveRules() {
        assertTrue(resolver.hasAccess(Set.of("kats"), channel("2014")));
        assertTrue(resolver.hasAccess(Set.of("Stadtwerke"), channel("2020")));
        assertTrue(resolver.hasAccess(Set.of("abschleppdienst"), channel("2021")));
        assertTrue(resolver.hasAccess(Set.of("EFS Stadtwerke GmbH"), channel("2020")));
        assertTrue(resolver.hasAccess(Set.of("Kommunaler Abschleppdienst"), channel("2021")));
        assertFalse(resolver.hasAccess(Set.of("ACME Services"), channel("2021")));
    }

    @Test
    void directRequiredKeyWorksWithoutACompatibilityMapping() {
        RadioConfig.Channel custom = new RadioConfig.Channel("custom", "Custom", "custom_institution");
        assertTrue(resolver.hasAccess(Set.of("CUSTOM_INSTITUTION"), custom));
        assertFalse(resolver.hasAccess(Set.of("other"), custom));
    }

    private static RadioConfig.Channel channel(String id) {
        return new RadioConfig().channels.stream()
                .filter(channel -> channel.id.equals(id))
                .findFirst().orElseThrow();
    }
}
