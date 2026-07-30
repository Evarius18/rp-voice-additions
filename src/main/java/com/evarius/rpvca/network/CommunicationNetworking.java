package com.evarius.rpvca.network;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.RpVoiceServices;
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
            case "open_phone_request" -> openHeldPhone(services, player);
            case "speech_cycle" -> services.speech().cycle(player.getUuid());
            case "speech_set" -> services.speech().setMode(player.getUuid(), value);
            case "phone_call" -> services.phones().call(player, value);
            case "phone_answer" -> parseUuid(value).ifPresent(id -> services.phones().acceptCall(player, id));
            case "phone_decline" -> parseUuid(value).ifPresent(id -> services.phones().declineCall(player, id));
            case "phone_hangup" -> services.phones().hangup(player);
            case "phone_speaker" -> services.phones().toggleSpeaker(player);
            case "contact_upsert" -> {
                String[] fields = value.split("\\t", 2);
                if (fields.length == 2) {
                    services.phones().upsertContact(player, fields[0], fields[1]);
                }
            }
            case "contact_remove" -> services.phones().removeContact(player, value);
            case "history_remove" -> {
                try {
                    services.phones().removeHistoryEntry(player, java.util.UUID.fromString(value));
                } catch (IllegalArgumentException ignored) {
                    RpVoiceAddon.LOGGER.warn("Ungültige Historien-ID von {}", player.getGameProfile().getName());
                }
            }
            case "history_clear" -> services.phones().clearOwnCallHistory(player);
            case "radio_tune" -> services.radios().tune(player, value);
            case "radio_tx" -> services.radios().setTransmitting(player, Boolean.parseBoolean(value));
            case "radio_toggle" -> services.radios().toggleTransmit(player);
            case "radio_off" -> services.radios().off(player);
            case "compat_open" -> {
                com.evarius.rpvca.api.PhoneApplicationProvider app =
                        services.compatibility().phoneIntegrations().get(value);
                if (app != null && app.available(player)) app.open(player);
            }
            default -> RpVoiceAddon.LOGGER.warn("Unbekannte Geräteaktion '{}' von {}", action,
                    player.getGameProfile().getName());
        }
        sync(player);
    }

    private static CommunicationStatus createStatus(RpVoiceServices services, ServerPlayerEntity player) {
        CommunicationStatus status = new CommunicationStatus();
        SpeechConfig.Mode speech = services.speech().mode(player.getUuid());
        status.speechMode = speech.id;
        status.speechTranslationKey = com.evarius.rpvca.speech.SpeechMode.translationKey(speech.id);
        status.speechDistance = speech.distance;

        PhoneService.ClientView phone = services.phones().clientView(player);
        status.phoneState = phone.state();
        status.phoneCallId = phone.callId() == null ? "" : phone.callId().toString();
        status.phonePeer = phone.peer();
        status.phonePeerNumber = phone.peerNumber();
        status.phonePeerSavedContact = phone.savedContact();
        status.phoneNumber = phone.number();
        status.phoneSpeaker = phone.speaker();
        status.phoneCoverage = phone.coverage();
        status.phoneHeld = phone.phoneHeld();
        status.phoneNotice = phone.notice();
        status.contacts.putAll(phone.contacts());
        status.callHistory.addAll(phone.history());
        for (EmergencyConfig.Number number : services.configs().emergency().numbers) {
            status.emergencyNumbers.add(new CommunicationStatus.NamedEntry(number.number, number.displayName));
        }

        String channelId = services.radios().tunedChannel(player.getUuid());
        RadioConfig.Channel tuned = channelId == null ? null : services.radios().channel(channelId);
        status.radioChannel = tuned == null ? "" : tuned.id;
        status.radioDisplayName = tuned == null ? "" : tuned.displayName;
        status.radioTransmitting = services.radios().isTransmitting(player.getUuid());
        status.radioAvailable = services.radios().hasRadioDevice(player);
        status.radioEnabled = services.configs().radio().enabled;
        status.radioPoweredOn = tuned != null;
        for (RadioConfig.Channel channel : services.radios().accessibleChannels(player)) {
            status.radioChannels.add(new CommunicationStatus.NamedEntry(channel.id, channel.displayName));
        }
        for (java.util.Map.Entry<String, com.evarius.rpvca.api.PhoneApplicationProvider> entry
                : services.compatibility().phoneIntegrations().entrySet()) {
            if (entry.getValue().available(player)) {
                status.phoneApps.add(new CommunicationStatus.NamedEntry(
                        entry.getKey(), entry.getValue().title()));
            }
        }
        return status;
    }

    private static java.util.Optional<java.util.UUID> parseUuid(String value) {
        try {
            return java.util.Optional.of(java.util.UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return java.util.Optional.empty();
        }
    }

    private static void openHeldPhone(RpVoiceServices services, ServerPlayerEntity player) {
        java.util.Optional<com.evarius.rpvca.item.ResolvedPhoneDevice> held =
                services.deviceItems().resolveHeldPhone(player, services.configs().phone());
        if (held.isEmpty()) {
            services.phones().showNotice(player, "notice.rp-vca.phone.must_be_held");
            return;
        }
        com.evarius.rpvca.item.ResolvedPhoneDevice device = held.get();
        if (device.capabilities().openDefaultRpVcaScreen()) {
            openDevice(player, "phone");
            return;
        }
        com.evarius.rpvca.api.PhoneApplicationProvider provider =
                services.compatibility().phoneIntegrations().get(device.screenProviderId());
        if (provider != null && provider.available(player) && provider.open(player)) {
            return;
        }
        services.phones().showNotice(player, "notice.rp-vca.phone.no_screen_provider");
    }
}
