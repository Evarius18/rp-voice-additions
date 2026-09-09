package com.evarius.rpvca.client.gui;

import com.evarius.rpvca.network.CommunicationStatus;
import net.minecraft.client.Minecraft;

/** Opens and closes the lightweight call card only from confirmed server state. */
public final class IncomingCallController {
    private IncomingCallController() {
    }

    public static void onStatus(Minecraft client, CommunicationStatus status) {
        if (client.screen instanceof IncomingCallScreen) {
            if (!"incoming".equals(status.phoneState) || status.phoneCallId.isBlank()) {
                client.setScreen(null);
            }
            return;
        }
        if (client.screen == null && "incoming".equals(status.phoneState)
                && !status.phoneCallId.isBlank()) {
            client.setScreen(new IncomingCallScreen(status.phoneCallId));
        }
    }
}
