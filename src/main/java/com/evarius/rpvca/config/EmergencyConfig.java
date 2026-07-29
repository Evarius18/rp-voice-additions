package com.evarius.rpvca.config;

import java.util.ArrayList;
import java.util.List;

public final class EmergencyConfig {
    public boolean enabled = true;
    public List<Number> numbers = new ArrayList<>(List.of(
            new Number("110", "Polizei", "police"),
            new Number("112", "Feuerwehr und Rettungsdienst", "emergency")
    ));

    public static final class Number {
        public String number;
        public String displayName;
        public String responderTeam;

        public Number() {
        }

        public Number(String number, String displayName, String responderTeam) {
            this.number = number;
            this.displayName = displayName;
            this.responderTeam = responderTeam;
        }
    }
}
