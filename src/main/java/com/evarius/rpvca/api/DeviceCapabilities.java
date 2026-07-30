package com.evarius.rpvca.api;

/** Capabilities contributed by an optional communication-device integration. */
public record DeviceCapabilities(boolean phoneEnabled, boolean openDefaultRpVcaScreen,
                                 boolean supportsCalls, boolean supportsSpeaker,
                                 boolean supportsContacts, boolean radioEnabled) {
    public static DeviceCapabilities externalPhone() {
        return new DeviceCapabilities(true, false, true, true, true, false);
    }
}
