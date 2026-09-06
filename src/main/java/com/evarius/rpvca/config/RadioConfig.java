package com.evarius.rpvca.config;

import java.util.ArrayList;
import java.util.List;

public final class RadioConfig {
    public int configVersion = 0;
    public boolean enabled = true;
    public boolean requireRadioItem = true;
    public String deviceLocation = "INVENTORY";
    public boolean allowMainInventory = true;
    public boolean allowHotbar = true;
    public boolean allowOffHand = true;
    public boolean allowArmorSlots = false;
    public boolean requireSameDimension = true;
    public double maximumRange = 0.0D;
    public List<Channel> channels = defaultChannels();

    public static ArrayList<Channel> defaultChannels() {
        return new ArrayList<>(List.of(
                new Channel("2000", "EFS-BOS", "BOS"),
                new Channel("2010", "EFS-Anruf", "ANRUF"),
                new Channel("2011", "EFS-RD", "RD"),
                new Channel("2012", "EFS-FW", "FW"),
                new Channel("2013", "EFS-POL", "POL"),
                new Channel("2014", "EFS-KatS", "KATS"),
                new Channel("2020", "EFS-SW", "SW"),
                new Channel("2021", "EFS-AC", "AC")
        ));
    }

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
