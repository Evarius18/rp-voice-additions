package com.evarius.rpvca;

import com.evarius.rpvca.client.ClientActions;
import com.evarius.rpvca.client.ClientConfigStore;
import com.evarius.rpvca.client.ClientCommunicationState;
import com.evarius.rpvca.client.gui.PhoneScreen;
import com.evarius.rpvca.client.gui.RadioScreen;
import com.evarius.rpvca.client.hud.CommunicationHud;
import com.evarius.rpvca.client.keybind.CommunicationKeybinds;
import com.evarius.rpvca.network.OpenDevicePayload;
import com.evarius.rpvca.network.StatusPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class RpVoiceAddonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientConfigStore.load();
        ClientPlayNetworking.registerGlobalReceiver(StatusPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    ClientCommunicationState.update(payload.json());
                    com.evarius.rpvca.client.gui.IncomingCallController.onStatus(
                            context.client(), ClientCommunicationState.get());
                }));
        ClientPlayNetworking.registerGlobalReceiver(OpenDevicePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if ("phone".equals(payload.device())) {
                        context.client().setScreen(new PhoneScreen());
                    } else if ("radio".equals(payload.device())) {
                        context.client().setScreen(new RadioScreen());
                    }
                }));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientActions.send("status_request"));
        CommunicationKeybinds.register();
        CommunicationHud.register();
    }
}
