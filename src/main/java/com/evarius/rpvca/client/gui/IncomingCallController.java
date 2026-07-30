package com.evarius.rpvca.client.gui;

import com.evarius.rpvca.network.CommunicationStatus;
import net.minecraft.client.MinecraftClient;

/** Opens and closes the lightweight call card only from confirmed server state. */
public final class IncomingCallController {
    private IncomingCallController() {
    }

    public static void onStatus(MinecraftClient client, CommunicationStatus status) {
        if (client.currentScreen instanceof IncomingCallScreen) {
            if (!"incoming".equals(status.phoneState) || status.phoneCallId.isBlank()) {
                client.setScreen(null);
            }
            return;
        }
        if (client.currentScreen == null && "incoming".equals(status.phoneState)
                && !status.phoneCallId.isBlank()) {
            client.setScreen(new IncomingCallScreen(status.phoneCallId));
        }
    }
}
