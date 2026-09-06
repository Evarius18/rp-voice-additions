package com.evarius.rpvca.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompatibilityConfig {
    public int configVersion = 0;
    public boolean terraNexusEnabled = true;
    public boolean terraNexusPhoneAppEnabled = true;
    public boolean terraNexusInstitutionPermissionsEnabled = true;

    /**
     * Case-insensitive institution ID, name or type -> radio channel IDs.
     * Multiple entries may match and are combined.
     */
    public Map<String, List<String>> institutionRadioChannels = defaultInstitutionRadioChannels();

    public static LinkedHashMap<String, List<String>> defaultInstitutionRadioChannels() {
        LinkedHashMap<String, List<String>> mappings = new LinkedHashMap<>();
        mappings.put("FW", channels("2000", "2010", "2012"));
        mappings.put("Feuerwehr", channels("2000", "2010", "2012"));
        mappings.put("RD", channels("2000", "2010", "2011"));
        mappings.put("Rettungsdienst", channels("2000", "2010", "2011"));
        mappings.put("POL", channels("2000", "2010", "2013"));
        mappings.put("Polizei", channels("2000", "2010", "2013"));
        mappings.put("THW", channels("2000", "2010", "2014"));
        mappings.put("KatS", channels("2000", "2010", "2014"));
        mappings.put("Katastrophenschutz", channels("2000", "2010", "2014"));
        mappings.put("Technisches Hilfswerk", channels("2000", "2010", "2014"));
        mappings.put("Rettungsorganisation", channels("2000", "2010", "2011", "2012"));
        mappings.put("SW", channels("2010", "2020"));
        mappings.put("Stadtwerke", channels("2010", "2020"));
        mappings.put("AC", channels("2010", "2021"));
        mappings.put("Abschleppdienst", channels("2010", "2021"));
        return mappings;
    }

    private static ArrayList<String> channels(String... ids) {
        return new ArrayList<>(List.of(ids));
    }
}
