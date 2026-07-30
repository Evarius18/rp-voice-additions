package com.evarius.rpvca.network;

import com.evarius.rpvca.phone.history.CallHistoryEntryView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compact server-authoritative view used by screens and the HUD. */
public final class CommunicationStatus {
    public String speechMode = "normal";
    public String speechTranslationKey = "speech_mode.rpvca.normal";
    public float speechDistance = 32.0F;
    public String phoneState = "idle";
    public String phoneCallId = "";
    public String phonePeer = "";
    public String phonePeerNumber = "";
    public boolean phonePeerSavedContact;
    public String phoneNumber = "";
    public boolean phoneSpeaker;
    public boolean phoneCoverage = true;
    public boolean phoneHeld = true;
    public String phoneNotice = "";
    public Map<String, String> contacts = new LinkedHashMap<>();
    public List<CallHistoryEntryView> callHistory = new ArrayList<>();
    public List<NamedEntry> emergencyNumbers = new ArrayList<>();
    public String radioChannel = "";
    public String radioDisplayName = "";
    public boolean radioTransmitting;
    public boolean radioAvailable;
    public boolean radioEnabled;
    public boolean radioPoweredOn;
    public List<NamedEntry> radioChannels = new ArrayList<>();
    public List<NamedEntry> phoneApps = new ArrayList<>();

    public record NamedEntry(String id, String name) {
    }
}
