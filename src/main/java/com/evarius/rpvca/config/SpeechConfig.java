package com.evarius.rpvca.config;

import java.util.ArrayList;
import java.util.List;

public final class SpeechConfig {
    public boolean enabled = true;
    public String defaultMode = "normal";
    public List<String> modeOrder = new ArrayList<>(List.of(
            "WHISPER", "QUIET", "NORMAL", "SHOUT", "SCREAM"
    ));
    public List<Mode> modes = new ArrayList<>(List.of(
            new Mode("quiet", 8.0F),
            new Mode("normal", 32.0F),
            new Mode("shout", 64.0F),
            new Mode("scream", 96.0F)
    ));

    public static final class Mode {
        public String id;
        public float distance;

        public Mode() {
        }

        public Mode(String id, float distance) {
            this.id = id;
            this.distance = distance;
        }
    }
}
