package com.evarius.rpvca.config;

import java.util.ArrayList;
import java.util.List;

/** Server-authoritative siren, scheduling and announcement configuration. */
public final class SirenConfig {
    public int configVersion = 0;
    public boolean enabled = true;
    /** Maximum SVC attenuation distance in blocks. */
    public float audibleDistance = 192.0F;
    /** Linear PCM gain for bundled signals. 0.22 avoids clipping while retaining siren character. */
    public float signalGain = 0.22F;
    /** Linear PCM gain for saved announcements. Live announcements retain microphone gain. */
    public float announcementGain = 0.65F;
    public int maxLinkedSirensPerController = 64;
    public int operatePermissionLevel = 2;
    public int configurePermissionLevel = 2;
    public List<String> operatorMembershipKeys = new ArrayList<>(List.of(
            "fire_department", "police", "emergency_management"));
    public List<String> configurationMembershipKeys = new ArrayList<>(List.of(
            "emergency_management"));
    public int maximumScheduledAlarmsPerController = 32;
    public int maximumAnnouncementSeconds = 120;
    public int maximumSavedAnnouncementsPerController = 16;
    public boolean allowCrossDimensionLinks = false;
    public List<Scenario> scenarios = new ArrayList<>(List.of(
            new Scenario("fire_alarm", "scenario.rp-vca.siren.fire_alarm",
                    List.of(new Step("fire_alarm", 0))),
            new Scenario("warning", "scenario.rp-vca.siren.warning",
                    List.of(new Step("warning", 0))),
            new Scenario("all_clear", "scenario.rp-vca.siren.all_clear",
                    List.of(new Step("all_clear", 0))),
            new Scenario("test", "scenario.rp-vca.siren.test",
                    List.of(new Step("test", 0)))
    ));

    public static final class Scenario {
        public String id;
        /** Translation key for built-ins, or a literal name for custom scenarios. */
        public String displayName;
        public List<Step> steps = new ArrayList<>();

        public Scenario() {
        }

        public Scenario(String id, String displayName, List<Step> steps) {
            this.id = id;
            this.displayName = displayName;
            this.steps = new ArrayList<>(steps);
        }
    }

    public static final class Step {
        /** One of fire_alarm, warning, all_clear or test. */
        public String signal;
        /** Delay from the previous step before this signal starts. */
        public int delaySeconds;

        public Step() {
        }

        public Step(String signal, int delaySeconds) {
            this.signal = signal;
            this.delaySeconds = delaySeconds;
        }
    }
}
