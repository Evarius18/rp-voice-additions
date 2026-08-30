package com.evarius.rpvca.siren;

import java.util.Locale;

public enum SirenSignal {
    FIRE_ALARM("fire_alarm", "signal.rp-vca.siren.fire_alarm", "fire_alarm.mp3"),
    WARNING("warning", "signal.rp-vca.siren.warning", "warning.mp3"),
    ALL_CLEAR("all_clear", "signal.rp-vca.siren.all_clear", "all_clear.mp3"),
    TEST("test", "signal.rp-vca.siren.test", "test.mp3");

    private final String id;
    private final String translationKey;
    private final String resourceFile;

    SirenSignal(String id, String translationKey, String resourceFile) {
        this.id = id;
        this.translationKey = translationKey;
        this.resourceFile = resourceFile;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return translationKey;
    }

    public String resourcePath() {
        return "/assets/rp-vca/audio/siren/" + resourceFile;
    }

    public static SirenSignal fromId(String id) {
        if (id != null) {
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            for (SirenSignal signal : values()) {
                if (signal.id.equals(normalized)) {
                    return signal;
                }
            }
        }
        return null;
    }
}
