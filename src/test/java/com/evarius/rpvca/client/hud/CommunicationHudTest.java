package com.evarius.rpvca.client.hud;

import com.evarius.rpvca.config.HudConfig;
import com.evarius.rpvca.network.CommunicationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunicationHudTest {
    @Test
    void hiddenInactiveRadioRequiresCompleteActiveState() {
        HudConfig config = new HudConfig();
        config.showRadio = true;
        config.hideInactiveRadio = true;
        CommunicationStatus status = new CommunicationStatus();

        assertFalse(CommunicationHud.shouldRenderRadioHud(status, config));
        status.radioEnabled = true;
        status.radioPoweredOn = true;
        status.radioChannel = "1";
        assertFalse(CommunicationHud.shouldRenderRadioHud(status, config));
        status.radioAvailable = true;
        assertTrue(CommunicationHud.shouldRenderRadioHud(status, config));
        status.radioAvailable = false;
        assertFalse(CommunicationHud.shouldRenderRadioHud(status, config));
    }

    @Test
    void inactiveRadioCanBeShownWhenConfigured() {
        HudConfig config = new HudConfig();
        config.showRadio = true;
        config.hideInactiveRadio = false;
        CommunicationStatus status = new CommunicationStatus();
        assertFalse(CommunicationHud.shouldRenderRadioHud(status, config));
        status.radioAvailable = true;
        assertTrue(CommunicationHud.shouldRenderRadioHud(status, config));
        config.showRadio = false;
        assertFalse(CommunicationHud.shouldRenderRadioHud(status, config));
    }
}
