package com.evarius.rpvca.network;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.RpVoiceServices;
import com.evarius.rpvca.compatibility.PhoneIntegration;
import com.evarius.rpvca.config.EmergencyConfig;
import com.evarius.rpvca.config.RadioConfig;
import com.evarius.rpvca.config.SpeechConfig;
import com.evarius.rpvca.service.PhoneService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class CommunicationNetworking {
    private static final Gson GSON = new GsonBuilder().create();

    private CommunicationNetworking() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(DeviceActionPayload.ID, DeviceActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenDevicePayload.ID, OpenDevicePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatusPayload.ID, StatusPayload.CODEC);
    }

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(DeviceActionPayload.ID, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
    }

    public static void openDevice(ServerPlayerEntity player, String device) {
        sync(player);
        ServerPlayNetworking.send(player, new OpenDevicePayload(device));
    }

    public static void sync(ServerPlayerEntity player) {
        RpVoiceServices services = RpVoiceServices.get();
        if (services == null || !ServerPlayNetworking.canSend(player, StatusPayload.ID)) {
            return;
        }
        CommunicationStatus status = createStatus(services, player);
        ServerPlayNetworking.send(player, new StatusPayload(GSON.toJson(status)));
    }

    private static void handle(ServerPlayerEntity player, DeviceActionPayload payload) {
        RpVoiceServices services = RpVoiceServices.get();
        if (services == null) {
            return;
        }
        String action = payload.action();
        String value = payload.value();
        switch (action) {
            case "status_request" -> {
            }
            case "speech_cycle" -> services.speech().cycle(player.getUuid());
            case "speech_set" -> services.speech().setMode(player.getUuid(), value);
            case "phone_call" -> services.phones().call(player, value);
            case "phone_answer" -> services.phones().answer(player);
            case "phone_decline" -> services.phones().decline(player);
            case "phone_hangup" -> services.phones().hangup(player);
            case "phone_speaker" -> services.phones().toggleSpeaker(player);
            case "radio_tune" -> services.radios().tune(player, value);
            case "radio_tx" -> services.radios().setTransmitting(player, Boolean.parseBoolean(value));
            case "radio_toggle" -> services.radios().toggleTransmit(player);
            case "radio_off" -> services.radios().off(player);
            case "compat_open" -> services.compatibility().phoneIntegrations().stream()
                    .filter(app -> app.id().equals(value) && app.available(player))
                    .findFirst().ifPresent(app -> app.open(player));
            default -> RpVoiceAddon.LOGGER.warn("Unbekannte Geräteaktion '{}' von {}", action,
                    player.getGameProfile().getName());
        }
        sync(player);
    }

    private static CommunicationStatus createStatus(RpVoiceServices services, ServerPlayerEntity player) {
        CommunicationStatus status = new CommunicationStatus();
        SpeechConfig.Mode speech = services.speech().mode(player.getUuid());
        status.speechMode = speech.id;
        status.speechDisplayName = speech.displayName;
        status.speechDistance = speech.distance;

        PhoneService.ClientView phone = services.phones().clientView(player);
        status.phoneState = phone.state();
        status.phonePeer = phone.peer();
        status.phoneNumber = phone.number();
        status.phoneSpeaker = phone.speaker();
        status.phoneCoverage = phone.coverage();
        status.phoneNotice = phone.notice();
        status.contacts.putAll(phone.contacts());
        for (EmergencyConfig.Number number : services.configs().emergency().numbers) {
            status.emergencyNumbers.add(new CommunicationStatus.NamedEntry(number.number, number.displayName));
        }

        String channelId = services.radios().tunedChannel(player.getUuid());
        RadioConfig.Channel tuned = channelId == null ? null : services.radios().channel(channelId);
        status.radioChannel = tuned == null ? "" : tuned.id;
        status.radioDisplayName = tuned == null ? "" : tuned.displayName;
        status.radioTransmitting = services.radios().isTransmitting(player.getUuid());
        for (RadioConfig.Channel channel : services.radios().accessibleChannels(player)) {
            status.radioChannels.add(new CommunicationStatus.NamedEntry(channel.id, channel.displayName));
        }
        for (PhoneIntegration app : services.compatibility().phoneIntegrations()) {
            if (app.available(player)) {
                status.phoneApps.add(new CommunicationStatus.NamedEntry(app.id(), app.title()));
            }
        }
        return status;
    }
}
