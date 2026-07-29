package com.evarius.rpvca.client.audio;

import com.evarius.rpvca.client.ClientConfigStore;
import com.evarius.rpvca.voice.VoiceChannelIds;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.minecraft.client.MinecraftClient;

/**
 * Applies the radio GUI's client-local gain only to RP-VCA radio channels.
 * Simple Voice Chat's category volume remains available as an additional master control.
 */
public final class ClientRadioAudio {
    private ClientRadioAudio() {
    }

    public static void register(EventRegistration registration) {
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, ClientRadioAudio::onStaticSound);
    }

    private static void onStaticSound(ClientReceiveSoundEvent.StaticSound event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.world.getPlayers().stream()
                .noneMatch(player -> VoiceChannelIds.forPlayer("radio", player.getUuid()).equals(event.getId()))) {
            return;
        }
        double gain = ClientConfigStore.get().radioVolume;
        if (gain == 1.0D || event.getRawAudio().length == 0) {
            return;
        }
        short[] input = event.getRawAudio();
        short[] output = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (short) Math.clamp(Math.round(input[i] * gain), Short.MIN_VALUE, Short.MAX_VALUE);
        }
        event.setRawAudio(output);
    }
}
