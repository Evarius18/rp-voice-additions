package com.evarius.rpvca.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TowerTypeTest {
    @Test
    void missingLegacyTypeMigratesToCellular() {
        assertEquals(TowerRegistry.TowerType.CELLULAR, TowerRegistry.TowerType.fromId(null));
        assertEquals(TowerRegistry.TowerType.CELLULAR, TowerRegistry.TowerType.fromId("unknown"));
    }

    @Test
    void digitalRadioTypeRoundTripsCaseInsensitively() {
        assertEquals(TowerRegistry.TowerType.DIGITAL_RADIO,
                TowerRegistry.TowerType.fromId("DIGITAL_RADIO"));
        assertEquals("digital_radio", TowerRegistry.TowerType.DIGITAL_RADIO.id());
    }
}
