package com.evarius.rpvca.config;

public final class PhoneConfig {
    public boolean enabled = true;
    public boolean requirePhoneItem = true;
    public boolean requireCoverage = false;
    /** Kept for migration from the original sequential number allocator. */
    public int numberLength = 6;
    public int ringTimeoutSeconds = 30;
    public float speakerDistance = 18.0F;
    public int maxContactNameLength = 32;
    public int maxContactsPerPlayer = 100;
    public int maxCallHistoryEntries = 50;
    public int historyAdminPermissionLevel = 3;
    public boolean mustBeHeldForAudio = true;
    public boolean allowMainHand = true;
    public boolean allowOffHand = true;
    public boolean keepCallConnectedWhenNotHeld = true;
    public PhoneNumberGeneration phoneNumberGeneration = new PhoneNumberGeneration();

    public static final class PhoneNumberGeneration {
        public boolean enabled = true;
        public String prefix = "0151";
        public int randomDigits = 6;
        public String separator = " ";
        public boolean allowLeadingZeroInRandomPart = true;
        public int maxGenerationAttempts = 100;
        public boolean reuseReleasedNumbers = false;
    }
}
