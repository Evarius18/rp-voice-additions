package com.evarius.rpvca.client;

import com.evarius.rpvca.network.DeviceActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientActions {
    private ClientActions() {
    }

    public static void send(String action) {
        send(action, "");
    }

    public static void send(String action, String value) {
        if (ClientPlayNetworking.canSend(DeviceActionPayload.ID)) {
            ClientPlayNetworking.send(new DeviceActionPayload(action, value == null ? "" : value));
        }
    }
}
