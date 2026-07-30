package com.evarius.rpvca.voice;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.RpVoiceServices;
import com.evarius.rpvca.service.PhoneService;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RpVoicechatPlugin implements VoicechatPlugin {
    private static final String PHONE_CATEGORY = "rp_phone";
    private static final String RADIO_CATEGORY = "rp_radio";

    private final Map<UUID, StaticAudioChannel> phoneChannels = new ConcurrentHashMap<>();
    private final Map<UUID, EntityAudioChannel> speakerChannels = new ConcurrentHashMap<>();
    private final Map<UUID, StaticAudioChannel> radioChannels = new ConcurrentHashMap<>();
    private volatile VoicechatServerApi api;

    @Override
    public String getPluginId() {
        return RpVoiceAddon.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onVoicechatStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> clearChannels());
        registration.registerEvent(VoiceDistanceEvent.class, this::onVoiceDistance);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            com.evarius.rpvca.client.audio.ClientRadioAudio.register(registration);
            com.evarius.rpvca.client.compatibility.svc.SvcWhisperCompatibility.register(registration);
        }
    }

    private void onVoicechatStarted(VoicechatServerStartedEvent event) {
        api = event.getVoicechat();
        api.registerVolumeCategory(api.volumeCategoryBuilder()
                .setId(PHONE_CATEGORY)
                .setName("Telefon")
                .setDescription("Telefonanrufe von RP Voice Additions")
                .build());
        api.registerVolumeCategory(api.volumeCategoryBuilder()
                .setId(RADIO_CATEGORY)
                .setName("Funk")
                .setDescription("Funkübertragungen von RP Voice Additions")
                .build());
        RpVoiceAddon.LOGGER.info("Simple Voice Chat Routing ist bereit");
    }

    private void onVoiceDistance(VoiceDistanceEvent event) {
        RpVoiceServices services = RpVoiceServices.get();
        if (services == null || event.getSenderConnection() == null
                || !services.configs().speech().enabled) {
            return;
        }
        UUID senderId = event.getSenderConnection().getPlayer().getUuid();
        services.speech().observeNativeWhisper(senderId, event.getPacket().isWhispering());
        if (event.getPacket().isWhispering() || services.speech().isWhisper(senderId)) {
            // Native SVC packets retain SVC's own whisper distance. A selected-but-not-native
            // whisper never receives a synthetic low-distance fallback.
            return;
        }
        event.setDistance(services.speech().mode(senderId).distance);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        RpVoiceServices services = RpVoiceServices.get();
        VoicechatServerApi voiceApi = api;
        if (services == null || voiceApi == null || event.getSenderConnection() == null) {
            return;
        }
        UUID senderId = event.getSenderConnection().getPlayer().getUuid();
        ServerPlayerEntity sender = services.server().getPlayerManager().getPlayer(senderId);
        if (sender == null) {
            return;
        }
        routePhone(event, services, voiceApi, sender);
        routeRadio(event, services, voiceApi, sender);
    }

    private void routePhone(MicrophonePacketEvent event, RpVoiceServices services,
                            VoicechatServerApi voiceApi, ServerPlayerEntity sender) {
        PhoneService.CallSession call = services.phones().activeCall(sender.getUuid());
        if (call == null || !services.phones().canRouteAudio(sender)) {
            return;
        }
        UUID receiverId = call.other(sender.getUuid());
        ServerPlayerEntity receiver = services.server().getPlayerManager().getPlayer(receiverId);
        if (receiver == null || !services.phones().canRouteAudio(receiver)) {
            return;
        }
        if (services.phones().isSpeakerEnabled(receiverId)) {
            EntityAudioChannel channel = speakerChannels.compute(receiverId, (id, existing) -> {
                de.maxhenkel.voicechat.api.Entity entity = voiceApi.fromEntity(receiver);
                if (existing == null || existing.isClosed()) {
                    EntityAudioChannel created = voiceApi.createEntityAudioChannel(
                            VoiceChannelIds.forPlayer("phone-speaker", id), entity);
                    if (created != null) {
                        created.setCategory(PHONE_CATEGORY);
                        created.setDistance(services.configs().phone().speakerDistance);
                    }
                    return created;
                }
                existing.updateEntity(entity);
                existing.setDistance(services.configs().phone().speakerDistance);
                return existing;
            });
            if (channel != null) {
                channel.send(event.getPacket());
            }
        } else {
            StaticAudioChannel channel = phoneChannels.computeIfAbsent(sender.getUuid(), id -> {
                StaticAudioChannel created = voiceApi.createStaticAudioChannel(
                        VoiceChannelIds.forPlayer("phone-private", id));
                if (created != null) {
                    created.setCategory(PHONE_CATEGORY);
                    created.setBypassGroupIsolation(true);
                }
                return created;
            });
            if (channel != null) {
                channel.clearTargets();
                de.maxhenkel.voicechat.api.VoicechatConnection connection = voiceApi.getConnectionOf(receiverId);
                if (connection != null) {
                    channel.addTarget(connection);
                    channel.send(event.getPacket());
                }
            }
        }
    }

    private void routeRadio(MicrophonePacketEvent event, RpVoiceServices services,
                            VoicechatServerApi voiceApi, ServerPlayerEntity sender) {
        if (!services.radios().canTransmit(sender)) {
            return;
        }
        StaticAudioChannel channel = radioChannels.computeIfAbsent(sender.getUuid(), id -> {
            StaticAudioChannel created = voiceApi.createStaticAudioChannel(VoiceChannelIds.forPlayer("radio", id));
            if (created != null) {
                created.setCategory(RADIO_CATEGORY);
                created.setBypassGroupIsolation(true);
            }
            return created;
        });
        if (channel == null) {
            return;
        }
        channel.clearTargets();
        for (ServerPlayerEntity receiver : services.server().getPlayerManager().getPlayerList()) {
            if (!services.radios().canReceive(sender, receiver)) {
                continue;
            }
            de.maxhenkel.voicechat.api.VoicechatConnection connection = voiceApi.getConnectionOf(receiver.getUuid());
            if (connection != null) {
                channel.addTarget(connection);
            }
        }
        channel.send(event.getPacket());
    }

    private void clearChannels() {
        phoneChannels.values().forEach(StaticAudioChannel::flush);
        speakerChannels.values().forEach(EntityAudioChannel::flush);
        radioChannels.values().forEach(StaticAudioChannel::flush);
        phoneChannels.clear();
        speakerChannels.clear();
        radioChannels.clear();
        api = null;
    }
}
