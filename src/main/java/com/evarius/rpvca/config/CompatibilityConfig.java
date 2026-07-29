package com.evarius.rpvca.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompatibilityConfig {
    public boolean terraNexusEnabled = true;
    public boolean terraNexusPhoneAppEnabled = true;
    public boolean terraNexusInstitutionPermissionsEnabled = true;

    /**
     * Case-insensitive institution ID, name or type -> radio channel IDs.
     * Multiple entries may match and are combined.
     */
    public Map<String, List<String>> institutionRadioChannels = new LinkedHashMap<>(Map.of(
            "Polizei", new ArrayList<>(List.of("police")),
            "Feuerwehr", new ArrayList<>(List.of("fire")),
            "Rettungsdienst", new ArrayList<>(List.of("medical")),
            "Rettungsorganisation", new ArrayList<>(List.of("fire", "medical"))
    ));
}
