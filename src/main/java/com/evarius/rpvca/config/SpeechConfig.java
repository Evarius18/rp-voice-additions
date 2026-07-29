package com.evarius.rpvca.config;

import java.util.ArrayList;
import java.util.List;

public final class SpeechConfig {
    public boolean enabled = true;
    public String defaultMode = "normal";
    public List<Mode> modes = new ArrayList<>(List.of(
            new Mode("quiet", "Leise", 8.0F),
            new Mode("normal", "Normal", 32.0F),
            new Mode("shout", "Rufen", 64.0F),
            new Mode("scream", "Schreien", 96.0F)
    ));

    public static final class Mode {
        public String id;
        public String displayName;
        public float distance;

        public Mode() {
        }

        public Mode(String id, String displayName, float distance) {
            this.id = id;
            this.displayName = displayName;
            this.distance = distance;
        }
    }
}
