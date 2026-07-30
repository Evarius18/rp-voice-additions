package com.evarius.rpvca.config;

import java.util.ArrayList;
import java.util.List;

public final class EmergencyConfig {
    public boolean enabled = true;
    public List<Number> numbers = new ArrayList<>(List.of(
            new Number("110", "Polizei", "police", "police"),
            new Number("112", "Feuerwehr und Rettungsdienst", "emergency",
                    "emergency", "fire", "medical")
    ));

    public static final class Number {
        public String number;
        public String displayName;
        public String responderTeam;
        public List<String> responderKeys = new ArrayList<>();

        public Number() {
        }

        public Number(String number, String displayName, String responderTeam, String... responderKeys) {
            this.number = number;
            this.displayName = displayName;
            this.responderTeam = responderTeam;
            if (responderKeys != null) {
                java.util.Collections.addAll(this.responderKeys, responderKeys);
            }
            if (this.responderKeys.isEmpty() && responderTeam != null && !responderTeam.isBlank()) {
                this.responderKeys.add(responderTeam);
            }
        }
    }
}
