package com.evarius.rpvca.config;

import java.util.ArrayList;
import java.util.List;

public final class RadioConfig {
    public boolean enabled = true;
    public boolean requireRadioItem = true;
    public boolean requireSameDimension = true;
    public double maximumRange = 0.0D;
    public List<Channel> channels = new ArrayList<>(List.of(
            new Channel("1", "Allgemein", ""),
            new Channel("police", "Polizei", "police"),
            new Channel("fire", "Feuerwehr", "fire"),
            new Channel("medical", "Rettungsdienst", "medical")
    ));

    public static final class Channel {
        public String id;
        public String displayName;
        public String requiredTeam;

        public Channel() {
        }

        public Channel(String id, String displayName, String requiredTeam) {
            this.id = id;
            this.displayName = displayName;
            this.requiredTeam = requiredTeam;
        }
    }
}
