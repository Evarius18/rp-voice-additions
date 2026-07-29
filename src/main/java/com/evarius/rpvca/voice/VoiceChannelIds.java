package com.evarius.rpvca.voice;

import com.evarius.rpvca.RpVoiceAddon;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable IDs shared by server routing and optional client-side audio processing. */
public final class VoiceChannelIds {
    private VoiceChannelIds() {
    }

    public static UUID forPlayer(String type, UUID playerId) {
        return UUID.nameUUIDFromBytes((RpVoiceAddon.MOD_ID + ":" + type + ":" + playerId)
                .getBytes(StandardCharsets.UTF_8));
    }
}
