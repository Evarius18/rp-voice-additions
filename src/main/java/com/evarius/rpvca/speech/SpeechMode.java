package com.evarius.rpvca.speech;

import java.util.Locale;
import java.util.Optional;

/** Stable speech identifiers; presentation is always resolved by the client language. */
public enum SpeechMode {
    WHISPER("whisper"),
    QUIET("quiet"),
    NORMAL("normal"),
    SHOUT("shout"),
    SCREAM("scream");

    private final String id;

    SpeechMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "speech_mode.rpvca." + id;
    }

    public static Optional<SpeechMode> fromId(String id) {
        if (id == null) return Optional.empty();
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(values()).filter(mode -> mode.id.equals(normalized)).findFirst();
    }

    public static String translationKey(String id) {
        return fromId(id).map(SpeechMode::translationKey).orElse("speech_mode.rpvca.normal");
    }
}
