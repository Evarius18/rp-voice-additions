package com.evarius.rpvca.client.compatibility.svc;

import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

/**
 * Public-API-only bridge to Simple Voice Chat's native whisper state.
 *
 * <p>SVC API 2.6.20 exposes no setter for the local microphone whisper flag. RP-VCA
 * therefore observes native state and never reflects into SVC internals or simulates keys.</p>
 */
public final class SvcWhisperCompatibility {
    private static volatile VoicechatClientApi api;
    private static volatile boolean connected;

    private SvcWhisperCompatibility() {
    }

    public static void register(EventRegistration registration) {
        registration.registerEvent(ClientVoicechatConnectionEvent.class, event -> {
            api = event.getVoicechat();
            connected = event.isConnected();
        });
    }

    public static boolean isAvailable() {
        return connected && api != null;
    }

    public static boolean isNativeWhispering() {
        VoicechatClientApi current = api;
        return connected && current != null && current.isWhispering();
    }

    public static boolean canProgrammaticallySetWhisper() {
        return false;
    }
}
